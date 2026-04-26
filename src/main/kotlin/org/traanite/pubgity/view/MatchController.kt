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
        private val logger = LoggerFactory.getLogger(javaClass)
    }

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

        val modeExtractor = statsAggregationService.gameModeExtractor(match.gameMode)
        val participantsWithMode = match.rosters.flatMap { it.participants }
            .filter { it.lifetimeStatsSnapshot != null }
            .map { p ->
                val agg = statsAggregationService.aggregateParticipantStats(p.lifetimeStatsSnapshot!!, modeExtractor)
                ParticipantView(
                    accountId = p.accountId,
                    playerName = p.playerName,
                    placeTaken = p.matchStats?.winPlace ?: 123,
                    kills = agg?.totalKills ?: 0,
                    damage = agg?.totalDamageDealt ?: 0.0,
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
    val placeTaken: Int,
    val damage: Double,
    val wins: Int,
    val roundsPlayed: Int,
    val headshotKills: Int,
    val top10s: Int,
    val timeSurvived: Double,
    val bestRankPoint: Double,
    val kd: Double
)

