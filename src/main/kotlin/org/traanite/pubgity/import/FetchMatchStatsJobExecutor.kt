package org.traanite.pubgity.import

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.traanite.pubgity.match.*
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.player.PlayerStatsUpdater
import org.traanite.pubgity.player.StatsUpdateResult
import org.traanite.pubgity.pubgapi.toParticipantLifetimeStats

@Service
class FetchMatchStatsJobExecutor(
    jobRepository: ImportJobRepository,
    private val playerService: PlayerService,
    private val matchService: MatchService,
    private val matchDataFetcher: MatchDataFetcher,
    private val playerStatsUpdater: PlayerStatsUpdater
) : BaseImportJobExecutor(jobRepository, JobType.FETCH_MATCH_STATS) {

    companion object {
        private val logger = LoggerFactory.getLogger(FetchMatchStatsJobExecutor::class.java)
    }

    // todo for future multi threading
    //  ensure no job for the same match ID running, but might be queued
    //  distributed rate limiting (only one active job by type running)
    //  jobs starting in a separate thread, heartbeat, kill hanging jobs

    // todo auto retry for failed jobs, up to x retries (configurable)
    //  creates a new job with retries ++ and sets old job as retired
    //  when clicked on frontend check if job hasn't been retried yet, if so, just skip

    // todo save ban type

    @Scheduled(fixedDelay = 5000)
    private fun jobScheduledTask() {
        processNextJob()
    }

    override fun executeJob(job: ImportJob) {
        val jobId = job.id!!
        ensureMatchNotExists(jobId, job.matchId!!)
        val newMatch = collectNewMatchMetadata(jobId, job.matchId)
        val participants = collectParticipants(jobId, newMatch)
        fetchLifetimeStats(jobId, participants)
        fetchCurrentSeasonStats(jobId, participants)
        saveMatchSnapshot(jobId, newMatch)
    }

    private fun ensureMatchNotExists(jobId: ObjectId, matchId: String) {
        updateProgress(jobId, "Ensuring match is not already in DB...")
        if (matchService.existsByMatchId(matchId)) {
            throw JobCancelledByExecutor("Match $matchId already exists in DB")
        }
    }

    private fun collectNewMatchMetadata(jobId: ObjectId, matchId: String): FetchedMatch {
        updateProgress(jobId, "Calling match fetcher for new matches...")
        val newMatch =
            matchDataFetcher.fetchSingleMatch(matchId) ?: throw IllegalStateException("No match found for $matchId")
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

            val updateResult = playerStatsUpdater.updateLifetimeStats(participant.accountId, participant.playerName)
            when (updateResult) {
                StatsUpdateResult.UPDATED -> fetched++
                StatsUpdateResult.SKIPPED -> skipped++
                StatsUpdateResult.FAILED -> throw IllegalStateException(
                    "Lifetime stats update failed for player ${participant.playerName} (${participant.accountId})"
                )
            }
        }

        logger.info("Lifetime stats: {} total, {} fetched, {} cached", participants.size, fetched, skipped)
    }

    private fun fetchCurrentSeasonStats(jobId: ObjectId, participants: Set<SimpleParticipant>) {
        var fetched = 0
        var skipped = 0

        participants.forEachIndexed { index, participant ->
            val progress =
                "Fetching current season stats (${index + 1}/${participants.size}, $fetched fetched, $skipped cached)"
            updateProgress(jobId, progress)

            val updateResult =
                playerStatsUpdater.updateCurrentSeasonPlayerStats(participant.accountId, participant.playerName)
            when (updateResult) {
                StatsUpdateResult.UPDATED -> fetched++
                StatsUpdateResult.SKIPPED -> skipped++
                StatsUpdateResult.FAILED -> throw IllegalStateException(
                    "Current season stats update failed for player ${participant.playerName} (${participant.accountId})"
                )
            }
        }

        logger.info("Current season stats: {} total, {} fetched, {} cached", participants.size, fetched, skipped)
    }

    private fun saveMatchSnapshot(jobId: ObjectId, fetchedMatch: FetchedMatch) {
        updateProgress(jobId, "Saving match snapshot ${fetchedMatch.matchId}")
        saveMatchSnapshot(fetchedMatch)
        logger.info("Saved new match snapshot ${fetchedMatch.matchId}")
    }

    private fun saveMatchSnapshot(fetchedMatch: FetchedMatch) {
        val participantsAccountIds =
            fetchedMatch.rosters.flatMap { roster -> roster.participants.map { it.accountId } }.toSet()

        val participatingPlayers = playerService.getLatestLifetimeStatsByPlayer(participantsAccountIds)

        val rosterSnapshots = fetchedMatch.rosters.map { roster ->
            val participantSnapshots = roster.participants.map { participant ->
                val latestSnapshot = participatingPlayers[participant.accountId]
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
                seasonId = fetchedMatch.seasonId,
                gameMode = fetchedMatch.gameMode,
                mapName = fetchedMatch.mapName,
                duration = fetchedMatch.duration,
                botCount = fetchedMatch.botCount,
                rosters = rosterSnapshots,
                telemetryFileUri = fetchedMatch.telemetryUri
            )
        )

        // todo do it in events? separate service? we should have some normalization mechanism
        rosterSnapshots.flatMap { roster -> roster.participants }.forEach { participant ->
            playerService.appendMatchRefs(participant.accountId, listOf(fetchedMatch.matchId))
        }
    }
}

