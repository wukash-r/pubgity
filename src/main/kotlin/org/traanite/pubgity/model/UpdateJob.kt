package org.traanite.pubgity.model

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "update_jobs")
data class UpdateJob(
    @Id val id: ObjectId? = null,
    val accountId: String? = null,
    val playerName: String,
    val status: JobStatus = JobStatus.QUEUED,
    val createdAt: Instant = Instant.now(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val errorMessage: String? = null,
    val progress: String? = null
)

enum class JobStatus {
    QUEUED, RUNNING, COMPLETED, FAILED
}

