package org.traanite.pubgity.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.Instant

@Document(collection = "players")
data class Player(
    @Id val id: ObjectId? = null,
    val playerName: String,
    @Indexed(unique = true, sparse = true) val accountId: String? = null,
    val lastUpdated: Instant? = null,
    val matchIds: List<String> = emptyList(),
    val lifetimeStats: LifetimeStats? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LifetimeStats(
    val bestRankPoint: Double = 0.0, val gameModeStats: GameModeStats = GameModeStats()
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

