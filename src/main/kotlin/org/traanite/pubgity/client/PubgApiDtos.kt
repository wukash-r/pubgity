package org.traanite.pubgity.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// Exceptions
class TooManyRequestsException(message: String = "Rate limited by PUBG API") : RuntimeException(message)
class PubgApiException(message: String) : RuntimeException(message)

// --- Player search/lookup DTOs ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlayersResponse(val data: List<PlayerData>)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlayerData(
    val id: String,
    val attributes: PlayerAttributes,
    val relationships: PlayerRelationships? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlayerAttributes(val name: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlayerRelationships(val matches: RelationshipData? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RelationshipData(val data: List<RelationshipItem> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class RelationshipItem(val type: String, val id: String)

// --- Match DTOs ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchResponse(
    val data: MatchData,
    val included: List<MatchIncluded> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchData(
    val id: String,
    val attributes: MatchAttributes? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchAttributes(
    val createdAt: String? = null,
    val gameMode: String? = null,
    val mapName: String? = null,
    val duration: Int = 0
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchIncluded(
    val type: String,
    val id: String,
    val attributes: MatchIncludedAttributes? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MatchIncludedAttributes(
    val stats: ParticipantStats? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParticipantStats(
    val playerId: String? = null,
    val name: String? = null
)

// --- Lifetime stats DTOs ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class LifetimeStatsResponse(val data: LifetimeStatsData)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LifetimeStatsData(val attributes: LifetimeStatsAttributes)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LifetimeStatsAttributes(
    val gameModeStats: ApiGameModeStats = ApiGameModeStats(),
    val bestRankPoint: Double = 0.0
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiGameModeStats(
    val duo: ApiModeStats? = null,
    @JsonProperty("duo-fpp") val duoFpp: ApiModeStats? = null,
    val solo: ApiModeStats? = null,
    @JsonProperty("solo-fpp") val soloFpp: ApiModeStats? = null,
    val squad: ApiModeStats? = null,
    @JsonProperty("squad-fpp") val squadFpp: ApiModeStats? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ApiModeStats(
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

