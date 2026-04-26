package org.traanite.pubgity.player

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

@Controller
@RequestMapping("/players")
class PlayerController(
    private val playerService: PlayerService,
    private val matchService: MatchService,
    private val statsAggregationService: StatsAggregationService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun listPlayers(@RequestParam(required = false) filter: String?, model: Model): String {
        val players = if (filter.isNullOrBlank()) {
            playerService.findAll()
        } else {
            playerService.searchByPlayerName(filter)
        }
        logger.debug("Player list: filter='{}', found {} players", filter ?: "", players.size)
        model.addAttribute("players", players)
        model.addAttribute("filter", filter ?: "")
        return "players"
    }

    @GetMapping("/{accountId}")
    fun playerDetail(@PathVariable accountId: String, model: Model): String {
        logger.info("Loading detail page for accountId: {}", accountId)
        val player = playerService.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")

        val matches = matchService.findByMatchIds(player.matches.map { it.matchId })
            .sortedByDescending { it.createdAt }
        logger.info("Player '{}': loaded {} matches from DB", player.playerName, matches.size)

        val perMatchStats = statsAggregationService.computePerMatchSkillData(matches, accountId)

        val latestSnapshot = playerService.getLatestLifetimeStats(accountId)
        val playerAggregated = latestSnapshot?.stats?.let { statsAggregationService.computePlayerAggregatedView(it) }

        model.addAttribute("player", player)
        model.addAttribute("matches", matches)
        model.addAttribute("perMatchStats", perMatchStats)
        model.addAttribute("playerAggregated", playerAggregated)
        model.addAttribute("latestStats", latestSnapshot)
        return "player-detail"
    }
}

