package org.traanite.pubgity.job

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.player.PlayerService

@Service
class JobService(
    private val jobRepository: UpdateJobRepository,
    private val playerService: PlayerService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(JobService::class.java)
    }

    fun getJobs(): List<UpdateJob> = jobRepository.findAllByOrderByCreatedAtDesc()

    fun queueJob(accountId: String?, playerName: String, matchCount: Int): UpdateJob {
        val clampedCount = matchCount.coerceIn(1, 10)
        val job = jobRepository.save(
            UpdateJob(
                accountId = accountId,
                playerName = playerName,
                jobType = JobType.FORK,
                matchCount = clampedCount
            )
        )
        logger.info(
            "Queued update job {} for player '{}' (accountId={}, matchCount={})",
            job.id,
            playerName,
            accountId,
            clampedCount
        )
        return job
    }

    // todo should just create job with propert createdAt and push to the top prio, but after implementing priority list
    fun retryJob(jobId: ObjectId) {
        logger.info("Retrying job $jobId")
        val job = jobRepository.findById(jobId).orElse(null) ?: return
        if (job.status == JobStatus.FAILED || job.status == JobStatus.CANCELLED) {
            val retryJob = UpdateJob(
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

    fun addPlayer(playerName: String) {
        playerService.addPlayer(playerName)
    }

    fun removePlayer(playerId: ObjectId) {
        playerService.removePlayer(playerId)
    }

    fun getPlayers() = playerService.findAll()
}

