package org.traanite.pubgity.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "pubg.api")
data class PubgApiProperties(
    val key: String,
    val baseUrl: String = "https://api.pubg.com/shards/steam",
    val endpoints: EndpointProperties = EndpointProperties(),
    val rateLimit: RateLimitProperties = RateLimitProperties(),
    val retry: RetryProperties = RetryProperties(),
    val matchCache: MatchCacheProperties = MatchCacheProperties()
) {
    data class RateLimitProperties(
        val limitForPeriod: Int = 10,
        val limitRefreshPeriod: Duration = Duration.ofSeconds(60),
        val timeoutDuration: Duration = Duration.ofMinutes(2)
    )
    data class EndpointProperties(
        val players: String = "/players",
        val matches: String = "/matches/{matchId}",
        val lifetimeStats: String = "/players/{accountId}/seasons/lifetime"
    )

    data class RetryProperties(
        val maxAttempts: Int = 3,
        val backoff: Duration = Duration.ofSeconds(30)
    )

    data class MatchCacheProperties(
        val maxSize: Long = 500,
        val ttl: Duration = Duration.ofHours(1)
    )
}

@ConfigurationProperties(prefix = "pubg.cache")
data class PubgCacheProperties(
    val playerStatsTtl: Duration = Duration.ofDays(7)
)
