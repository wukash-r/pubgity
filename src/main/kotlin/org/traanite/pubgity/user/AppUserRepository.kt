package org.traanite.pubgity.user

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface AppUserRepository : MongoRepository<AppUser, ObjectId> {
    fun findBySub(sub: String): AppUser?
    fun existsByRole(role: AppRole): Boolean
}

