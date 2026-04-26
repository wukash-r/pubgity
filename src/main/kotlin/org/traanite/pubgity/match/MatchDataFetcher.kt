package org.traanite.pubgity.match

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.pubgapi.PubgApiClient
import org.traanite.pubgity.pubgapi.toMatchParticipantStats
import java.time.Instant

@Service
class MatchDataFetcher(
    private val matchService: MatchService,
    private val pubgApiClient: PubgApiClient
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

        return FetchedMatch(
            matchId = matchId,
            createdAt = Instant.parse(createdAtStr),
            gameMode = attrs.gameMode ?: "unknown",
            mapName = attrs.mapName ?: "unknown",
            duration = attrs.duration,
            rosters = rosters,
            botCount = botCount
        )
    }

}

data class FetchedMatch(
    val matchId: String,
    val createdAt: Instant,
    val gameMode: String,
    val mapName: String,
    val duration: Int,
    val rosters: List<FetchedRoster>,
    val botCount: Int
) {
    val realParticipants: List<SimpleParticipant>
        get() = rosters.flatMap { it.participants }.map { SimpleParticipant(it.accountId, it.playerName) }
}

data class SimpleParticipant(
    val accountId: String, val playerName: String
)

data class FetchedRoster(
    val rosterId: String,
    val rank: Int,
    val won: Boolean,
    val participants: List<FetchedParticipant>
)

data class FetchedParticipant(
    val accountId: String, val playerName: String, val matchStats: MatchParticipantStats?
)
