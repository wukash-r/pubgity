package org.traanite.pubgity.pubgapi

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "pubg.api")
data class PubgApiProperties(
    val key: String,
    val baseUrl: String = "https://api.pubg.com/shards/steam",
    val endpoints: EndpointProperties = EndpointProperties(),
    val rateLimit: RateLimitProperties = RateLimitProperties(),
    val retry: RetryProperties = RetryProperties(),
    val matchCache: MatchCacheProperties = MatchCacheProperties(),
    val seasonsCache: SeasonsCache = SeasonsCache()
) {
    data class RateLimitProperties(
        val limitForPeriod: Int = 10,
        val limitRefreshPeriod: Duration = Duration.ofSeconds(60),
        val timeoutDuration: Duration = Duration.ofMinutes(2)
    )

    data class EndpointProperties(
        val players: String = "/players",
        val matches: String = "/matches/{matchId}",
        val lifetimeStats: String = "/players/{accountId}/seasons/lifetime",
        val seasons: String = "/seasons",
        val seasonStats: String = "/players/{accountId}/seasons/{seasonId}"
    )

    data class RetryProperties(
        val maxAttempts: Int = 3,
        val backoff: Duration = Duration.ofSeconds(30)
    )

    data class MatchCacheProperties(
        val maxSize: Long = 500,
        val ttl: Duration = Duration.ofHours(1)
    )

    data class SeasonsCache(
        val ttl: Duration = Duration.ofDays(1),
        val maxSize: Long = 50,
    )
}

