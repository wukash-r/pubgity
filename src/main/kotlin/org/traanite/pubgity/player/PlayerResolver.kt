package org.traanite.pubgity.player

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.pubgapi.PubgApiClient

@Service
class PlayerResolver(
    private val pubgApiClient: PubgApiClient,
    private val playerService: PlayerService
) {

    companion object {
        private val logger = LoggerFactory.getLogger(PlayerResolver::class.java)
    }

    fun resolve(accountId: String?, playerName: String): ResolvedPlayer {
            val playerData = if (accountId != null) {
                pubgApiClient.getPlayerByAccountId(accountId)
            } else {
                pubgApiClient.getPlayerByName(playerName)
            }

            val accountId = playerData.id
            val matchIds = playerData.relationships?.matches?.data?.map { it.id } ?: emptyList()
            val name = playerData.attributes.name

            logger.info("Resolved '{}' -> accountId={}, {} matches from API", name, accountId, matchIds.size)

            playerService.resolveOrCreatePlayer(accountId, name, playerName)

            return ResolvedPlayer(accountId, name, matchIds)
        }
}

data class ResolvedPlayer(
    val accountId: String, val playerName: String, val matchIds: List<String>
)
