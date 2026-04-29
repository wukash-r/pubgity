package org.traanite.pubgity.stats

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface SeasonRepository : MongoRepository<Season, ObjectId> {
}