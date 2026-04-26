package org.traanite.pubgity.match

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

