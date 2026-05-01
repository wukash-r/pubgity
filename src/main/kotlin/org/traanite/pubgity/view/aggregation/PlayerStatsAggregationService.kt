package org.traanite.pubgity.view.aggregation

import org.springframework.stereotype.Service
import org.traanite.pubgity.player.GameModeStats
import org.traanite.pubgity.player.LifetimeStats
import org.traanite.pubgity.player.ModeStats
import org.traanite.pubgity.player.SeasonStats

@Service
class PlayerStatsAggregationService {

    fun computePlayerSeasonAggregatedView(stats: SeasonStats): PlayerAggregatedView? {
        val allModes = listOfNotNull(
            stats.gameModeStats.solo,
            stats.gameModeStats.soloFpp,
            stats.gameModeStats.duo,
            stats.gameModeStats.duoFpp,
            stats.gameModeStats.squad,
            stats.gameModeStats.squadFpp
        )
        if (allModes.isEmpty()) return null
        val totalRounds = allModes.sumOf { it.roundsPlayed }
        val totalDamage = allModes.sumOf { it.damageDealt }
        val totalKills = allModes.sumOf { it.kills }
        return PlayerAggregatedView(
            totalKills = totalKills,
            totalDamage = totalDamage,
            totalTop10s = allModes.sumOf { it.top10s },
            totalWins = allModes.sumOf { it.wins },
            totalRoundsPlayed = totalRounds,
            totalHeadshotKills = allModes.sumOf { it.headshotKills },
            totalTimeSurvived = allModes.sumOf { it.timeSurvived },
            bestRankPoint = 0.0,
            kd = if (totalRounds > 0) totalKills.toDouble() / totalRounds else 0.0,
            dmgPerRound = if (totalRounds > 0) totalDamage / totalRounds else 0.0
        )
    }

    fun computePlayerAggregatedView(stats: LifetimeStats): PlayerAggregatedView? {
        val allModes = listOfNotNull(
            stats.gameModeStats.solo,
            stats.gameModeStats.soloFpp,
            stats.gameModeStats.duo,
            stats.gameModeStats.duoFpp,
            stats.gameModeStats.squad,
            stats.gameModeStats.squadFpp
        )
        if (allModes.isEmpty()) return null
        val totalRounds = allModes.sumOf { it.roundsPlayed }
        val totalDamage = allModes.sumOf { it.damageDealt }
        val totalKills = allModes.sumOf { it.kills }
        return PlayerAggregatedView(
            totalKills = totalKills,
            totalDamage = totalDamage,
            totalTop10s = allModes.sumOf { it.top10s },
            totalWins = allModes.sumOf { it.wins },
            totalRoundsPlayed = totalRounds,
            totalHeadshotKills = allModes.sumOf { it.headshotKills },
            totalTimeSurvived = allModes.sumOf { it.timeSurvived },
            bestRankPoint = stats.bestRankPoint,
            kd = if (totalRounds > 0) totalKills.toDouble() / totalRounds else 0.0,
            dmgPerRound = if (totalRounds > 0) totalDamage / totalRounds else 0.0
        )
    }

    fun computeGameModeStatsView(gameModeStats: GameModeStats): GameModeStatsView {
        return GameModeStatsView(
            solo = gameModeStats.solo?.let { toModeStatsView(it) },
            soloFpp = gameModeStats.soloFpp?.let { toModeStatsView(it) },
            duo = gameModeStats.duo?.let { toModeStatsView(it) },
            duoFpp = gameModeStats.duoFpp?.let { toModeStatsView(it) },
            squad = gameModeStats.squad?.let { toModeStatsView(it) },
            squadFpp = gameModeStats.squadFpp?.let { toModeStatsView(it) }
        )
    }

    private fun toModeStatsView(stats: ModeStats) = ModeStatsView(
        roundsPlayed = stats.roundsPlayed,
        kills = stats.kills,
        wins = stats.wins,
        damageDealt = stats.damageDealt,
        top10s = stats.top10s,
        kd = if (stats.roundsPlayed > 0) stats.kills.toDouble() / stats.roundsPlayed else 0.0,
        dmgPerRound = if (stats.roundsPlayed > 0) stats.damageDealt / stats.roundsPlayed else 0.0
    )
}

data class PlayerAggregatedView(
    val totalKills: Int,
    val totalDamage: Double,
    val totalTop10s: Int,
    val totalWins: Int,
    val totalRoundsPlayed: Int,
    val totalHeadshotKills: Int,
    val totalTimeSurvived: Double,
    val bestRankPoint: Double,
    val kd: Double,
    val dmgPerRound: Double
)

data class GameModeStatsView(
    val solo: ModeStatsView?,
    val soloFpp: ModeStatsView?,
    val duo: ModeStatsView?,
    val duoFpp: ModeStatsView?,
    val squad: ModeStatsView?,
    val squadFpp: ModeStatsView?
)

data class ModeStatsView(
    val roundsPlayed: Int,
    val kills: Int,
    val wins: Int,
    val damageDealt: Double,
    val top10s: Int,
    val kd: Double,
    val dmgPerRound: Double
)
