package org.traanite.pubgity.view

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.traanite.pubgity.player.PlayerService

@Controller
@RequestMapping("/players")
class PlayerController(
    private val playerService: PlayerService,
    private val playerStatsViewService: PlayerStatsViewService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PlayerController::class.java)
    }

    @GetMapping
    fun listPlayers(@RequestParam(required = false) filter: String?, model: Model): String {
        val players = if (filter.isNullOrBlank()) {
            playerService.findAll()
        } else {
            playerService.searchByPlayerName(filter)
        }
        logger.debug("Player list: filter='{}', found {} players", filter ?: "", players.size)
        model.addAttribute("players", players.sortedByDescending { it.matches.matchIds.size })
        model.addAttribute("filter", filter ?: "")
        return "players"
    }

    @GetMapping("/{accountId}")
    fun playerDetail(@PathVariable accountId: String, model: Model): String {
        if (accountId.isBlank()) return "players"
        playerStatsViewService.populateModelByAccountId(accountId, model)
        return "player-detail"
    }

    @GetMapping("/{accountId}/charts/lifetime")
    fun playerLifetimeCharts(@PathVariable accountId: String, model: Model): String {
        playerStatsViewService.populateModelByAccountId(accountId, model)
        return "player-lifetime-charts"
    }

    @GetMapping("/{accountId}/charts/season")
    fun playerSeasonCharts(@PathVariable accountId: String, model: Model): String {
        playerStatsViewService.populateModelByAccountId(accountId, model)
        return "player-season-charts"
    }
}
