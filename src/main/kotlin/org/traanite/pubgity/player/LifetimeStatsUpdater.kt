package org.traanite.pubgity.player

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.pubgapi.PubgApiClient
import org.traanite.pubgity.pubgapi.toLifetimeStats
import java.time.Duration
import java.time.Instant

@Service
class LifetimeStatsUpdater(
    private val playerService: PlayerService,
    private val pubgApiClient: PubgApiClient,
    private val playerProperties: PlayerProperties
) {

    companion object {
        private val logger = LoggerFactory.getLogger(javaClass)
    }

    fun updateLifetimeStats(accountId: String, playerName: String): LifetimeStatsUpdateResult {
        val latestSnapshot = playerService.getLatestLifetimeStats(accountId)

        if (latestSnapshot != null && isWithinCacheThreshold(latestSnapshot.capturedAt)) {
            return LifetimeStatsUpdateResult.SKIPPED
        }

        try {
            val stats = pubgApiClient.getLifetimeStats(accountId).data.attributes.toLifetimeStats()
            playerService.saveLifetimeStatsSnapshot(accountId, stats)

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