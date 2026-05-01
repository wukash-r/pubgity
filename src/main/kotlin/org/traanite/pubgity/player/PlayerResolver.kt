package org.traanite.pubgity.player

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.traanite.pubgity.pubgapi.PubgApiClient

@Service
class PlayerResolver(
    private val pubgApiClient: PubgApiClient, private val playerRepository: PlayerRepository
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

        val resolvedPlayer = resolveOrCreatePlayer(accountId, name, playerName)
        return ResolvedPlayer(resolvedPlayer.accountId!!, resolvedPlayer.playerName, matchIds)
    }

    fun resolveOrCreatePlayer(accountId: String, currentName: String, originalName: String): Player {
        val player = playerRepository.findByAccountId(accountId)
            ?: playerRepository.findByPlayerName(originalName)
            ?: Player(playerName = currentName)

        val updatedPlayer = player.updateAccountData(accountId, currentName)
        playerRepository.save(updatedPlayer)
        logger.info("Resolved or created player: accountId={}, name='{}'", updatedPlayer.accountId, updatedPlayer.playerName)
        return updatedPlayer
    }
}

data class ResolvedPlayer(
    val accountId: String, val playerName: String, val matchIds: List<String>
)
