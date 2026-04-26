package org.traanite.pubgity.job

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface UpdateJobRepository : MongoRepository<UpdateJob, ObjectId> {
    fun findFirstByStatusOrderByCreatedAtAsc(status: JobStatus): UpdateJob?
    fun findAllByOrderByCreatedAtDesc(): List<UpdateJob>
    fun existsByStatus(status: JobStatus): Boolean
}

