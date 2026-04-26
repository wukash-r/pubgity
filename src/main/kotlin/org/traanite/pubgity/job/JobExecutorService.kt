package org.traanite.pubgity.job

import jakarta.annotation.PostConstruct
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.traanite.pubgity.match.*
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.pubgapi.PubgApiClient
import org.traanite.pubgity.pubgapi.toLifetimeStats
import org.traanite.pubgity.pubgapi.toMatchParticipantStats
import org.traanite.pubgity.pubgapi.toParticipantLifetimeStats
import java.time.Duration
import java.time.Instant

@Service
class JobExecutorService(
    private val jobRepository: UpdateJobRepository,
    private val playerService: PlayerService,
    private val matchService: MatchService,
    private val pubgApiClient: PubgApiClient,
    private val jobProperties: JobProperties
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun resetStaleJobs() {
        val stale = jobRepository.findAll().filter { it.status == JobStatus.RUNNING }
        if (stale.isEmpty()) return
        stale.forEach { jobRepository.save(it.copy(status = JobStatus.QUEUED)) }
        logger.info("Reset {} stale RUNNING jobs to QUEUED", stale.size)
    }

    @Scheduled(fixedDelay = 5000)
    fun processNextJob() {
        if (jobRepository.existsByStatus(JobStatus.RUNNING)) {
            logger.debug("A job is already running, skipping")
            return
        }

        val job = jobRepository.findFirstByStatusOrderByCreatedAtAsc(JobStatus.QUEUED) ?: return
        val jobId = job.id!!
        logger.info("Picked up job {} for player '{}' (matchCount={})", jobId, job.playerName, job.matchCount)

        jobRepository.save(job.copy(status = JobStatus.RUNNING, startedAt = Instant.now()))

        try {
            executeJob(jobId, job)
            markJobCompleted(jobId)
        } catch (e: Exception) {
            logger.error("Job {} failed for player '{}'", jobId, job.playerName, e)
            markJobFailed(jobId, job, e)
        }
    }

    private fun executeJob(jobId: ObjectId, job: UpdateJob) {
        if (isCancelled(jobId)) return

        val resolvedPlayer = resolvePlayer(jobId, job)
        if (isCancelled(jobId)) return

        val allMatches = fetchAllMatchMetadata(jobId, resolvedPlayer.matchIds)
        if (isCancelled(jobId)) return

        val selectedMatches = allMatches.sortedByDescending { it.createdAt }.take(job.matchCount)

        val storedMatchIds = matchService.findExistingMatchIds(selectedMatches.map { it.matchId })
        val newMatches = selectedMatches.filter { it.matchId !in storedMatchIds }

        logger.info(
            "Selected {} most recent matches: {} already in DB, {} new",
            selectedMatches.size, storedMatchIds.size, newMatches.size
        )

        // Append match refs to the tracked player
        playerService.appendMatchRefs(resolvedPlayer.accountId, selectedMatches.map { it.matchId })

        if (isCancelled(jobId)) return

        val participants = collectParticipants(newMatches)
        fetchLifetimeStats(jobId, participants)
        if (isCancelled(jobId)) return

        saveMatchSnapshots(jobId, newMatches)
    }

    // --- Step A: Resolve player identity ---

    private data class ResolvedPlayer(
        val accountId: String,
        val playerName: String,
        val matchIds: List<String>
    )

    private fun resolvePlayer(jobId: ObjectId, job: UpdateJob): ResolvedPlayer {
        updateProgress(jobId, "Resolving player...")

        val playerData = if (job.accountId != null) {
            pubgApiClient.getPlayerByAccountId(job.accountId)
        } else {
            pubgApiClient.getPlayerByName(job.playerName)
        }

        val accountId = playerData.id
        val matchIds = playerData.relationships?.matches?.data?.map { it.id } ?: emptyList()
        val name = playerData.attributes.name

        logger.info("Resolved '{}' -> accountId={}, {} matches from API", name, accountId, matchIds.size)

        val currentJob = jobRepository.findById(jobId).get()
        jobRepository.save(currentJob.copy(accountId = accountId))

        // Ensure player exists in DB
        playerService.resolveOrCreatePlayer(accountId, name, job.playerName)

        return ResolvedPlayer(accountId, name, matchIds)
    }

    // --- Step B: Fetch match metadata ---

    private data class FetchedRoster(
        val rosterId: String,
        val rank: Int,
        val won: Boolean,
        val participants: Map<String, FetchedParticipant> // accountId -> FetchedParticipant
    )

    private data class FetchedParticipant(
        val accountId: String,
        val playerName: String,
        val matchStats: MatchParticipantStats?
    )

    private data class FetchedMatch(
        val matchId: String,
        val createdAt: Instant,
        val gameMode: String,
        val mapName: String,
        val duration: Int,
        val rosters: List<FetchedRoster>,
        val botCount: Int
    ) {
        val realParticipants: Map<String, String>
            get() = rosters.flatMap { it.participants.values }.associate { it.accountId to it.playerName }
    }

    private fun fetchAllMatchMetadata(jobId: ObjectId, matchIds: List<String>): List<FetchedMatch> {
        updateProgress(jobId, "Fetching match metadata (0/${matchIds.size})...")

        return matchIds.mapIndexed { index, matchId ->
            updateProgress(jobId, "Fetching match metadata (${index + 1}/${matchIds.size})")
            fetchSingleMatch(matchId)
        }.filterNotNull()
    }

    private fun fetchSingleMatch(matchId: String): FetchedMatch? {
        val matchResponse = pubgApiClient.getMatch(matchId)
        val attrs = matchResponse.data.attributes
        val createdAtStr = attrs?.createdAt ?: return null

        val participantLookup = mutableMapOf<String, FetchedParticipant>()
        var botCount = 0

        matchResponse.included
            .filter { it.type == "participant" }
            .forEach { included ->
                val stats = included.attributes?.stats
                val pid = stats?.playerId ?: return@forEach
                val pname = stats.name ?: return@forEach

                when {
                    pid.startsWith("account.") -> participantLookup[included.id] = FetchedParticipant(
                        accountId = pid,
                        playerName = pname,
                        matchStats = stats.toMatchParticipantStats()
                    )
                    pid.startsWith("ai.") -> botCount++
                }
            }

        val rosters = matchResponse.included
            .filter { it.type == "roster" }
            .map { roster ->
                val rosterStats = roster.attributes?.stats
                val rank = rosterStats?.rank ?: 0
                val won = roster.attributes?.won == "true"
                val rosterParticipantIds = roster.relationships?.participants?.data?.map { it.id } ?: emptyList()

                val rosterParticipants = rosterParticipantIds
                    .mapNotNull { participantLookup[it] }
                    .associateBy { it.accountId }

                FetchedRoster(
                    rosterId = roster.id,
                    rank = rank,
                    won = won,
                    participants = rosterParticipants
                )
            }
            .filter { it.participants.isNotEmpty() }

        return FetchedMatch(
            matchId = matchId,
            createdAt = Instant.parse(createdAtStr),
            gameMode = attrs.gameMode ?: "unknown",
            mapName = attrs.mapName ?: "unknown",
            duration = attrs.duration,
            rosters = rosters,
            botCount = botCount
        )
    }

    // --- Step C: Collect unique participants ---

    private fun collectParticipants(matches: List<FetchedMatch>): Map<String, String> {
        val participants = matches.flatMap { it.realParticipants.entries }
            .associate { it.key to it.value }
        logger.info("Collected {} unique real participants across {} new matches", participants.size, matches.size)
        return participants
    }

    // --- Step D: Fetch lifetime stats with caching ---

    private fun fetchLifetimeStats(jobId: ObjectId, participants: Map<String, String>) {
        val entries = participants.entries.toList()
        var fetched = 0
        var skipped = 0

        entries.forEachIndexed { index, (accountId, playerName) ->
            if (isCancelled(jobId)) return

            updateProgress(
                jobId,
                "Fetching lifetime stats (${index + 1}/${entries.size}, $fetched fetched, $skipped cached)"
            )

            val latestSnapshot = playerService.getLatestLifetimeStats(accountId)

            if (latestSnapshot != null && isWithinCacheThreshold(latestSnapshot.capturedAt)) {
                skipped++
                return@forEachIndexed
            }

            try {
                val stats = pubgApiClient.getLifetimeStats(accountId).data.attributes.toLifetimeStats()
                playerService.saveLifetimeStatsSnapshot(accountId, stats)

                // Ensure player document exists
                if (playerService.findByAccountId(accountId) == null) {
                    playerService.resolveOrCreatePlayer(accountId, playerName, playerName)
                }

                fetched++
            } catch (e: Exception) {
                logger.warn("Failed to fetch lifetime stats for {}: {}", accountId, e.message)
            }
        }

        logger.info("Lifetime stats: {} total, {} fetched, {} cached", entries.size, fetched, skipped)
    }

    private fun isWithinCacheThreshold(capturedAt: Instant): Boolean {
        return Duration.between(capturedAt, Instant.now()) < jobProperties.statsTtl
    }

    // --- Step E: Save match snapshots ---

    private fun saveMatchSnapshots(jobId: ObjectId, matches: List<FetchedMatch>) {
        updateProgress(jobId, "Saving match snapshots...")

        matches.forEach { fetched ->
            if (isCancelled(jobId)) return

            val rosterSnapshots = fetched.rosters.map { roster ->
                val participantSnapshots = roster.participants.values.map { participant ->
                    val latestSnapshot = playerService.getLatestLifetimeStats(participant.accountId)
                    MatchParticipant(
                        accountId = participant.accountId,
                        playerName = participant.playerName,
                        matchStats = participant.matchStats,
                        lifetimeStatsSnapshot = latestSnapshot?.stats?.toParticipantLifetimeStats()
                    )
                }
                MatchRoster(
                    rosterId = roster.rosterId,
                    rank = roster.rank,
                    won = roster.won,
                    participants = participantSnapshots
                )
            }

            matchService.saveMatch(
                Match(
                    matchId = fetched.matchId,
                    createdAt = fetched.createdAt,
                    gameMode = fetched.gameMode,
                    mapName = fetched.mapName,
                    duration = fetched.duration,
                    botCount = fetched.botCount,
                    rosters = rosterSnapshots
                )
            )
        }
        logger.info("Saved {} new match snapshots", matches.size)
    }

    // --- Job lifecycle helpers ---

    private fun isCancelled(jobId: ObjectId): Boolean {
        val current = jobRepository.findById(jobId).orElse(null) ?: return true
        return current.status == JobStatus.CANCELLED
    }

    private fun markJobCompleted(jobId: ObjectId) {
        val current = jobRepository.findById(jobId).get()
        if (current.status == JobStatus.CANCELLED) return
        jobRepository.save(current.copy(status = JobStatus.COMPLETED, completedAt = Instant.now()))
        logger.info("Job {} completed", jobId)
    }

    private fun markJobFailed(jobId: ObjectId, job: UpdateJob, error: Exception) {
        val current = jobRepository.findById(jobId).orElse(job)!!
        if (current.status == JobStatus.CANCELLED) return
        jobRepository.save(
            current.copy(
                status = JobStatus.FAILED,
                completedAt = Instant.now(),
                errorMessage = error.message?.take(500)
            )
        )
    }

    private fun updateProgress(jobId: ObjectId, progress: String) {
        jobRepository.findById(jobId).ifPresent {
            if (it.status != JobStatus.CANCELLED) {
                jobRepository.save(it.copy(progress = progress))
            }
        }
    }
}

