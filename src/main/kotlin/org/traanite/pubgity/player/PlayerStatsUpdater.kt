package org.traanite.pubgity.player

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.pubgapi.PubgApiClient
import org.traanite.pubgity.pubgapi.toLifetimeStats
import java.time.Duration
import java.time.Instant

@Service
class PlayerStatsUpdater(
    private val pubgApiClient: PubgApiClient,
    private val playerProperties: PlayerProperties,
    private val playerRepository: PlayerRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PlayerStatsUpdater::class.java)
    }

    fun updateLifetimeStats(accountId: String, playerName: String): StatsUpdateResult {
        val player =
            playerRepository.findByAccountId(accountId) ?: Player(accountId = accountId, playerName = playerName)

        logger.info("Updating lifetime stats for player '{}'", player.playerName)
        val latestSnapshot = player.lifetimeStats.latestSnapshot

        if (latestSnapshot != null && isWithinCacheThreshold(latestSnapshot.capturedAt)) {
            logger.debug(
                "Skipping lifetime stats update for '{}' - latest snapshot captured at {} is within cache threshold",
                player.playerName,
                latestSnapshot.capturedAt
            )
            return StatsUpdateResult.SKIPPED
        }

        try {
            val stats = pubgApiClient.getLifetimeStats(accountId).data.attributes.toLifetimeStats()
            player.updateLifetimeStats(stats).let {
                playerRepository.save(it)
                logger.info("Lifetime stats updated: {}", it.accountId)
            }
            return StatsUpdateResult.UPDATED
        } catch (e: Exception) {
            logger.warn("Failed to fetch lifetime stats for {}: {}", accountId, e.message)
            return StatsUpdateResult.FAILED
        }
    }

    fun updateCurrentSeasonPlayerStats(accountId: String, playerName: String): StatsUpdateResult {
        val player =
            playerRepository.findByAccountId(accountId) ?: Player(accountId = accountId, playerName = playerName)

        try {
            val currentSeason = pubgApiClient.getCurrentSeason()
                ?: throw IllegalStateException("No current season found")

            if (player.seasonStats.stats[currentSeason.id] != null
                && isWithinCacheThreshold(player.seasonStats.stats[currentSeason.id]!!.capturedAt)
            ) {
                logger.info(
                    "Skipping season stats update for '{}' - latest snapshot for season {} is within cache threshold",
                    player.playerName,
                    currentSeason.id
                )
                return StatsUpdateResult.SKIPPED
            }

            val playerSeasonStats = pubgApiClient.getSeasonStats(currentSeason.id, accountId)
            player.updateSeasonStats(playerSeasonStats).let {
                playerRepository.save(it)
                logger.info("Season stats updated: {}", it.accountId)
            }
            return StatsUpdateResult.UPDATED
        } catch (e: Exception) {
            logger.warn("Failed to fetch season stats for {}: {}", accountId, e.message)
            return StatsUpdateResult.FAILED
        }
    }

    // todo split these ttl in properties
    private fun isWithinCacheThreshold(capturedAt: Instant): Boolean {
        return Duration.between(capturedAt, Instant.now()) < playerProperties.statsTtl
    }
}

enum class StatsUpdateResult {
    UPDATED, SKIPPED, FAILED
}