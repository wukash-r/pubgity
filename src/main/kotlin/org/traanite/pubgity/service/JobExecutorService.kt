package org.traanite.pubgity.service

import jakarta.annotation.PostConstruct
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.traanite.pubgity.client.PubgApiClient
import org.traanite.pubgity.client.toModel
import org.traanite.pubgity.config.PubgCacheProperties
import org.traanite.pubgity.model.*
import org.traanite.pubgity.repository.MatchRepository
import org.traanite.pubgity.repository.PlayerRepository
import org.traanite.pubgity.repository.UpdateJobRepository
import java.time.Duration
import java.time.Instant

@Service
class JobExecutorService(
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository,
    private val jobRepository: UpdateJobRepository,
    private val pubgApiClient: PubgApiClient,
    private val cacheProperties: PubgCacheProperties
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
        val resolvedPlayer = resolvePlayer(jobId, job)
        val allMatches = fetchAllMatchMetadata(jobId, resolvedPlayer.matchIds)
        val selectedMatches = allMatches.sortedByDescending { it.createdAt }.take(job.matchCount)

        val storedMatchIds = matchRepository.findByMatchIdIn(selectedMatches.map { it.matchId })
            .map { it.matchId }.toSet()
        val newMatches = selectedMatches.filter { it.matchId !in storedMatchIds }

        logger.info(
            "Selected {} most recent matches: {} already in DB, {} new",
            selectedMatches.size, storedMatchIds.size, newMatches.size
        )

        // todo we shouldn't merge already here, do it in the last step - update matches for ALL the participants, including player that we triggered the job for
        mergePlayerMatches(resolvedPlayer.accountId, resolvedPlayer.playerName, job.playerName, selectedMatches)

        val participants = collectParticipants(newMatches)
        fetchLifetimeStats(jobId, participants)
        saveMatchSnapshots(jobId, newMatches)
        updateMatchesForAllParticipants(jobId, newMatches)
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

        // todo save player id if not in db

        val accountId = playerData.id
        val matchIds = playerData.relationships?.matches?.data?.map { it.id } ?: emptyList()
        val name = playerData.attributes.name

        logger.info("Resolved '{}' -> accountId={}, {} matches from API", name, accountId, matchIds.size)

        val currentJob = jobRepository.findById(jobId).get()
        jobRepository.save(currentJob.copy(accountId = accountId))

        return ResolvedPlayer(accountId, name, matchIds)
    }

    // --- Step B: Fetch match metadata (not rate-limited) ---

    private data class FetchedRoster(
        val rosterId: String,
        val rank: Int,
        val won: Boolean,
        val participants: Map<String, String> // accountId -> playerName
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
            get() = rosters.flatMap { it.participants.entries }.associate { it.key to it.value }
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

        // Build participant lookup: participantId -> (accountId, playerName)
        val participantLookup = mutableMapOf<String, Pair<String, String>>()
        var botCount = 0

        matchResponse.included
            .filter { it.type == "participant" }
            .forEach { included ->
                val stats = included.attributes?.stats
                val pid = stats?.playerId ?: return@forEach
                val pname = stats.name ?: return@forEach

                when {
                    pid.startsWith("account.") -> participantLookup[included.id] = pid to pname
                    pid.startsWith("ai.") -> botCount++
                }
            }

        // Build rosters
        val rosters = matchResponse.included
            .filter { it.type == "roster" }
            .map { roster ->
                val rosterStats = roster.attributes?.stats
                val rank = rosterStats?.rank ?: 0
                val won = roster.attributes?.won == "true"
                val rosterParticipantIds = roster.relationships?.participants?.data?.map { it.id } ?: emptyList()

                val rosterParticipants = rosterParticipantIds
                    .mapNotNull { participantLookup[it] }
                    .associate { it.first to it.second }

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

    // --- Step C: Merge match IDs into player document ---

    private fun mergePlayerMatches(
        accountId: String,
        currentName: String,
        originalName: String,
        selectedMatches: List<FetchedMatch>
    ) {
        val existingPlayer = playerRepository.findByAccountId(accountId)
            ?: playerRepository.findByPlayerName(originalName)
            ?: Player(playerName = currentName)

        val existingMatchIds = existingPlayer.matches.map { it.matchId }.toSet()
        val newRefs = selectedMatches
            .filter { it.matchId !in existingMatchIds }
            .map { PlayerMatchRef(matchId = it.matchId) }
        val mergedMatches = existingPlayer.matches + newRefs

        playerRepository.save(
            existingPlayer.copy(
                accountId = accountId,
                playerName = currentName,
                matches = mergedMatches
            )
        )
        logger.info("Player '{}' now has {} total matches", currentName, mergedMatches.size)
    }

    // --- Step D: Collect unique participants ---

    private fun collectParticipants(matches: List<FetchedMatch>): Map<String, String> {
        val participants = matches.flatMap { it.realParticipants.entries }
            .associate { it.key to it.value }
        logger.info("Collected {} unique real participants across {} new matches", participants.size, matches.size)
        return participants
    }

    // --- Step E: Fetch lifetime stats with caching ---

    private fun fetchLifetimeStats(jobId: ObjectId, participants: Map<String, String>) {
        val entries = participants.entries.toList()
        var fetched = 0
        var skipped = 0

        entries.forEachIndexed { index, (accountId, playerName) ->
            updateProgress(
                jobId,
                "Fetching lifetime stats (${index + 1}/${entries.size}, $fetched fetched, $skipped cached)"
            )

            val existing = playerRepository.findByAccountId(accountId)

            if (existing != null && isWithinCacheThreshold(existing.lastUpdated)) {
                skipped++
                return@forEachIndexed
            }

            try {
                val stats = pubgApiClient.getLifetimeStats(accountId).data.attributes.toModel()
                val playerDoc = existing ?: Player(playerName = playerName)
                playerRepository.save(
                    playerDoc.copy(
                        accountId = accountId,
                        playerName = playerName,
                        lifetimeStats = stats,
                        lastUpdated = Instant.now()
                    )
                )
                fetched++
            } catch (e: Exception) {
                logger.warn("Failed to fetch lifetime stats for {}: {}", accountId, e.message)
            }
        }

        logger.info("Lifetime stats: {} total, {} fetched, {} cached", entries.size, fetched, skipped)
    }

    private fun isWithinCacheThreshold(lastUpdated: Instant?): Boolean {
        if (lastUpdated == null) return false
        return Duration.between(lastUpdated, Instant.now()) < cacheProperties.playerStatsTtl
    }

    // --- Step F: Save match snapshots ---

    private fun saveMatchSnapshots(jobId: ObjectId, matches: List<FetchedMatch>) {
        updateProgress(jobId, "Saving match snapshots...")

        matches.forEach { fetched ->
            val rosterSnapshots = fetched.rosters.map { roster ->
                val participantSnapshots = roster.participants.map { (pid, pname) ->
                    MatchParticipantSnapshot(
                        accountId = pid,
                        playerName = pname,
                        lifetimeStats = playerRepository.findByAccountId(pid)?.lifetimeStats
                    )
                }
                MatchRoster(
                    rosterId = roster.rosterId,
                    rank = roster.rank,
                    won = roster.won,
                    participants = participantSnapshots
                )
            }

            val existing = matchRepository.findByMatchId(fetched.matchId)
            matchRepository.save(
                Match(
                    id = existing?.id,
                    matchId = fetched.matchId,
                    createdAt = fetched.createdAt,
                    gameMode = fetched.gameMode,
                    mapName = fetched.mapName,
                    duration = fetched.duration,
                    botCount = fetched.botCount,
                    rosters = rosterSnapshots
                )
            )
            logger.debug("Saved match {} with {} rosters", fetched.matchId, rosterSnapshots.size)
        }
        logger.info("Saved {} new match snapshots", matches.size)
    }

    // --- Step G: Propagate match IDs to all participants ---

    private fun updateMatchesForAllParticipants(jobId: ObjectId, matches: List<FetchedMatch>) {
        updateProgress(jobId, "Updating matches for all participants...")

        val participantMatchMap = buildParticipantMatchMap(matches)
        var updatedCount = 0

        participantMatchMap.forEach { (pid, matchIdsToAdd) ->
            val playerDoc = playerRepository.findByAccountId(pid) ?: return@forEach
            val currentIds = playerDoc.matches.map { it.matchId }.toSet()
            val newIds = matchIdsToAdd - currentIds

            if (newIds.isNotEmpty()) {
                val newRefs = newIds.map { PlayerMatchRef(matchId = it) }
                playerRepository.save(playerDoc.copy(matches = playerDoc.matches + newRefs))
                updatedCount++
            }
        }
        logger.info("Updated matches for {} participants", updatedCount)
    }

    private fun buildParticipantMatchMap(matches: List<FetchedMatch>): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        matches.forEach { match ->
            match.realParticipants.keys.forEach { pid ->
                result.getOrPut(pid) { mutableSetOf() }.add(match.matchId)
            }
        }
        return result
    }

    // --- Job lifecycle helpers ---

    private fun markJobCompleted(jobId: ObjectId) {
        val current = jobRepository.findById(jobId).get()
        jobRepository.save(current.copy(status = JobStatus.COMPLETED, completedAt = Instant.now()))
        logger.info("Job {} completed", jobId)
    }

    private fun markJobFailed(jobId: ObjectId, job: UpdateJob, error: Exception) {
        val current = jobRepository.findById(jobId).orElse(job)
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
            jobRepository.save(it.copy(progress = progress))
        }
    }
}
