package org.traanite.pubgity.player

import org.springframework.stereotype.Service
import org.traanite.pubgity.match.Match
import org.traanite.pubgity.match.ParticipantGameModeStats
import org.traanite.pubgity.match.ParticipantLifetimeStats
import org.traanite.pubgity.match.ParticipantModeStats

@Service
class StatsAggregationService {

    fun computePerMatchSkillData(
        matches: List<Match>, accountId: String
    ): List<PerMatchSkillData> {
        return matches.sortedBy { it.createdAt }.map { match ->
            val allParticipants = match.rosters.flatMap { it.participants }
            val realParticipants = allParticipants.filter { it.lifetimeStatsSnapshot != null }

            val aggregated = realParticipants.mapNotNull { p ->
                aggregateParticipantStats(p.lifetimeStatsSnapshot!!, match.gameMode)
            }

            val kills = aggregated.map { it.totalKills.toDouble() }
            val hsKills = aggregated.map { it.modeHeadshotKills.toDouble() }
            val kd = aggregated.map { it.modeKD }

            val damage = aggregated.map { it.totalDamageDealt }
            val dmgPerRoundLifetime = aggregated.map { it.dmgPerRoundLifetime }
            val dmgPerRoundMode = aggregated.map { it.dmgPerRoundMode }
            val dmgPerMinuteLifetime = aggregated.map { it.dmgPerMinuteLifetime }
            val dmgPerMinuteMode = aggregated.map { it.dmgPerMinuteMode }

            val brpAll = aggregated.map { it.bestRankPoint }
            val brpNonZero = brpAll.filter { it > 0.0 }

            val timeSurvived = aggregated.map { it.totalTimeSurvived }
            val rounds = aggregated.map { it.modeRoundsPlayed.toDouble() }

            val wins = aggregated.map { it.modeWins.toDouble() }
            val top10s = aggregated.map { it.totalTop10s.toDouble() }


            val playerSnapshot = allParticipants.firstOrNull { it.accountId == accountId }?.lifetimeStatsSnapshot?.let {
                aggregateParticipantStats(
                    it, match.gameMode
                )
            }

            PerMatchSkillData(
                matchId = match.matchId,
                label = "${match.createdAt} - ${match.gameMode} - ${match.mapName}",
                participantCount = realParticipants.size,
                botCount = match.botCount,
                minKills = min(kills),
                maxKills = max(kills),
                medianKills = median(kills),
                avgKills = avg(kills),
                minDamage = min(damage),
                maxDamage = max(damage),
                medianDamage = median(damage),
                avgDamage = avg(damage),
                minDmgPerRoundLifetime = min(dmgPerRoundLifetime),
                maxDmgPerRoundLifetime = max(dmgPerRoundLifetime),
                medianDmgPerRoundLifetime = median(dmgPerRoundLifetime),
                avgDmgPerRoundLifetime = avg(dmgPerRoundLifetime),
                minDmgPerRoundMode = min(dmgPerRoundMode),
                maxDmgPerRoundMode = max(dmgPerRoundMode),
                medianDmgPerRoundMode = median(dmgPerRoundMode),
                avgDmgPerRoundMode = avg(dmgPerRoundMode),
                minDmgPerMinuteLifetime = min(dmgPerMinuteLifetime),
                maxDmgPerMinuteLifetime = max(dmgPerMinuteLifetime),
                medianDmgPerMinuteLifetime = median(dmgPerMinuteLifetime),
                avgDmgPerMinuteLifetime = avg(dmgPerMinuteLifetime),
                minDmgPerMinuteMode = min(dmgPerMinuteMode),
                maxDmgPerMinuteMode = max(dmgPerMinuteMode),
                medianDmgPerMinuteMode = median(dmgPerMinuteMode),
                avgDmgPerMinuteMode = avg(dmgPerMinuteMode),
                minKD = min(kd),
                maxKD = max(kd),
                medianKD = median(kd),
                avgKD = avg(kd),
                minBestRankPoint = min(brpNonZero),
                maxBestRankPoint = max(brpNonZero),
                medianBestRankPoint = median(brpNonZero),
                avgBestRankPoint = avg(brpNonZero),
                rankedPlayerCount = brpNonZero.size,
                minTimeSurvived = min(timeSurvived),
                maxTimeSurvived = max(timeSurvived),
                medianTimeSurvived = median(timeSurvived),
                avgTimeSurvived = avg(timeSurvived),
                minWins = min(wins),
                maxWins = max(wins),
                medianWins = median(wins),
                avgWins = avg(wins),
                minRoundsPlayed = min(rounds),
                maxRoundsPlayed = max(rounds),
                medianRoundsPlayed = median(rounds),
                avgRoundsPlayed = avg(rounds),
                minHeadshotKills = min(hsKills),
                maxHeadshotKills = max(hsKills),
                medianHeadshotKills = median(hsKills),
                avgHeadshotKills = avg(hsKills),
                minTop10s = min(top10s),
                maxTop10s = max(top10s),
                medianTop10s = median(top10s),
                avgTop10s = avg(top10s),
                playerSnapshotKills = playerSnapshot?.totalKills?.toDouble() ?: 0.0,
                playerSnapshotDamage = playerSnapshot?.totalDamageDealt ?: 0.0,
                playerSnapshotDmgPerRoundLifetime = playerSnapshot?.dmgPerRoundLifetime ?: 0.0,
                playerSnapshotDmgPerRoundMode = playerSnapshot?.dmgPerRoundMode ?: 0.0,
                playerSnapshotDmgPerMinuteLifetime = playerSnapshot?.dmgPerMinuteLifetime ?: 0.0,
                playerSnapshotDmgPerMinuteMode = playerSnapshot?.dmgPerMinuteMode ?: 0.0,
                playerSnapshotKD = playerSnapshot?.modeKD ?: 0.0,
                playerSnapshotBestRankPoint = playerSnapshot?.bestRankPoint ?: 0.0,
                playerSnapshotTimeSurvived = playerSnapshot?.totalTimeSurvived ?: 0.0,
                playerSnapshotWins = playerSnapshot?.modeWins?.toDouble() ?: 0.0,
                playerSnapshotRoundsPlayed = playerSnapshot?.modeRoundsPlayed?.toDouble() ?: 0.0,
                playerSnapshotHeadshotKills = playerSnapshot?.modeHeadshotKills?.toDouble() ?: 0.0,
                playerSnapshotTop10s = playerSnapshot?.totalTop10s?.toDouble() ?: 0.0
            )
        }
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
        return PlayerAggregatedView(
            totalKills = allModes.sumOf { it.kills },
            totalDamage = allModes.sumOf { it.damageDealt },
            totalTop10s = allModes.sumOf { it.top10s },
            totalWins = allModes.sumOf { it.wins },
            totalRoundsPlayed = allModes.sumOf { it.roundsPlayed },
            totalHeadshotKills = allModes.sumOf { it.headshotKills },
            totalTimeSurvived = allModes.sumOf { it.timeSurvived },
            bestRankPoint = stats.bestRankPoint
        )
    }

    fun aggregateParticipantStats(
        stats: ParticipantLifetimeStats, gameMode: String
    ): AggregatedParticipantStats? {
        val allModes = listOfNotNull(
            stats.gameModeStats.solo,
            stats.gameModeStats.soloFpp,
            stats.gameModeStats.duo,
            stats.gameModeStats.duoFpp,
            stats.gameModeStats.squad,
            stats.gameModeStats.squadFpp
        )
        if (allModes.isEmpty()) return null

        val modeSpecific = gameModeExtractor(gameMode)(stats.gameModeStats)

        return AggregatedParticipantStats(
            totalKills = allModes.sumOf { it.kills },
            totalDamageDealt = allModes.sumOf { it.damageDealt },
            totalTop10s = allModes.sumOf { it.top10s },
            totalTimeSurvived = allModes.sumOf { it.timeSurvived },
            bestRankPoint = stats.bestRankPoint,
            modeWins = modeSpecific?.wins ?: 0,
            modeRoundsPlayed = modeSpecific?.roundsPlayed ?: 0,
            modeHeadshotKills = modeSpecific?.headshotKills ?: 0,
            modeKD = if ((modeSpecific?.roundsPlayed
                    ?: 0) > 0
            ) modeSpecific!!.kills.toDouble() / modeSpecific.roundsPlayed else 0.0,
            dmgPerRoundLifetime = if (allModes.sumOf { it.roundsPlayed } > 0) allModes.sumOf { it.damageDealt } / allModes.sumOf { it.roundsPlayed } else 0.0,
            dmgPerRoundMode = if ((modeSpecific?.roundsPlayed ?: 0) > 0) modeSpecific!!.damageDealt / modeSpecific.roundsPlayed else 0.0,
            dmgPerMinuteLifetime = if (allModes.sumOf { it.timeSurvived } > 0) allModes.sumOf { it.damageDealt } / (allModes.sumOf { it.timeSurvived } / 60.0) else 0.0,
            dmgPerMinuteMode = if ((modeSpecific?.timeSurvived ?: 0.0) > 0) modeSpecific!!.damageDealt / (modeSpecific.timeSurvived / 60.0) else 0.0
        )
    }

    fun gameModeExtractor(gameMode: String): (ParticipantGameModeStats) -> ParticipantModeStats? {
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

    private fun min(values: List<Double>): Double = values.minOrNull() ?: 0.0
    private fun max(values: List<Double>): Double = values.maxOrNull() ?: 0.0
}


data class PerMatchSkillData(
    val matchId: String,
    val label: String,
    val participantCount: Int,
    val botCount: Int,
    val minKills: Double,
    val maxKills: Double,
    val medianKills: Double,
    val avgKills: Double,
    val minDamage: Double,
    val maxDamage: Double,
    val medianDamage: Double,
    val avgDamage: Double,
    val minDmgPerRoundLifetime: Double,
    val maxDmgPerRoundLifetime: Double,
    val medianDmgPerRoundLifetime: Double,
    val avgDmgPerRoundLifetime: Double,
    val minDmgPerRoundMode: Double,
    val maxDmgPerRoundMode: Double,
    val medianDmgPerRoundMode: Double,
    val avgDmgPerRoundMode: Double,
    val minDmgPerMinuteLifetime: Double,
    val maxDmgPerMinuteLifetime: Double,
    val medianDmgPerMinuteLifetime: Double,
    val avgDmgPerMinuteLifetime: Double,
    val minDmgPerMinuteMode: Double,
    val maxDmgPerMinuteMode: Double,
    val medianDmgPerMinuteMode: Double,
    val avgDmgPerMinuteMode: Double,
    val minKD: Double,
    val maxKD: Double,
    val medianKD: Double,
    val avgKD: Double,
    val minBestRankPoint: Double,
    val maxBestRankPoint: Double,
    val medianBestRankPoint: Double,
    val avgBestRankPoint: Double,
    val rankedPlayerCount: Int,
    val minTimeSurvived: Double,
    val maxTimeSurvived: Double,
    val medianTimeSurvived: Double,
    val avgTimeSurvived: Double,
    val minWins: Double,
    val maxWins: Double,
    val medianWins: Double,
    val avgWins: Double,
    val minRoundsPlayed: Double,
    val maxRoundsPlayed: Double,
    val medianRoundsPlayed: Double,
    val avgRoundsPlayed: Double,
    val minHeadshotKills: Double,
    val maxHeadshotKills: Double,
    val medianHeadshotKills: Double,
    val avgHeadshotKills: Double,
    val minTop10s: Double,
    val maxTop10s: Double,
    val medianTop10s: Double,
    val avgTop10s: Double,
    val playerSnapshotKills: Double,
    val playerSnapshotDamage: Double,
    val playerSnapshotKD: Double,
    val playerSnapshotBestRankPoint: Double,
    val playerSnapshotTimeSurvived: Double,
    val playerSnapshotWins: Double,
    val playerSnapshotRoundsPlayed: Double,
    val playerSnapshotHeadshotKills: Double,
    val playerSnapshotTop10s: Double,
    val playerSnapshotDmgPerRoundLifetime: Double,
    val playerSnapshotDmgPerRoundMode: Double,
    val playerSnapshotDmgPerMinuteLifetime: Double,
    val playerSnapshotDmgPerMinuteMode: Double
)

data class AggregatedParticipantStats(
    val totalKills: Int,
    val totalDamageDealt: Double,
    val totalTop10s: Int,
    val totalTimeSurvived: Double,
    val bestRankPoint: Double,
    val modeWins: Int,
    val modeRoundsPlayed: Int,
    val modeHeadshotKills: Int,
    val modeKD: Double,
    val dmgPerRoundLifetime: Double,
    val dmgPerRoundMode: Double,
    val dmgPerMinuteLifetime: Double,
    val dmgPerMinuteMode: Double
)

data class PlayerAggregatedView(
    val totalKills: Int,
    val totalDamage: Double,
    val totalTop10s: Int,
    val totalWins: Int,
    val totalRoundsPlayed: Int,
    val totalHeadshotKills: Int,
    val totalTimeSurvived: Double,
    val bestRankPoint: Double
)

