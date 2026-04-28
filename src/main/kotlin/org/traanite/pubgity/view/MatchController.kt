package org.traanite.pubgity.view

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.server.ResponseStatusException
import org.traanite.pubgity.match.MatchService
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.player.StatsAggregationService

@Controller
@RequestMapping("/players/{accountId}/matches")
class MatchController(
    private val matchService: MatchService,
    private val playerService: PlayerService,
    private val statsAggregationService: StatsAggregationService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MatchController::class.java)
    }

    // todo dmg / round (mode)
    //  dmg / round (overall)
    //  player stats of the match
    @GetMapping("/{matchId}")
    fun matchDetail(
        @PathVariable accountId: String,
        @PathVariable matchId: String,
        model: Model
    ): String {
        logger.info("Loading match detail: accountId={}, matchId={}", accountId, matchId)
        val player = playerService.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")
        val match = matchService.findByMatchId(matchId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found")

        val participantsWithMode = match.rosters.flatMap { roster ->
                roster.participants.map { p -> Pair(roster, p) }
            }
            .filter { it.second.lifetimeStatsSnapshot != null }
            .map { (roster, p) ->
                val agg = statsAggregationService.aggregateParticipantStats(p.lifetimeStatsSnapshot!!, match.gameMode)
                ParticipantView(
                    accountId = p.accountId,
                    playerName = p.playerName,
                    winPlace = p.matchStats?.winPlace ?: 123,
                    rosterWinPlace = roster.rank,
                    killsMatch = p.matchStats?.kills ?: 0,
                    dmgMatch = p.matchStats?.damageDealt?.toInt() ?: 0,
                    kills = agg?.totalKills ?: 0,
                    damage = agg?.totalDamageDealt ?: 0.0,
                    dmgPerRoundLifetime = agg?.dmgPerRoundLifetime ?: 0.0,
                    dmgPerRoundMode = agg?.dmgPerRoundMode ?: 0.0,
                    dmgPerMinuteLifetime = agg?.dmgPerMinuteLifetime ?: 0.0,
                    dmgPerMinuteMode = agg?.dmgPerMinuteMode ?: 0.0,
                    wins = agg?.modeWins ?: 0,
                    roundsPlayed = agg?.modeRoundsPlayed ?: 0,
                    headshotKills = agg?.modeHeadshotKills ?: 0,
                    top10s = agg?.totalTop10s ?: 0,
                    timeSurvived = agg?.totalTimeSurvived ?: 0.0,
                    bestRankPoint = agg?.bestRankPoint ?: 0.0,
                    kd = agg?.modeKD ?: 0.0
                )
            }
            .sortedByDescending { it.kills }

        model.addAttribute("player", player)
        model.addAttribute("match", match)
        model.addAttribute("participants", participantsWithMode)
        return "match-detail"
    }
}

data class ParticipantView(
    val accountId: String,
    val playerName: String,
    val kills: Int,
    val winPlace: Int,
    val rosterWinPlace: Int,
    val killsMatch: Int,
    val dmgMatch: Int,
    val damage: Double,
    val dmgPerRoundLifetime: Double,
    val dmgPerRoundMode: Double,
    val dmgPerMinuteLifetime: Double,
    val dmgPerMinuteMode: Double,
    val wins: Int,
    val roundsPlayed: Int,
    val headshotKills: Int,
    val top10s: Int,
    val timeSurvived: Double,
    val bestRankPoint: Double,
    val kd: Double
)

