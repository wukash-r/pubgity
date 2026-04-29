package org.traanite.pubgity.stats

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface PlayerLifetimeStatsRepository : MongoRepository<PlayerLifetimeStatsSnapshot, ObjectId> {
    fun findFirstByAccountIdOrderByCapturedAtDesc(accountId: String): PlayerLifetimeStatsSnapshot?
    fun findByAccountIdOrderByCapturedAtDesc(accountId: String): List<PlayerLifetimeStatsSnapshot>
}

