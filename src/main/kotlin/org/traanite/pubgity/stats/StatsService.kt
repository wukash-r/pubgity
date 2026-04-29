package org.traanite.pubgity.stats

import org.springframework.stereotype.Service
import java.time.Instant

@Service
class StatsService(
    private val lifetimeStatsRepository: PlayerLifetimeStatsRepository
) {

    fun getLatestLifetimeStats(accountId: String): PlayerLifetimeStatsSnapshot? {
        return lifetimeStatsRepository.findFirstByAccountIdOrderByCapturedAtDesc(accountId)
    }

    fun saveLifetimeStatsSnapshot(accountId: String, stats: LifetimeStats): PlayerLifetimeStatsSnapshot {
        val snapshot = PlayerLifetimeStatsSnapshot(
            accountId = accountId,
            capturedAt = Instant.now(),
            stats = stats
        )
        return lifetimeStatsRepository.save(snapshot)
    }

}