package org.traanite.pubgity.controller

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException
import org.traanite.pubgity.client.PubgApiClient
import org.traanite.pubgity.model.Player
import org.traanite.pubgity.repository.PlayerRepository

@Controller
@RequestMapping("/players")
class PlayerController(
    private val playerRepository: PlayerRepository,
    private val pubgApiClient: PubgApiClient
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun listPlayers(@RequestParam(required = false) filter: String?, model: Model): String {
        val players = if (filter.isNullOrBlank()) {
            playerRepository.findAll().filter { it.lifetimeStats != null }
        } else {
            playerRepository.searchByPlayerName(filter).filter { it.lifetimeStats != null }
        }
        model.addAttribute("players", players)
        model.addAttribute("filter", filter ?: "")
        return "players"
    }

    @GetMapping("/{accountId}")
    fun playerDetail(@PathVariable accountId: String, model: Model): String {
        val player = playerRepository.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")

        // Fetch recent matches to find participants
        val recentMatchIds = player.matchIds.take(10)
        val participantIds = mutableSetOf<String>()

        for (matchId in recentMatchIds) {
            try {
                val match = pubgApiClient.getMatch(matchId)
                match.included
                    .filter { it.type == "participant" }
                    .mapNotNull { it.attributes?.stats?.playerId }
                    .filter { it.startsWith("account.") && it != accountId }
                    .forEach { participantIds.add(it) }
            } catch (e: Exception) {
                logger.warn("Failed to fetch match {} for detail view: {}", matchId, e.message)
            }
        }

        // Load participants from DB (only those with stats)
        val participants = playerRepository.findByAccountIdIn(participantIds)
            .filter { it.lifetimeStats != null }

        // Compute average stats of participants for comparison
        val avgStats = computeAverageStats(participants)

        model.addAttribute("player", player)
        model.addAttribute("participantCount", participants.size)
        model.addAttribute("avgStats", avgStats)
        model.addAttribute("matchCount", recentMatchIds.size)
        return "player-detail"
    }

    private fun computeAverageStats(participants: List<Player>): Map<String, AverageModeStat> {
        val modes = mapOf(
            "duo" to { p: Player -> p.lifetimeStats?.gameModeStats?.duo },
            "duo-fpp" to { p: Player -> p.lifetimeStats?.gameModeStats?.duoFpp },
            "solo" to { p: Player -> p.lifetimeStats?.gameModeStats?.solo },
            "solo-fpp" to { p: Player -> p.lifetimeStats?.gameModeStats?.soloFpp },
            "squad" to { p: Player -> p.lifetimeStats?.gameModeStats?.squad },
            "squad-fpp" to { p: Player -> p.lifetimeStats?.gameModeStats?.squadFpp }
        )

        return modes.mapValues { (_, extractor) ->
            val stats = participants.mapNotNull(extractor).filter { it.roundsPlayed > 0 }
            if (stats.isEmpty()) {
                AverageModeStat()
            } else {
                val count = stats.size.toDouble()
                AverageModeStat(
                    avgKills = stats.sumOf { it.kills } / count,
                    avgDamage = stats.sumOf { it.damageDealt } / count,
                    avgWins = stats.sumOf { it.wins } / count,
                    avgTop10s = stats.sumOf { it.top10s } / count,
                    avgRoundsPlayed = stats.sumOf { it.roundsPlayed } / count,
                    avgHeadshotKills = stats.sumOf { it.headshotKills } / count,
                    avgAssists = stats.sumOf { it.assists } / count,
                    sampleSize = stats.size
                )
            }
        }
    }
}

data class AverageModeStat(
    val avgKills: Double = 0.0,
    val avgDamage: Double = 0.0,
    val avgWins: Double = 0.0,
    val avgTop10s: Double = 0.0,
    val avgRoundsPlayed: Double = 0.0,
    val avgHeadshotKills: Double = 0.0,
    val avgAssists: Double = 0.0,
    val sampleSize: Int = 0
)


