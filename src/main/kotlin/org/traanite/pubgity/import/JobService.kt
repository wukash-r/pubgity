package org.traanite.pubgity.import

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.player.PlayerService

@Service
class JobService(
    private val jobRepository: ImportJobRepository,
    private val playerService: PlayerService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JobService::class.java)
        private val ACTIVE_STATUSES = listOf(JobStatus.QUEUED, JobStatus.RUNNING)
    }

    fun getJobs(): List<ImportJob> = jobRepository.findAllByOrderByCreatedAtDesc()

    fun findJobById(jobId: ObjectId): ImportJob? = jobRepository.findById(jobId).orElse(null)

    /**
     * Returns all jobs that belong to the given set of PUBG account IDs,
     * ordered by creation time descending. Used to scope the jobs view for moderators.
     */
    fun getJobsForAccountIds(accountIds: Set<String>): List<ImportJob> =
        jobRepository.findAllByAccountIdInOrderByCreatedAtDesc(accountIds)

    /**
     * Checks whether a moderator is allowed to queue another job given their constraints.
     * Counts currently QUEUED or RUNNING jobs across the moderator's allowed players.
     */
    fun canModeratorQueue(allowedPlayerIds: Set<ObjectId>, maxQueueSize: Int): Boolean {
        val accountIds = allowedPlayerIds
            .mapNotNull { playerService.findById(it)?.accountId }
            .toSet()
        if (accountIds.isEmpty()) return false
        val active = jobRepository.countByAccountIdInAndStatusIn(accountIds, ACTIVE_STATUSES)
        return active < maxQueueSize
    }

    fun queueJob(playerObjectId: ObjectId, matchCount: Int) {
        val player = playerService.findById(playerObjectId)
        if (player == null) {
            logger.warn("Cannot queue job for not existing player $playerObjectId")
            return
        }

        val clampedCount = matchCount.coerceIn(1, 100)
        val job = jobRepository.save(
            ImportJob(
                accountId = player.accountId,
                playerName = player.playerName,
                jobType = JobType.FETCH_PLAYER_MATCHES,
                matchCount = clampedCount
            )
        )
        logger.info(
            "Queued update job {} for player '{}' (accountId={}, matchCount={})",
            job.id, player.playerName, player.accountId, clampedCount
        )
    }

    // todo should just create job with propert createdAt and push to the top prio, but after implementing priority list
    fun retryJob(jobId: ObjectId) {
        logger.info("Retrying job $jobId")
        val job = jobRepository.findById(jobId).orElse(null) ?: return
        if (job.status == JobStatus.FAILED || job.status == JobStatus.CANCELLED) {
            val retryJob = ImportJob(
                accountId = job.accountId,
                playerName = job.playerName,
                jobType = job.jobType,
                matchCount = job.matchCount,
                matchId = job.matchId,
                status = JobStatus.QUEUED,
                createdAt = job.createdAt
            )
            jobRepository.save(retryJob)
            jobRepository.save(job.copy(retried = true))
            logger.info("Retried failed job {}", jobId)
        }
    }

    fun cancelJob(jobId: ObjectId) {
        val job = jobRepository.findById(jobId).orElse(null) ?: return
        if (job.status == JobStatus.QUEUED || job.status == JobStatus.RUNNING) {
            jobRepository.save(job.copy(status = JobStatus.CANCELLED))
            logger.info("Cancelled job {}", jobId)
        }
    }
}

