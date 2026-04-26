package org.traanite.pubgity.job

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.traanite.pubgity.match.FetchedMatch
import org.traanite.pubgity.match.MatchDataFetcher
import org.traanite.pubgity.player.PlayerResolver
import org.traanite.pubgity.player.ResolvedPlayer
import java.time.Instant

@Service
class ForkJobExecutor(
    private val jobRepository: UpdateJobRepository,
    private val playerResolver: PlayerResolver,
    private val matchDataFetcher: MatchDataFetcher
) {
    companion object {
        private val logger = LoggerFactory.getLogger(javaClass)
        private val jobType: JobType = JobType.FORK
    }

    @Scheduled(fixedDelay = 5000)
    fun processNextJob() {
        logger.info("Checking for next $jobType job...")
        if (jobRepository.existsByJobTypeAndStatus(jobType, JobStatus.RUNNING)) {
            logger.info("A job is already running, skipping")
            return
        }

        val job = jobRepository.findFirstByJobTypeAndStatusOrderByCreatedAtAsc(jobType, JobStatus.QUEUED) ?: return
        val jobId = job.id!!
        logger.info("Picked up job {} for player '{}' (matchCount={})", jobId, job.playerName, job.matchCount)

        jobRepository.save(job.copy(status = JobStatus.RUNNING, startedAt = Instant.now()))

        try {
            executeJob(job)
            markJobCompleted(jobId)
        } catch (_: JobCancelledException) {
            logger.info("Job {} was cancelled during execution", jobId)
        } catch (e: Exception) {
            logger.error("Job {} failed for player '{}'", jobId, job.playerName, e)
            markJobFailed(jobId, job, e)
        }
    }

    private fun executeJob(job: UpdateJob) {
        val jobId = job.id!!
        val resolvedPlayer = resolvePlayer(jobId, job)
        val newMatches = collectNewMatchesMetadata(jobId, job.matchCount!!, resolvedPlayer.matchIds)

        newMatches.map { match ->
            val currentJob = jobRepository.findById(jobId).get()
            UpdateJob(
                accountId = currentJob.accountId,
                playerName = currentJob.playerName,
                jobType = JobType.SINGLE_MATCH,
                // match api responses are cached, no harm in only saving matchId here
                matchId = match.matchId
            )
        }.forEach { singleMatchJob ->
            val created = jobRepository.save(singleMatchJob)
            logger.info("Created single match job {} for match {}", created.id, created.matchId)
        }

    }

    private fun resolvePlayer(jobId: ObjectId, job: UpdateJob): ResolvedPlayer {
        updateProgress(jobId, "Resolving player...")
        val resolvedPlayer = playerResolver.resolve(job.accountId, job.playerName)
        val currentJob = jobRepository.findById(jobId).get()
        jobRepository.save(currentJob.copy(accountId = resolvedPlayer.accountId))
        return resolvedPlayer
    }

    private fun collectNewMatchesMetadata(
        jobId: ObjectId, matchCount: Int, matchIds: List<String>
    ): List<FetchedMatch> {
        updateProgress(jobId, "Calling match fetcher for new matches...")
        val newMatches = matchDataFetcher.collectNewMatches(matchCount, matchIds)
        updateProgress(jobId, "Match fetcher found ${newMatches.size} new matches, fetching details...")
        return newMatches
    }


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
                status = JobStatus.FAILED, completedAt = Instant.now(), errorMessage = error.message?.take(500)
            )
        )
    }

    private fun updateProgress(jobId: ObjectId, progress: String) {
        ensureJobNotCancelled(jobId)
        jobRepository.findById(jobId).ifPresent {
            if (it.status != JobStatus.CANCELLED) {
                jobRepository.save(it.copy(progress = progress))
            }
        }
    }

    private fun ensureJobNotCancelled(jobId: ObjectId) {
        if (isCancelled(jobId)) {
            logger.info("Job $jobId cancelled, aborting")
            throw IllegalStateException("Job $jobId aborted")
        }
    }
}

class JobCancelledException(message: String) : RuntimeException(message)