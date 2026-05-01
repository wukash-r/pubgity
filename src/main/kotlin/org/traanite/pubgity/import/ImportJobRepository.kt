package org.traanite.pubgity.import

import org.bson.types.ObjectId
import org.springframework.data.mongodb.repository.MongoRepository

interface ImportJobRepository : MongoRepository<ImportJob, ObjectId> {
    fun findFirstByJobTypeAndStatusOrderByCreatedAtAsc(jobType: JobType, status: JobStatus): ImportJob?
    fun findAllByOrderByCreatedAtDesc(): List<ImportJob>
    fun existsByJobTypeAndStatus(jobType: JobType, status: JobStatus): Boolean
    fun countByAccountIdInAndStatusIn(accountIds: Collection<String>, statuses: Collection<JobStatus>): Long
    fun findAllByAccountIdInOrderByCreatedAtDesc(accountIds: Collection<String>): List<ImportJob>
}
