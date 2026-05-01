package org.traanite.pubgity.import

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.traanite.pubgity.match.FetchedMatch
import org.traanite.pubgity.match.MatchDataFetcher
import org.traanite.pubgity.player.PlayerResolver
import org.traanite.pubgity.player.ResolvedPlayer

@Service
class FetchPlayerMatchesJobExecutor(
    private val jobRepository: ImportJobRepository,
    private val playerResolver: PlayerResolver,
    private val matchDataFetcher: MatchDataFetcher
) : BaseImportJobExecutor(jobRepository, JobType.FETCH_PLAYER_MATCHES) {

    companion object {
        private val logger = LoggerFactory.getLogger(FetchPlayerMatchesJobExecutor::class.java)
    }

    @Scheduled(fixedDelay = 5000)
    private fun jobScheduledTask() {
        processNextJob()
    }

    override fun executeJob(job: ImportJob) {
        val jobId = job.id!!
        val resolvedPlayer = resolvePlayer(jobId, job)
        val updatedJob = jobRepository.save(job.copy(accountId = resolvedPlayer.accountId))
        val newMatches = collectNewMatchesMetadata(
            jobId,
            updatedJob.matchCount!!,
            resolvedPlayer.matchIds
        )
        createFetchMatchStatsJobs(job, newMatches)
    }

    private fun createFetchMatchStatsJobs(job: ImportJob, newMatches: List<FetchedMatch>) {
        newMatches.map { match ->
            ImportJob(
                accountId = job.accountId, playerName = job.playerName, jobType = JobType.FETCH_MATCH_STATS,
                // match api responses are cached, no harm in only saving matchId here
                matchId = match.matchId
            )
        }.forEach { singleMatchJob ->
            val created = jobRepository.save(singleMatchJob)
            logger.info("Created single match job {} for match {}", created.id, created.matchId)
        }
    }

    private fun resolvePlayer(jobId: ObjectId, job: ImportJob): ResolvedPlayer {
        updateProgress(jobId, "Resolving player...")
        val resolvedPlayer = playerResolver.resolve(job.accountId, job.playerName)
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
}
