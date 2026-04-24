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
import org.traanite.pubgity.model.GameModeStats
import org.traanite.pubgity.model.ModeStats
import org.traanite.pubgity.repository.MatchRepository
import org.traanite.pubgity.repository.PlayerRepository

@Controller
@RequestMapping("/players")
class PlayerController(
    private val playerRepository: PlayerRepository,
    private val matchRepository: MatchRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping
    fun listPlayers(@RequestParam(required = false) filter: String?, model: Model): String {
        val players = if (filter.isNullOrBlank()) {
            playerRepository.findAll()
        } else {
            playerRepository.searchByPlayerName(filter)
        }
        logger.debug("Player list: filter='{}', found {} players", filter ?: "", players.size)
        model.addAttribute("players", players)
        model.addAttribute("filter", filter ?: "")
        return "players"
    }

    @GetMapping("/{accountId}")
    fun playerDetail(@PathVariable accountId: String, model: Model): String {
        logger.info("Loading detail page for accountId: {}", accountId)
        val player = playerRepository.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")

        val matches = matchRepository.findByMatchIdIn(player.matchIds)
            .sortedByDescending { it.createdAt }
        logger.info("Player '{}': loaded {} matches from DB", player.playerName, matches.size)

        val perMatchStats = matches.map { match ->
            val realParticipants = match.participants.filter { it.lifetimeStats != null }
            val modeExtractor = gameModeExtractor(match.gameMode)

            val modeStatsList = realParticipants.mapNotNull { p ->
                modeExtractor(p.lifetimeStats!!.gameModeStats)
            }.filter { it.roundsPlayed > 0 }

            val kills = modeStatsList.map { it.kills.toDouble() }
            val damage = modeStatsList.map { it.damageDealt }
            val kd = modeStatsList.map { if (it.roundsPlayed > 0) it.kills.toDouble() / it.roundsPlayed else 0.0 }

            PerMatchSkillData(
                matchId = match.matchId,
                label = "${match.gameMode} - ${match.mapName}",
                participantCount = realParticipants.size,
                botCount = match.botCount,
                medianKills = median(kills),
                avgKills = avg(kills),
                medianDamage = median(damage),
                avgDamage = avg(damage),
                medianKD = median(kd),
                avgKD = avg(kd)
            )
        }

        model.addAttribute("player", player)
        model.addAttribute("matches", matches)
        model.addAttribute("perMatchStats", perMatchStats)
        return "player-detail"
    }

    @GetMapping("/{accountId}/matches/{matchId}")
    fun matchDetail(
        @PathVariable accountId: String,
        @PathVariable matchId: String,
        model: Model
    ): String {
        logger.info("Loading match detail: accountId={}, matchId={}", accountId, matchId)
        val player = playerRepository.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")
        val match = matchRepository.findByMatchId(matchId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found")

        val modeExtractor = gameModeExtractor(match.gameMode)
        val participantsWithMode = match.participants
            .filter { it.lifetimeStats != null }
            .map { p ->
                val ms = modeExtractor(p.lifetimeStats!!.gameModeStats)
                ParticipantView(
                    accountId = p.accountId,
                    playerName = p.playerName,
                    kills = ms?.kills ?: 0,
                    damage = ms?.damageDealt ?: 0.0,
                    wins = ms?.wins ?: 0,
                    roundsPlayed = ms?.roundsPlayed ?: 0,
                    headshotKills = ms?.headshotKills ?: 0,
                    top10s = ms?.top10s ?: 0,
                    kd = if ((ms?.roundsPlayed ?: 0) > 0) (ms!!.kills.toDouble() / ms.roundsPlayed) else 0.0
                )
            }
            .sortedByDescending { it.kills }

        model.addAttribute("player", player)
        model.addAttribute("match", match)
        model.addAttribute("participants", participantsWithMode)
        return "match-detail"
    }

    private fun gameModeExtractor(gameMode: String): (GameModeStats) -> ModeStats? {
        return when {
            gameMode.contains("squad") && gameMode.contains("fpp") -> { g -> g.squadFpp }
            gameMode.contains("squad") -> { g -> g.squad }
            gameMode.contains("duo") && gameMode.contains("fpp") -> { g -> g.duoFpp }
            gameMode.contains("duo") -> { g -> g.duo }
            gameMode.contains("solo") && gameMode.contains("fpp") -> { g -> g.soloFpp }
            gameMode.contains("solo") -> { g -> g.solo }
            else -> { g -> g.squadFpp ?: g.duoFpp ?: g.soloFpp }
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun avg(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        return values.sum() / values.size
    }
}

data class PerMatchSkillData(
    val matchId: String,
    val label: String,
    val participantCount: Int,
    val botCount: Int,
    val medianKills: Double,
    val avgKills: Double,
    val medianDamage: Double,
    val avgDamage: Double,
    val medianKD: Double,
    val avgKD: Double
)

data class ParticipantView(
    val accountId: String,
    val playerName: String,
    val kills: Int,
    val damage: Double,
    val wins: Int,
    val roundsPlayed: Int,
    val headshotKills: Int,
    val top10s: Int,
    val kd: Double
)
