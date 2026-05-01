package org.traanite.pubgity.match

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.pubgapi.PubgApiClient
import org.traanite.pubgity.pubgapi.toMatchParticipantStats
import java.net.URI
import java.time.Instant

@Service
class MatchDataFetcher(
    private val matchService: MatchService, private val pubgApiClient: PubgApiClient
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MatchDataFetcher::class.java)
    }

    fun collectNewMatches(matchCount: Int, matchIds: List<String>): List<FetchedMatch> {
        logger.info("Collecting metadata for {} matches, matchIds: {}", matchCount, matchIds)
        val fetchedMatches = matchIds.mapNotNull { matchId ->
            fetchSingleMatch(matchId)
        }

        logger.info("Fetched metadata for {} matches, filtering new matches against DB", fetchedMatches.size)
        val newMatches = filterNewMatches(matchCount, fetchedMatches)

        // todo save new matches metadata, match stats etc and come up with state enum
        //  "match per job" executor will only fetch participants stats then, and after that update match object
        //  emit an event with participant list so that Player objects are either updated or created with that matchId
        //  on event, upsert on mongoTemplate needed with atomic $addToSet and setOnInsert playerId for thread safety
        logger.info("After filtering, {} new matches to process", newMatches.size)
        return newMatches
    }

    private fun filterNewMatches(matchCount: Int, allMatches: List<FetchedMatch>): List<FetchedMatch> {
        val selectedMatches = allMatches.sortedByDescending { it.createdAt }.take(matchCount)
        val storedMatchIds = matchService.findExistingMatchIds(selectedMatches.map { it.matchId })
        val newMatches = selectedMatches.filter { it.matchId !in storedMatchIds }
        return newMatches
    }

    fun fetchSingleMatch(matchId: String): FetchedMatch? {
        val matchResponse = pubgApiClient.getMatch(matchId)
        val attrs = matchResponse.data.attributes
        val createdAtStr = attrs?.createdAt ?: return null

        val participantLookup = mutableMapOf<String, FetchedParticipant>()
        var botCount = 0

        matchResponse.included.filter { it.type == "participant" }.forEach { included ->
            val stats = included.attributes?.stats
            val pid = stats?.playerId ?: return@forEach
            val pname = stats.name ?: return@forEach

            when {
                pid.startsWith("account.") -> participantLookup[included.id] = FetchedParticipant(
                    accountId = pid, playerName = pname, matchStats = stats.toMatchParticipantStats()
                )

                pid.startsWith("ai.") -> botCount++
            }
        }

        val rosters = matchResponse.included.filter { it.type == "roster" }.map { roster ->
            val rosterStats = roster.attributes?.stats
            val rank = rosterStats?.rank ?: 0
            val won = roster.attributes?.won == "true"
            val rosterParticipantIds = roster.relationships?.participants?.data?.map { it.id } ?: emptyList()

            val rosterParticipants = rosterParticipantIds.mapNotNull { participantLookup[it] }

            FetchedRoster(
                rosterId = roster.id, rank = rank, won = won, participants = rosterParticipants
            )
        }.filter { it.participants.isNotEmpty() }

        val season =
            if (matchResponse.data.attributes.seasonState != null
                && matchResponse.data.attributes.seasonState == "progress"
            ) {
                pubgApiClient.getCurrentSeason()
            } else {
                null
            }
        return FetchedMatch(
            matchId = matchId,
            createdAt = Instant.parse(createdAtStr),
            seasonId = season?.id,
            gameMode = attrs.gameMode ?: "unknown",
            mapName = attrs.mapName ?: "unknown",
            duration = attrs.duration,
            rosters = rosters,
            botCount = botCount,
            telemetryUri = extractTelemetryUrl(matchId, matchResponse)
        )
    }

    private fun extractTelemetryUrl(matchId: String, matchResponse: org.traanite.pubgity.pubgapi.MatchResponse): URI? {
        logger.info("Extracting telemetry url from match $matchId")
        val assetIds = matchResponse.data.relationships?.assets?.data
            ?.filter { it.type == "asset" }
            ?.map { it.id }
            ?: return null

        logger.info("Match {}: found asset IDs in relationships: {}", matchId, assetIds)

        val telemetryUrlStr = matchResponse.included
            .firstOrNull { it.type == "asset" && it.id in assetIds && it.attributes?.name == "telemetry" }
            ?.attributes?.url
            ?: return null

        logger.info("Match {}: found telemetry URL in included assets: {}", matchId, telemetryUrlStr)

        return try {
            URI(telemetryUrlStr)
        } catch (_: Exception) {
            logger.warn("Invalid telemetry URI in match {}: {}", matchId, telemetryUrlStr)
            null
        }
    }

}

data class FetchedMatch(
    val matchId: String,
    val createdAt: Instant,
    val seasonId: String?,
    val gameMode: String,
    val mapName: String,
    val duration: Int,
    val rosters: List<FetchedRoster>,
    val botCount: Int,
    val telemetryUri: URI?
) {
    val realParticipants: List<SimpleParticipant>
        get() = rosters.flatMap { it.participants }.map { SimpleParticipant(it.accountId, it.playerName) }
}

data class SimpleParticipant(
    val accountId: String, val playerName: String
)

data class FetchedRoster(
    val rosterId: String, val rank: Int, val won: Boolean, val participants: List<FetchedParticipant>
)

data class FetchedParticipant(
    val accountId: String, val playerName: String, val matchStats: MatchParticipantStats?
)
