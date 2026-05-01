package org.traanite.pubgity.import

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import java.time.Instant

abstract class BaseImportJobExecutor(
    private val jobRepository: ImportJobRepository, private val jobType: JobType
) {

    companion object {
        private val logger = LoggerFactory.getLogger(BaseImportJobExecutor::class.java)
    }

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
        } catch (e: JobCancelledByExecutor) {
            logger.info("Job {} cancelled by executor {}", jobId, e.message)
            markJobCancelled(jobId, job, e)
        } catch (_: JobCancelledOutsideExecutor) {
            logger.info("Job {} cancelled outside executor", jobId)
        } catch (e: Exception) {
            logger.error("Job {} failed for player '{}'", jobId, job.playerName, e)
            markJobFailed(jobId, job, e)
        }
    }

    abstract fun executeJob(job: ImportJob)

    protected fun isCancelled(jobId: ObjectId): Boolean {
        val current = jobRepository.findById(jobId).orElse(null) ?: return true
        return current.status == JobStatus.CANCELLED
    }

    protected fun markJobCompleted(jobId: ObjectId) {
        val current = jobRepository.findById(jobId).get()
        if (current.status == JobStatus.CANCELLED) return
        jobRepository.save(current.copy(status = JobStatus.COMPLETED, completedAt = Instant.now()))
        logger.info("Job {} completed", jobId)
    }

    protected fun markJobFailed(jobId: ObjectId, job: ImportJob, error: Exception) {
        val current = jobRepository.findById(jobId).orElse(job)!!
        if (current.status == JobStatus.CANCELLED) return
        jobRepository.save(
            current.copy(
                status = JobStatus.FAILED, completedAt = Instant.now(), errorMessage = error.message?.take(500)
            )
        )
    }

    protected fun updateProgress(jobId: ObjectId, progress: String) {
        ensureJobNotCancelled(jobId)
        jobRepository.findById(jobId).ifPresent {
            if (it.status != JobStatus.CANCELLED) {
                jobRepository.save(it.copy(progress = progress))
            }
        }
    }

    protected fun ensureJobNotCancelled(jobId: ObjectId) {
        if (isCancelled(jobId)) {
            logger.info("Job $jobId cancelled, aborting")
            throw IllegalStateException("Job $jobId aborted")
        }
    }

    private fun markJobCancelled(jobId: ObjectId, job: ImportJob, error: Exception) {
        val current = jobRepository.findById(jobId).orElse(job)!!
        jobRepository.save(
            current.copy(
                status = JobStatus.SKIPPED, completedAt = Instant.now(), errorMessage = error.message?.take(500)
            )
        )
    }
}

class JobCancelledOutsideExecutor(message: String) : RuntimeException(message)
class JobCancelledByExecutor(message: String) : RuntimeException(message)
