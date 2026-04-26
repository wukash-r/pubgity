package org.traanite.pubgity.match

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface MatchRepository : MongoRepository<Match, ObjectId> {
    fun findByMatchId(matchId: String): Match?
    fun findByMatchIdIn(matchIds: Collection<String>): List<Match>
}

