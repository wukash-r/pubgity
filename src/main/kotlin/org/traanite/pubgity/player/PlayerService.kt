package org.traanite.pubgity.player

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PlayerService(
    private val playerRepository: PlayerRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PlayerService::class.java)
    }

    fun findAll(): List<Player> = playerRepository.findAll()

    fun searchByPlayerName(pattern: String): List<Player> = playerRepository.searchByPlayerName(pattern)

    fun findByAccountId(accountId: String): Player? = playerRepository.findByAccountId(accountId)

    fun findByPlayerName(playerName: String): Player? = playerRepository.findByPlayerName(playerName)

    fun findById(id: ObjectId): Player? = playerRepository.findById(id).orElse(null)

    fun addPlayer(playerName: String): Player? {
        val trimmed = playerName.trim()
        if (trimmed.isBlank() || playerRepository.findByPlayerName(trimmed) != null) {
            logger.warn("Player '{}' already exists or name is blank, skipping add", trimmed)
            return null
        }
        val player = playerRepository.save(Player(playerName = trimmed))
        logger.info("Added player to watchlist: {}", trimmed)
        return player
    }

    fun removePlayer(id: ObjectId) {
        logger.info("Removing player with id: {}", id)
        playerRepository.deleteById(id)
    }

    fun resolveOrCreatePlayer(accountId: String, currentName: String, originalName: String): Player {
        val existing = playerRepository.findByAccountId(accountId)
            ?: playerRepository.findByPlayerName(originalName)
            ?: Player(playerName = currentName)

        return playerRepository.save(
            existing.copy(
                accountId = accountId,
                playerName = currentName
            )
        )
    }

    fun appendMatchRefs(accountId: String, matchIds: List<String>) {
        val player = playerRepository.findByAccountId(accountId) ?: return
        val existingIds = player.matches.map { it.matchId }.toSet()
        val newRefs = matchIds.filter { it !in existingIds }.map { PlayerMatchRef(it) }
        if (newRefs.isNotEmpty()) {
            playerRepository.save(player.copy(matches = player.matches + newRefs))
            logger.info("Appended {} match refs to player '{}'", newRefs.size, player.playerName)
        }
    }


    fun findByAccountIdIn(accountIds: Collection<String>): List<Player> {
        return playerRepository.findByAccountIdIn(accountIds)
    }
}

