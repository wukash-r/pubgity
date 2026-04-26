package org.traanite.pubgity.job

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.traanite.pubgity.match.*
import org.traanite.pubgity.player.*
import org.traanite.pubgity.pubgapi.toParticipantLifetimeStats
import java.time.Instant

@Service
class JobExecutorService(
    private val jobRepository: UpdateJobRepository,
    private val playerService: PlayerService,
    private val matchService: MatchService,
    private val matchDataFetcher: MatchDataFetcher,
    private val lifetimeStatsUpdater: LifetimeStatsUpdater,
    private val playerResolver: PlayerResolver
) {
    companion object {
        private val logger = LoggerFactory.getLogger(javaClass)
    }
    // todo what to do about jobs that hangs while running

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
            executeJob(job)
            markJobCompleted(jobId)
        } catch (e: Exception) {
            logger.error("Job {} failed for player '{}'", jobId, job.playerName, e)
            markJobFailed(jobId, job, e)
        }
    }

    private fun executeJob(job: UpdateJob) {
        val jobId = job.id!!
        val resolvedPlayer = resolvePlayer(jobId, job)
        val newMatches = collectNewMatchesMetadata(jobId, job.matchCount, resolvedPlayer.matchIds)
        val participants = collectParticipants(jobId, newMatches)
        fetchLifetimeStats(jobId, participants)
        saveMatchSnapshots(jobId, newMatches)
    }

    private fun collectNewMatchesMetadata(
        jobId: ObjectId, matchCount: Int, matchIds: List<String>
    ): List<FetchedMatch> {
        updateProgress(jobId, "Calling match fetcher for new matches...")
        val newMatches = matchDataFetcher.collectNewMatches(matchCount, matchIds)
        updateProgress(jobId, "Match fetcher found ${newMatches.size} new matches, fetching details...")
        return newMatches
    }

    private fun resolvePlayer(jobId: ObjectId, job: UpdateJob): ResolvedPlayer {
        updateProgress(jobId, "Resolving player...")
        val resolvedPlayer = playerResolver.resolve(job.accountId, job.playerName)
        val currentJob = jobRepository.findById(jobId).get()
        jobRepository.save(currentJob.copy(accountId = resolvedPlayer.accountId))
        return resolvedPlayer
    }

    private fun collectParticipants(jobId: ObjectId, matches: List<FetchedMatch>): Set<SimpleParticipant> {
        updateProgress(jobId, "Collecting participants from matches responses...")

        val participants =
            matches.flatMap { it.realParticipants }.map { SimpleParticipant(it.accountId, it.playerName) }.toSet()
        logger.info("Collected ${participants.size} unique real participants across ${matches.size} new matches")
        return participants
    }

    private fun fetchLifetimeStats(jobId: ObjectId, participants: Set<SimpleParticipant>) {
        var fetched = 0
        var skipped = 0

        participants.forEachIndexed { index, participant ->
            val progress =
                "Fetching lifetime stats (${index + 1}/${participants.size}, $fetched fetched, $skipped cached)"
            updateProgress(jobId, progress)

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

    private fun saveMatchSnapshots(jobId: ObjectId, matches: List<FetchedMatch>) {
        updateProgress(jobId, "Saving match snapshots...")

        matches.forEach { fetchedMatch ->
            updateProgress(jobId, "Saving match snapshot ${fetchedMatch.matchId}")
            saveMatchSnapshot(fetchedMatch)
        }
        logger.info("Saved {} new match snapshots", matches.size)
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

