package org.traanite.pubgity.player

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "players")
class Player(
    @Id
    val id: ObjectId? = null,
    @Indexed(unique = true)
    val playerName: String,
    @Indexed(unique = true, sparse = true)
    val accountId: String? = null,
    val matches: PlayerMatches = PlayerMatches(),
    val seasonStats: PlayerSeasonStats = PlayerSeasonStats(),
    val lifetimeStats: PlayerLifetimeStats = PlayerLifetimeStats(),
) {

    fun updateAccountData(accountId: String, playerName: String): Player {
        return Player(
            id = id,
            playerName = playerName,
            accountId = accountId,
            matches = matches,
            seasonStats = seasonStats,
            lifetimeStats = lifetimeStats
        )
    }

    fun addMatches(matchIds: Collection<String>): Player {
        return Player(
            id = id,
            playerName = playerName,
            accountId = accountId,
            matches = PlayerMatches(matches.matchIds + matchIds),
            seasonStats = seasonStats,
            lifetimeStats = lifetimeStats,
        )
    }

    fun removeMatches(matchIds: Collection<String>): Player {
        val matchIdsToRemove = matchIds.toSet()

        return Player(
            id = id,
            playerName = playerName,
            accountId = accountId,
            matches = PlayerMatches(matches.matchIds.filterNot { it in matchIdsToRemove }.toSet()),
            seasonStats = seasonStats,
            lifetimeStats = lifetimeStats,
        )
    }

    fun updateLifetimeStats(lifetimeStats: LifetimeStats): Player {
        val newSnapshot = PlayerLifetimeStatsSnapshot(stats = lifetimeStats)
        return Player(
            id = id,
            playerName = playerName,
            accountId = accountId,
            matches = matches,
            seasonStats = seasonStats,
            lifetimeStats = PlayerLifetimeStats(
                latestSnapshot = newSnapshot,
                snapshots = this.lifetimeStats.snapshots + newSnapshot
            )
        )
    }

    fun updateSeasonStats(seasonStats: SeasonStats): Player {
        return Player(
            id = id,
            playerName = playerName,
            accountId = accountId,
            matches = matches,
            seasonStats = PlayerSeasonStats(stats = this.seasonStats.stats + (seasonStats.seasonId to seasonStats)),
            lifetimeStats = lifetimeStats
        )
    }

}

data class PlayerMatches(
    val matchIds: Set<String> = emptySet(),
)

data class PlayerSeasonStats(
    val stats: Map<String, SeasonStats> = emptyMap(),
)

data class PlayerLifetimeStats(
    val latestSnapshot: PlayerLifetimeStatsSnapshot? = null,
    val snapshots: List<PlayerLifetimeStatsSnapshot> = emptyList()
)

data class PlayerLifetimeStatsSnapshot(
    val capturedAt: Instant = Instant.now(),
    val stats: LifetimeStats
)

data class SeasonStats(
    val seasonId: String,
    val capturedAt: Instant = Instant.now(),
    val gameModeStats: GameModeStats = GameModeStats()
)

data class LifetimeStats(
    val bestRankPoint: Double = 0.0,
    val gameModeStats: GameModeStats = GameModeStats()
)

data class GameModeStats(
    val duo: ModeStats? = null,
    val duoFpp: ModeStats? = null,
    val solo: ModeStats? = null,
    val soloFpp: ModeStats? = null,
    val squad: ModeStats? = null,
    val squadFpp: ModeStats? = null
)

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

