package org.traanite.pubgity.service

import jakarta.annotation.PostConstruct
import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.traanite.pubgity.client.PubgApiClient
import org.traanite.pubgity.client.toModel
import org.traanite.pubgity.config.PubgCacheProperties
import org.traanite.pubgity.model.JobStatus
import org.traanite.pubgity.model.Player
import org.traanite.pubgity.model.UpdateJob
import org.traanite.pubgity.repository.PlayerRepository
import org.traanite.pubgity.repository.UpdateJobRepository
import java.time.Duration
import java.time.Instant

@Service
class JobExecutorService(
    private val playerRepository: PlayerRepository,
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
        logger.info("Picked up job {} for player '{}' (accountId={})", jobId, job.playerName, job.accountId)

        jobRepository.save(job.copy(status = JobStatus.RUNNING, startedAt = Instant.now()))

        try {
            executeJob(jobId, job)
            val current = jobRepository.findById(jobId).get()
            jobRepository.save(current.copy(status = JobStatus.COMPLETED, completedAt = Instant.now()))
            logger.info("Job {} completed for player {}", jobId, job.playerName)
        } catch (e: Exception) {
            logger.error("Job {} failed for player {}", jobId, job.playerName, e)
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
        val matchIds = playerData.relationships?.matches?.data?.map { it.id } ?: emptyList()
        val currentName = playerData.attributes.name
        logger.info("Resolved player '{}' -> accountId={}, {} matches", currentName, accountId, matchIds.size)

        // Upsert player with accountId and matchIds (do NOT set lastUpdated)
        val existingPlayer = playerRepository.findByAccountId(accountId)
            ?: playerRepository.findByPlayerName(originalJob.playerName)
            ?: Player(playerName = currentName)

        playerRepository.save(
            existingPlayer.copy(
                accountId = accountId,
                playerName = currentName,
                matchIds = matchIds
            )
        )

        // Update job with resolved accountId
        val jobNow = jobRepository.findById(jobId).get()
        jobRepository.save(jobNow.copy(accountId = accountId))

        // Step B: Fetch matches and collect participants
        updateProgress(jobId, "Fetching matches (0/${matchIds.size})")

        data class ParticipantInfo(val playerId: String, val name: String)

        val allParticipants = mutableSetOf<String>()
        val participantNames = mutableMapOf<String, String>()

        matchIds.forEachIndexed { index, matchId ->
            updateProgress(jobId, "Fetching matches (${index + 1}/${matchIds.size})")
            try {
                val matchResponse = pubgApiClient.getMatch(matchId)
                matchResponse.included
                    .filter { it.type == "participant" }
                    .forEach { included ->
                        val stats = included.attributes?.stats
                        val pid = stats?.playerId
                        val pname = stats?.name
                        if (pid != null && pname != null && pid.startsWith("account.")) {
                            allParticipants.add(pid)
                            participantNames[pid] = pname
                        }
                    }
            } catch (e: Exception) {
                logger.warn("Failed to fetch match {}: {}", matchId, e.message)
            }
        }

        // Step C: Fetch lifetime stats for real players
        val participantList = allParticipants.toList()
        logger.info("Collected {} unique real participants from {} matches", participantList.size, matchIds.size)
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
                    playerName = participantNames[participantAccountId] ?: "Unknown"
                )
                playerRepository.save(
                    playerDoc.copy(
                        accountId = participantAccountId,
                        playerName = participantNames[participantAccountId] ?: playerDoc.playerName,
                        lifetimeStats = lifetimeStats,
                        lastUpdated = Instant.now()
                    )
                )
                fetched++
            } catch (e: Exception) {
                logger.warn("Failed to fetch lifetime stats for {}: {}", participantAccountId, e.message)
            }
        }

        logger.info(
            "Job stats: {} total participants, {} fetched, {} cached",
            participantList.size, fetched, skipped
        )
    }

    private fun updateProgress(jobId: ObjectId, progress: String) {
        jobRepository.findById(jobId).ifPresent {
            jobRepository.save(it.copy(progress = progress))
        }
    }
}

