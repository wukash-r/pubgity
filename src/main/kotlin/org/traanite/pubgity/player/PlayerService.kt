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

    fun getSeasonStatsByPlayer(accountIds: Set<String>): Map<String, Map<String, SeasonStats>> {
        return playerRepository.findByAccountIdIn(accountIds)
            .associate { player ->
                val stats = player.seasonStats.stats
                player.accountId!! to stats
            }
    }

    fun getLatestLifetimeStatsByPlayer(accountIds: Set<String>): Map<String, PlayerLifetimeStatsSnapshot?> {
        return playerRepository.findByAccountIdIn(accountIds)
            .associate { player ->
                val stats = player.lifetimeStats.latestSnapshot
                player.accountId!! to stats
            }
    }

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

    fun appendMatchRefs(accountId: String, matchIds: List<String>) {
        val player = playerRepository.findByAccountId(accountId) ?: return

        player.addMatches(matchIds).let { updatedPlayer ->
            playerRepository.save(updatedPlayer)
            logger.info("Appended match refs: {}", updatedPlayer)
        }
    }
}