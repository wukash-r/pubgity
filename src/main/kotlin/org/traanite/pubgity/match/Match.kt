package org.traanite.pubgity.match

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "matches")
data class Match(
    @Id val id: ObjectId? = null,
    @Indexed(unique = true) val matchId: String,
    val createdAt: Instant,
    val gameMode: String,
    val mapName: String,
    val duration: Int,
    val botCount: Int = 0,
    val rosters: List<MatchRoster> = emptyList()
)

data class MatchRoster(
    val rosterId: String,
    val rank: Int,
    val won: Boolean,
    val participants: List<MatchParticipant> = emptyList()
)

data class MatchParticipant(
    val accountId: String,
    val playerName: String,
    val matchStats: MatchParticipantStats? = null,
    // todo match snapshot as a table with matchId as one of the properties in player package?
    val lifetimeStatsSnapshot: ParticipantLifetimeStats? = null
)

data class MatchParticipantStats(
    val kills: Int = 0,
    val assists: Int = 0,
    val dBNOs: Int = 0,
    val damageDealt: Double = 0.0,
    val deathType: String = "",
    val headshotKills: Int = 0,
    val heals: Int = 0,
    val boosts: Int = 0,
    val killPlace: Int = 0,
    val killStreaks: Int = 0,
    val longestKill: Double = 0.0,
    val revives: Int = 0,
    val rideDistance: Double = 0.0,
    val roadKills: Int = 0,
    val swimDistance: Double = 0.0,
    val teamKills: Int = 0,
    val timeSurvived: Double = 0.0,
    val vehicleDestroys: Int = 0,
    val walkDistance: Double = 0.0,
    val weaponsAcquired: Int = 0,
    val winPlace: Int = 0
)

// Match-owned lifetime stats types — independent from player/LifetimeStats

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParticipantLifetimeStats(
    val bestRankPoint: Double = 0.0,
    val gameModeStats: ParticipantGameModeStats = ParticipantGameModeStats()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParticipantGameModeStats(
    val duo: ParticipantModeStats? = null,
    val duoFpp: ParticipantModeStats? = null,
    val solo: ParticipantModeStats? = null,
    val soloFpp: ParticipantModeStats? = null,
    val squad: ParticipantModeStats? = null,
    val squadFpp: ParticipantModeStats? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParticipantModeStats(
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

