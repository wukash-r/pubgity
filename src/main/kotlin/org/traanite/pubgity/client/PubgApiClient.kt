package org.traanite.pubgity.client

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.traanite.pubgity.config.PubgApiProperties
import java.time.Duration

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
                .body(PlayersResponse::class.java)!!
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
                .body(PlayersResponse::class.java)!!
                .data.first()
        }
    }

    fun getMatch(matchId: String): MatchResponse {
        return matchCache.get(matchId) { id ->
            logger.debug("Fetching match: {}", id)
            restClient.get()
                .uri(endpoints.matches, id)
                .retrieve()
                .body(MatchResponse::class.java)!!
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
                .body(LifetimeStatsResponse::class.java)!!
        }
    }

    private fun <T> rateLimitedCall(block: () -> T): T {
        val rateLimited = RateLimiter.decorateCallable(rateLimiter) { block() }
        val retried = Retry.decorateCallable(retry, rateLimited)
        return retried.call()
    }
}
