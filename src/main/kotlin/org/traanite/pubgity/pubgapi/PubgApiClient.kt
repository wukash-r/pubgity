package org.traanite.pubgity.pubgapi

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Service
class PubgApiClient(
    apiProperties: PubgApiProperties,
    restClientBuilder: RestClient.Builder
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val endpoints = apiProperties.endpoints

    private val restClient = restClientBuilder
        .baseUrl(apiProperties.baseUrl)
        .defaultHeader("Authorization", "Bearer ${apiProperties.key}")
        .defaultHeader("Accept", "application/vnd.api+json")
        .build()

    private val rateLimiter = RateLimiter.of(
        "pubg-api",
        RateLimiterConfig.custom()
            .limitForPeriod(apiProperties.rateLimit.limitForPeriod)
            .limitRefreshPeriod(apiProperties.rateLimit.limitRefreshPeriod)
            .timeoutDuration(apiProperties.rateLimit.timeoutDuration)
            .build()
    )

    private val retry = Retry.of(
        "pubg-api",
        RetryConfig.custom<Any>()
            .maxAttempts(apiProperties.retry.maxAttempts)
            .waitDuration(apiProperties.retry.backoff)
            .retryOnException { it is TooManyRequestsException }
            .build()
    )

    private val matchCache = Caffeine.newBuilder()
        .maximumSize(apiProperties.matchCache.maxSize)
        .expireAfterWrite(apiProperties.matchCache.ttl)
        .build<String, MatchResponse>()

    fun getPlayerByName(name: String): PlayerData {
        logger.info("Fetching player by name: {}", name)
        return rateLimitedCall {
            restClient.get()
                .uri { it.path(endpoints.players).queryParam("filter[playerNames]", name).build() }
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    if (response.statusCode.value() == 429) throw TooManyRequestsException()
                    throw PubgApiException("Player search failed: ${response.statusCode}")
                }
                .body<PlayersResponse>()!!
                .data.first()
        }
    }

    fun getPlayerByAccountId(accountId: String): PlayerData {
        logger.info("Fetching player by accountId: {}", accountId)
        return rateLimitedCall {
            restClient.get()
                .uri { it.path(endpoints.players).queryParam("filter[playerIds]", accountId).build() }
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    if (response.statusCode.value() == 429) throw TooManyRequestsException()
                    throw PubgApiException("Player lookup failed: ${response.statusCode}")
                }
                .body<PlayersResponse>()!!
                .data.first()
        }
    }

    fun getMatch(matchId: String): MatchResponse {
        val cached = matchCache.getIfPresent(matchId)
        if (cached != null) {
            logger.debug("Match cache hit: {}", matchId)
            return cached
        }
        return matchCache.get(matchId) { id ->
            logger.info("Fetching match from API: {}", id)
            restClient.get()
                .uri(endpoints.matches, id)
                .retrieve()
                .body<MatchResponse>()!!
        }
    }

    fun getLifetimeStats(accountId: String): LifetimeStatsResponse {
        logger.info("Fetching lifetime stats for: {}", accountId)
        return rateLimitedCall {
            restClient.get()
                .uri(endpoints.lifetimeStats, accountId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError) { _, response ->
                    if (response.statusCode.value() == 429) throw TooManyRequestsException()
                    throw PubgApiException("Lifetime stats failed: ${response.statusCode}")
                }
                .body<LifetimeStatsResponse>()!!
        }
    }

    private fun <T> rateLimitedCall(block: () -> T): T {
        logger.debug("Acquiring rate limiter permit (available: {})", rateLimiter.metrics.availablePermissions)
        val rateLimited = RateLimiter.decorateCallable(rateLimiter) { block() }
        val retried = Retry.decorateCallable(retry, rateLimited)
        return retried.call()
    }
}

