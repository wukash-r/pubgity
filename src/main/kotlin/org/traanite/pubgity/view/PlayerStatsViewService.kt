package org.traanite.pubgity.view

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.ui.Model
import org.springframework.web.server.ResponseStatusException
import org.traanite.pubgity.match.MatchService
import org.traanite.pubgity.player.Player
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.view.aggregation.MatchStatsAggregationService
import org.traanite.pubgity.view.aggregation.PlayerStatsAggregationService
import java.time.Instant

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

/**
 * Application service responsible for assembling the player statistics view model.
 * Shared between [PlayerController] and [HomeController] to avoid duplication.
 */
@Service
class PlayerStatsViewService(
    private val matchService: MatchService,
    private val playerService: PlayerService,
    private val matchStatsAggregationService: MatchStatsAggregationService,
    private val playerStatsAggregationService: PlayerStatsAggregationService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PlayerStatsViewService::class.java)
    }

    /**
     * Resolves a player by [accountId] and populates [model] with all statistics
     * needed to render the player-detail family of views.
     *
     * @throws ResponseStatusException 404 when the player is not found.
     */
    fun populateModelByAccountId(accountId: String, model: Model) {
        val player = playerService.findByAccountId(accountId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found")
        populateModel(player, model)
    }

    /**
     * Populates [model] using an already-resolved [Player].
     */
    fun populateModel(player: Player, model: Model) {
        val accountId = player.accountId ?: run {
            logger.warn("Player '{}' has no accountId, cannot populate stats model", player.playerName)
            return
        }

        logger.info("Loading detail for player '{}' (accountId={})", player.playerName, accountId)

        val matches = matchService.findByMatchIds(player.matches.matchIds.toList())
            .sortedByDescending { it.createdAt }
        logger.info("Player '{}': loaded {} matches", player.playerName, matches.size)

        val participatingAccountIds = matches
            .flatMap { it.rosters }
            .flatMap { roster -> roster.participants }
            .mapNotNull { it.accountId }
            .toSet()

        val participantsSeasonStats = playerService.getSeasonStatsByPlayer(participatingAccountIds)

        val perMatchStats = matchStatsAggregationService.computePerMatchSkillData(matches, accountId)
        val perMatchSeasonStats = matchStatsAggregationService.computePerMatchSeasonSkillData(
            matches, accountId, participantsSeasonStats
        )

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
                    .firstOrNull { p -> p.accountId == accountId }?.matchStats?.winPlace ?: 0
            )
        }

        val latestSnapshot = player.lifetimeStats.latestSnapshot
        val playerAggregated = latestSnapshot?.stats
            ?.let { playerStatsAggregationService.computePlayerAggregatedView(it) }
        val lifetimeGameModeStatsView = latestSnapshot?.stats?.gameModeStats
            ?.let { playerStatsAggregationService.computeGameModeStatsView(it) }

        val latestSeason = player.seasonStats.stats.values.maxByOrNull { it.capturedAt }
        val playerSeasonAggregated = latestSeason
            ?.let { playerStatsAggregationService.computePlayerSeasonAggregatedView(it) }
        val seasonGameModeStatsView = latestSeason?.gameModeStats
            ?.let { playerStatsAggregationService.computeGameModeStatsView(it) }

        model.addAttribute("player", player)
        model.addAttribute("matches", playerMatches)
        model.addAttribute("perMatchStats", perMatchStats)
        model.addAttribute("perMatchSeasonStats", perMatchSeasonStats)
        model.addAttribute("playerAggregated", playerAggregated)
        model.addAttribute("lifetimeGameModeStatsView", lifetimeGameModeStatsView)
        model.addAttribute("playerSeasonAggregated", playerSeasonAggregated)
        model.addAttribute("seasonGameModeStatsView", seasonGameModeStatsView)
        model.addAttribute("latestSeasonId", latestSeason?.seasonId)
        model.addAttribute("latestSeasonStats", latestSeason)
        model.addAttribute("latestStats", latestSnapshot)
    }
}


