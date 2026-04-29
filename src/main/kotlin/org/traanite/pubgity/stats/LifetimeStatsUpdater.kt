package org.traanite.pubgity.stats

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.player.PlayerProperties
import org.traanite.pubgity.player.PlayerService
import org.traanite.pubgity.pubgapi.PubgApiClient
import org.traanite.pubgity.pubgapi.toLifetimeStats
import java.time.Duration
import java.time.Instant

@Service
class LifetimeStatsUpdater(
    private val playerService: PlayerService,
    private val pubgApiClient: PubgApiClient,
    private val playerProperties: PlayerProperties,
    private val statsService: StatsService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(LifetimeStatsUpdater::class.java)
    }

    fun updateLifetimeStats(accountId: String, playerName: String): LifetimeStatsUpdateResult {
        val latestSnapshot = statsService.getLatestLifetimeStats(accountId)

        if (latestSnapshot != null && isWithinCacheThreshold(latestSnapshot.capturedAt)) {
            return LifetimeStatsUpdateResult.SKIPPED
        }

        try {
            val stats = pubgApiClient.getLifetimeStats(accountId).data.attributes.toLifetimeStats()
            statsService.saveLifetimeStatsSnapshot(accountId, stats)

            if (playerService.findByAccountId(accountId) == null) {
                playerService.resolveOrCreatePlayer(
                    accountId, playerName, playerName
                )
            }

            return LifetimeStatsUpdateResult.UPDATED
        } catch (e: Exception) {
            logger.warn("Failed to fetch lifetime stats for {}: {}", accountId, e.message)
            return LifetimeStatsUpdateResult.FAILED
        }
    }

    private fun isWithinCacheThreshold(capturedAt: Instant): Boolean {
        return Duration.between(capturedAt, Instant.now()) < playerProperties.statsTtl
    }

}

enum class LifetimeStatsUpdateResult {
    UPDATED,
    SKIPPED,
    FAILED
}