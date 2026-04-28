package org.traanite.pubgity.job

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.traanite.pubgity.match.*
import org.traanite.pubgity.player.LifetimeStatsUpdateResult
import org.traanite.pubgity.player.LifetimeStatsUpdater
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.pubgapi.toParticipantLifetimeStats
import java.time.Instant

@Service
class SingleMatchJobExecutor(
    private val jobRepository: UpdateJobRepository,
    private val playerService: PlayerService,
    private val matchService: MatchService,
    private val matchDataFetcher: MatchDataFetcher,
    private val lifetimeStatsUpdater: LifetimeStatsUpdater
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SingleMatchJobExecutor::class.java)
        private val jobType: JobType = JobType.SINGLE_MATCH
    }
    // todo what to do about jobs that hangs while running

    @Scheduled(fixedDelay = 5000)
    fun processNextJob() {
        logger.info("Checking for next $jobType job...")
        if (jobRepository.existsByJobTypeAndStatus(jobType, JobStatus.RUNNING)) {
            logger.info("A job is already running, skipping")
            return
        }
        // todo for future multi threading
        //  ensure no job for the same match ID running, but might be queued
        val job = jobRepository.findFirstByJobTypeAndStatusOrderByCreatedAtAsc(jobType, JobStatus.QUEUED) ?: return
        val jobId = job.id!!
        logger.info("Picked up job {} for player '{}' (matchCount={})", jobId, job.playerName, job.matchCount)

        jobRepository.save(job.copy(status = JobStatus.RUNNING, startedAt = Instant.now()))

        try {
            executeJob(job)
            markJobCompleted(jobId)
        } catch (e: MatchExistsException) {
            logger.info("Job {} match {} already exists in DB, marking job as completed", jobId, job.matchId)
            markJobCancelled(jobId, job, e)
        } catch (_: JobCancelledException) {
            logger.info("Job {} was cancelled during execution", jobId)
        } catch (e: Exception) {
            logger.error("Job {} failed for player '{}'", jobId, job.playerName, e)
            markJobFailed(jobId, job, e)
        }
    }

    private fun executeJob(job: UpdateJob) {
        val jobId = job.id!!
        ensureMatchNotExists(jobId, job.matchId!!)
        val newMatch = collectNewMatchMetadata(jobId, job.matchId!!)
        val participants = collectParticipants(jobId, newMatch)
        fetchLifetimeStats(jobId, participants)
        saveMatchSnapshot(jobId, newMatch)
    }

    private fun ensureMatchNotExists(jobId: ObjectId, matchId: String) {
        updateProgress(jobId, "Ensuring match is not already in DB...")
        if (matchService.existsByMatchId(matchId)) {
            throw MatchExistsException("Match $matchId already exists in DB")
        }
    }

    private fun collectNewMatchMetadata(jobId: ObjectId, matchId: String): FetchedMatch {
        updateProgress(jobId, "Calling match fetcher for new matches...")
        val newMatch = matchDataFetcher.fetchSingleMatch(matchId)
            ?: throw IllegalStateException("No match found for $matchId")
        return newMatch
    }

    private fun collectParticipants(jobId: ObjectId, match: FetchedMatch): Set<SimpleParticipant> {
        updateProgress(jobId, "Collecting participants from matches responses...")

        val participants = match.realParticipants.map {
            SimpleParticipant(it.accountId, it.playerName)
        }.toSet()
        logger.info("Collected ${participants.size} unique real participants from match ${match.matchId}")
        return participants
    }

    private fun fetchLifetimeStats(jobId: ObjectId, participants: Set<SimpleParticipant>) {
        var fetched = 0
        var skipped = 0

        participants.forEachIndexed { index, participant ->
            val progress =
                "Fetching lifetime stats (${index + 1}/${participants.size}, $fetched fetched, $skipped cached)"
            updateProgress(jobId, progress)

            // todo pull stats per season
            val updateLifetimeStats =
                lifetimeStatsUpdater.updateLifetimeStats(participant.accountId, participant.playerName)
            when (updateLifetimeStats) {
                LifetimeStatsUpdateResult.UPDATED -> fetched++
                LifetimeStatsUpdateResult.SKIPPED -> skipped++
                LifetimeStatsUpdateResult.FAILED -> throw IllegalStateException(
                    "Lifetime stats update failed for player ${participant.playerName} (${participant.accountId})"
                )
            }
        }

        logger.info("Lifetime stats: {} total, {} fetched, {} cached", participants.size, fetched, skipped)
    }

    private fun saveMatchSnapshot(jobId: ObjectId, fetchedMatch: FetchedMatch) {
        updateProgress(jobId, "Saving match snapshot ${fetchedMatch.matchId}")
        saveMatchSnapshot(fetchedMatch)
        logger.info("Saved new match snapshot ${fetchedMatch.matchId}")
    }

    private fun saveMatchSnapshot(fetchedMatch: FetchedMatch) {
        val rosterSnapshots = fetchedMatch.rosters.map { roster ->
            val participantSnapshots = roster.participants.map { participant ->
                val latestSnapshot = playerService.getLatestLifetimeStats(participant.accountId)
                MatchParticipant(
                    accountId = participant.accountId,
                    playerName = participant.playerName,
                    matchStats = participant.matchStats,
                    lifetimeStatsSnapshot = latestSnapshot?.stats?.toParticipantLifetimeStats()
                )
            }
            MatchRoster(
                rosterId = roster.rosterId, rank = roster.rank, won = roster.won, participants = participantSnapshots
            )
        }

        matchService.saveMatch(
            Match(
                matchId = fetchedMatch.matchId,
                createdAt = fetchedMatch.createdAt,
                gameMode = fetchedMatch.gameMode,
                mapName = fetchedMatch.mapName,
                duration = fetchedMatch.duration,
                botCount = fetchedMatch.botCount,
                rosters = rosterSnapshots
            )
        )

        // todo do it in events? separate service? we should have some normalization mechanism
        rosterSnapshots.flatMap { roster -> roster.participants }.forEach { participant ->
            playerService.appendMatchRefs(participant.accountId, listOf(fetchedMatch.matchId))
        }
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

    private fun markJobCancelled(jobId: ObjectId, job: UpdateJob, error: Exception) {
        val current = jobRepository.findById(jobId).orElse(job)!!
        jobRepository.save(
            current.copy(
                status = JobStatus.CANCELLED, completedAt = Instant.now(), errorMessage = error.message?.take(500)
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

