package org.traanite.pubgity.player

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query

interface PlayerRepository : MongoRepository<Player, ObjectId> {
    fun findByAccountId(accountId: String): Player?
    fun findByPlayerName(playerName: String): Player?

    @Query("{ 'playerName': { '\$regex': ?0, '\$options': 'i' } }")
    fun searchByPlayerName(pattern: String): List<Player>

    fun findByAccountIdIn(accountIds: Collection<String>): List<Player>
}

