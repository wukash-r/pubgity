package org.traanite.pubgity.player

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "player_lifetime_stats")
@CompoundIndex(name = "accountId_capturedAt", def = "{'accountId': 1, 'capturedAt': -1}")
data class PlayerLifetimeStatsSnapshot(
    @Id val id: ObjectId? = null,
    val accountId: String,
    val capturedAt: Instant = Instant.now(),
    val stats: LifetimeStats
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LifetimeStats(
    val bestRankPoint: Double = 0.0,
    val gameModeStats: GameModeStats = GameModeStats()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GameModeStats(
    val duo: ModeStats? = null,
    val duoFpp: ModeStats? = null,
    val solo: ModeStats? = null,
    val soloFpp: ModeStats? = null,
    val squad: ModeStats? = null,
    val squadFpp: ModeStats? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModeStats(
    val assists: Int = 0,
    val boosts: Int = 0,
    val dBNOs: Int = 0,
    val dailyKills: Int = 0,
    val dailyWins: Int = 0,
    val damageDealt: Double = 0.0,
    val days: Int = 0,
    val headshotKills: Int = 0,
    val heals: Int = 0,
    val killPoints: Int = 0,
    val kills: Int = 0,
    val longestKill: Double = 0.0,
    val longestTimeSurvived: Double = 0.0,
    val losses: Int = 0,
    val maxKillStreaks: Int = 0,
    val mostSurvivalTime: Double = 0.0,
    val rankPoints: Double = 0.0,
    val rankPointsTitle: String = "",
    val revives: Int = 0,
    val rideDistance: Double = 0.0,
    val roadKills: Int = 0,
    val roundMostKills: Int = 0,
    val roundsPlayed: Int = 0,
    val suicides: Int = 0,
    val swimDistance: Double = 0.0,
    val teamKills: Int = 0,
    val timeSurvived: Double = 0.0,
    val top10s: Int = 0,
    val vehicleDestroys: Int = 0,
    val walkDistance: Double = 0.0,
    val weaponsAcquired: Int = 0,
    val weeklyKills: Int = 0,
    val weeklyWins: Int = 0,
    val winPoints: Int = 0,
    val wins: Int = 0
)

