package org.traanite.pubgity.repository

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.traanite.pubgity.model.Match

interface MatchRepository : MongoRepository<Match, ObjectId> {
    fun findByMatchId(matchId: String): Match?
    fun findByMatchIdIn(matchIds: Collection<String>): List<Match>
}

