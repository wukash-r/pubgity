package org.traanite.pubgity.repository

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository
import org.traanite.pubgity.model.JobStatus
import org.traanite.pubgity.model.UpdateJob

interface UpdateJobRepository : MongoRepository<UpdateJob, ObjectId> {
    fun findFirstByStatusOrderByCreatedAtAsc(status: JobStatus): UpdateJob?
    fun findAllByOrderByCreatedAtDesc(): List<UpdateJob>
}

