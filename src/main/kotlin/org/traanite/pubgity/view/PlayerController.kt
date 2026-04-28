package org.traanite.pubgity.view

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.traanite.pubgity.match.MatchService
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.player.StatsAggregationService
import java.time.Instant

@Controller
@RequestMapping("/players")
class PlayerController(
    private val playerService: PlayerService,
    private val matchService: MatchService,
    private val statsAggregationService: StatsAggregationService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PlayerController::class.java)
    }

    // todo dmg / round (mode)
    //  dmg / round (overall)
    //  move kd by mode calculation to aggregator
    @GetMapping
    fun listPlayers(@RequestParam(required = false) filter: String?, model: Model): String {
        val players = if (filter.isNullOrBlank()) {
            playerService.findAll()
        } else {
            playerService.searchByPlayerName(filter)
        }
        logger.debug("Player list: filter='{}', found {} players", filter ?: "", players.size)
        model.addAttribute("players", players.sortedByDescending { it.matches.size })
        model.addAttribute("filter", filter ?: "")
        return "players"
    }

    // todo performance of this call with 30 matches takes around a second
    //  could lead to performance issues with more users
    //  caching on aggregation service desirable
    @GetMapping("/{accountId}")
    fun playerDetail(@PathVariable accountId: String, model: Model): String {
        logger.info("Loading detail page for accountId: {}", accountId)
        val player = playerService.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")

        val matches = matchService.findByMatchIds(player.matches.map { it.matchId })
            .sortedByDescending { it.createdAt }
        logger.info("Player '{}': loaded {} matches from DB", player.playerName, matches.size)

        val perMatchStats = statsAggregationService.computePerMatchSkillData(matches, accountId)

        logger.debug("Computed per-match stats for player '{}', matches: {}", player.playerName, perMatchStats.size)
        val playerMatches = matches.map {
            PlayerMatchView(
                matchId = it.matchId,
                createdAt = it.createdAt,
                gameMode = it.gameMode,
                mapName = it.mapName,
                duration = it.duration,
                botCount = it.botCount,
                playerCount = it.rosters.flatMap { roster -> roster.participants }.size,
                placeTaken = it.rosters.flatMap { roster -> roster.participants }
                    .firstOrNull { player -> player.accountId == accountId }?.matchStats?.winPlace ?: 0
            )
        }

        val latestSnapshot = playerService.getLatestLifetimeStats(accountId)
        val playerAggregated = latestSnapshot?.stats?.let { statsAggregationService.computePlayerAggregatedView(it) }

        logger.debug("Prepared aggregated stats for player '{}'", player.playerName)

        model.addAttribute("player", player)
        model.addAttribute("matches", playerMatches)
        model.addAttribute("perMatchStats", perMatchStats)
        model.addAttribute("playerAggregated", playerAggregated)
        model.addAttribute("latestStats", latestSnapshot)
        return "player-detail"
    }
}


data class PlayerMatchView(
    val matchId: String,
    val createdAt: Instant,
    val gameMode: String,
    val mapName: String,
    val duration: Int,
    val botCount: Int = 0,
    val playerCount: Int,
    val placeTaken: Int
)