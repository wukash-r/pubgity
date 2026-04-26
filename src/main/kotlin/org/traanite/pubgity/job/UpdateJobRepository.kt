package org.traanite.pubgity.job

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface UpdateJobRepository : MongoRepository<UpdateJob, ObjectId> {
    fun findFirstByJobTypeAndStatusOrderByCreatedAtAsc(jobType: JobType, status: JobStatus): UpdateJob?
    fun findAllByOrderByCreatedAtDesc(): List<UpdateJob>
    fun existsByJobTypeAndStatus(jobType: JobType, status: JobStatus): Boolean
}

