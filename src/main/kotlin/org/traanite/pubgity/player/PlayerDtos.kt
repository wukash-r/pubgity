package org.traanite.pubgity.player

data class PerMatchSkillData(
    val matchId: String,
    val label: String,
    val participantCount: Int,
    val botCount: Int,
    val minKills: Double, val maxKills: Double,
    val medianKills: Double, val avgKills: Double,
    val minDamage: Double, val maxDamage: Double,
    val medianDamage: Double, val avgDamage: Double,
    val minKD: Double, val maxKD: Double,
    val medianKD: Double, val avgKD: Double,
    val minBestRankPoint: Double, val maxBestRankPoint: Double,
    val medianBestRankPoint: Double, val avgBestRankPoint: Double,
    val rankedPlayerCount: Int,
    val minTimeSurvived: Double, val maxTimeSurvived: Double,
    val medianTimeSurvived: Double, val avgTimeSurvived: Double,
    val minWins: Double, val maxWins: Double,
    val medianWins: Double, val avgWins: Double,
    val minRoundsPlayed: Double, val maxRoundsPlayed: Double,
    val medianRoundsPlayed: Double, val avgRoundsPlayed: Double,
    val minHeadshotKills: Double, val maxHeadshotKills: Double,
    val medianHeadshotKills: Double, val avgHeadshotKills: Double,
    val minTop10s: Double, val maxTop10s: Double,
    val medianTop10s: Double, val avgTop10s: Double,
    val playerSnapshotKills: Double,
    val playerSnapshotDamage: Double,
    val playerSnapshotKD: Double,
    val playerSnapshotBestRankPoint: Double,
    val playerSnapshotTimeSurvived: Double,
    val playerSnapshotWins: Double,
    val playerSnapshotRoundsPlayed: Double,
    val playerSnapshotHeadshotKills: Double,
    val playerSnapshotTop10s: Double
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
    val modeKD: Double
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

