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
        stale.forEach { jobRepository.save(it.copy(status = JobStatus.QUEUED)) }
        if (stale.isNotEmpty()) {
            logger.info("Reset {} stale RUNNING jobs to QUEUED", stale.size)
        }
    }

    @Scheduled(fixedDelay = 5000)
    fun processNextJob() {
        val job = jobRepository.findFirstByStatusOrderByCreatedAtAsc(JobStatus.QUEUED) ?: return
        val jobId = job.id!!
        logger.info("Picked up job {} for player '{}' (accountId={}, matchCount={})", jobId, job.playerName, job.accountId, job.matchCount)

        jobRepository.save(job.copy(status = JobStatus.RUNNING, startedAt = Instant.now()))

        try {
            executeJob(jobId, job)
            val current = jobRepository.findById(jobId).get()
            jobRepository.save(current.copy(status = JobStatus.COMPLETED, completedAt = Instant.now()))
            logger.info("Job {} completed for player '{}'", jobId, job.playerName)
        } catch (e: Exception) {
            logger.error("Job {} failed for player '{}'", jobId, job.playerName, e)
            val current = jobRepository.findById(jobId).orElse(job)
            jobRepository.save(
                current.copy(
                    status = JobStatus.FAILED,
                    completedAt = Instant.now(),
                    errorMessage = e.message?.take(500)
                )
            )
        }
    }

    private fun executeJob(jobId: ObjectId, originalJob: UpdateJob) {
        // Step A: Resolve player
        updateProgress(jobId, "Resolving player...")

        val playerData = if (originalJob.accountId != null) {
            pubgApiClient.getPlayerByAccountId(originalJob.accountId)
        } else {
            pubgApiClient.getPlayerByName(originalJob.playerName)
        }

        val accountId = playerData.id
        val allMatchIds = playerData.relationships?.matches?.data?.map { it.id } ?: emptyList()
        val currentName = playerData.attributes.name
        logger.info("Resolved player '{}' -> accountId={}, {} total matches from API", currentName, accountId, allMatchIds.size)

        // Update job with resolved accountId
        val jobNow = jobRepository.findById(jobId).get()
        jobRepository.save(jobNow.copy(accountId = accountId))

        // Step B: Fetch all matches to find the N most recent by createdAt
        updateProgress(jobId, "Fetching matches to determine most recent (0/${allMatchIds.size})...")

        data class FetchedMatch(
            val matchId: String,
            val createdAt: Instant,
            val gameMode: String,
            val mapName: String,
            val duration: Int,
            val participantAccountIds: Map<String, String>, // accountId -> playerName
            val botCount: Int
        )

        val fetchedMatches = mutableListOf<FetchedMatch>()

        allMatchIds.forEachIndexed { index, matchId ->
            updateProgress(jobId, "Fetching matches (${index + 1}/${allMatchIds.size})")
            try {
                val matchResponse = pubgApiClient.getMatch(matchId)
                val attrs = matchResponse.data.attributes
                val createdAtStr = attrs?.createdAt ?: return@forEachIndexed
                val createdAt = Instant.parse(createdAtStr)

                val participants = mutableMapOf<String, String>()
                var botCount = 0
                matchResponse.included
                    .filter { it.type == "participant" }
                    .forEach { included ->
                        val stats = included.attributes?.stats
                        val pid = stats?.playerId
                        val pname = stats?.name
                        if (pid != null && pname != null) {
                            if (pid.startsWith("account.")) {
                                participants[pid] = pname
                            } else if (pid.startsWith("ai.")) {
                                botCount++
                            }
                        }
                    }

                fetchedMatches.add(
                    FetchedMatch(
                        matchId = matchId,
                        createdAt = createdAt,
                        gameMode = attrs.gameMode ?: "unknown",
                        mapName = attrs.mapName ?: "unknown",
                        duration = attrs.duration,
                        participantAccountIds = participants,
                        botCount = botCount
                    )
                )
            } catch (e: Exception) {
                logger.warn("Failed to fetch match {}: {}", matchId, e.message)
            }
        }

        // Sort by date descending, take N most recent
        val selectedMatches = fetchedMatches.sortedByDescending { it.createdAt }.take(originalJob.matchCount)
        val selectedMatchIds = selectedMatches.map { it.matchId }
        logger.info("Selected {} most recent matches out of {} fetched", selectedMatches.size, fetchedMatches.size)

        // Upsert player with accountId and selected matchIds (do NOT set lastUpdated)
        val existingPlayer = playerRepository.findByAccountId(accountId)
            ?: playerRepository.findByPlayerName(originalJob.playerName)
            ?: Player(playerName = currentName)

        playerRepository.save(
            existingPlayer.copy(
                accountId = accountId,
                playerName = currentName,
                matchIds = selectedMatchIds
            )
        )

        // Step C: Collect all unique participants across selected matches
        val allParticipants = mutableMapOf<String, String>() // accountId -> playerName
        selectedMatches.forEach { match ->
            allParticipants.putAll(match.participantAccountIds)
        }
        logger.info("Collected {} unique real participants across {} selected matches", allParticipants.size, selectedMatches.size)

        // Step D: Fetch lifetime stats for all participants (with caching)
        val participantList = allParticipants.keys.toList()
        var fetched = 0
        var skipped = 0

        participantList.forEachIndexed { index, participantAccountId ->
            updateProgress(
                jobId,
                "Fetching lifetime stats (${index + 1}/${participantList.size}, $fetched fetched, $skipped cached)"
            )

            // Check cache threshold
            val existing = playerRepository.findByAccountId(participantAccountId)
            if (existing?.lastUpdated != null) {
                val age = Duration.between(existing.lastUpdated, Instant.now())
                if (age < cacheProperties.playerStatsTtl) {
                    skipped++
                    return@forEachIndexed
                }
            }

            try {
                val statsResponse = pubgApiClient.getLifetimeStats(participantAccountId)
                val lifetimeStats = statsResponse.data.attributes.toModel()

                val playerDoc = existing ?: Player(
                    playerName = allParticipants[participantAccountId] ?: "Unknown"
                )
                playerRepository.save(
                    playerDoc.copy(
                        accountId = participantAccountId,
                        playerName = allParticipants[participantAccountId] ?: playerDoc.playerName,
                        lifetimeStats = lifetimeStats,
                        lastUpdated = Instant.now()
                    )
                )
                fetched++
            } catch (e: Exception) {
                logger.warn("Failed to fetch lifetime stats for {}: {}", participantAccountId, e.message)
            }
        }

        logger.info("Lifetime stats: {} total participants, {} fetched, {} cached", participantList.size, fetched, skipped)

        // Step E: Build Match documents with participant snapshots
        updateProgress(jobId, "Saving match snapshots...")

        selectedMatches.forEach { fetchedMatch ->
            val participantSnapshots = fetchedMatch.participantAccountIds.map { (pid, pname) ->
                val playerDoc = playerRepository.findByAccountId(pid)
                MatchParticipantSnapshot(
                    accountId = pid,
                    playerName = pname,
                    lifetimeStats = playerDoc?.lifetimeStats
                )
            }

            val existingMatch = matchRepository.findByMatchId(fetchedMatch.matchId)
            val matchDoc = Match(
                id = existingMatch?.id,
                matchId = fetchedMatch.matchId,
                createdAt = fetchedMatch.createdAt,
                gameMode = fetchedMatch.gameMode,
                mapName = fetchedMatch.mapName,
                duration = fetchedMatch.duration,
                botCount = fetchedMatch.botCount,
                participants = participantSnapshots
            )
            matchRepository.save(matchDoc)
            logger.debug("Saved match snapshot {} with {} participants", fetchedMatch.matchId, participantSnapshots.size)
        }

        logger.info("Saved {} match snapshots", selectedMatches.size)
    }

    private fun updateProgress(jobId: ObjectId, progress: String) {
        jobRepository.findById(jobId).ifPresent {
            jobRepository.save(it.copy(progress = progress))
        }
    }
}
