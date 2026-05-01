package org.traanite.pubgity.import

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "import_jobs")
data class ImportJob(
    @Id val id: ObjectId? = null,
    val accountId: String? = null,
    val playerName: String,
    val matchCount: Int? = null,
    val matchId: String? = null,

    val jobType: JobType,
    val status: JobStatus = JobStatus.QUEUED,
    val createdAt: Instant = Instant.now(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val errorMessage: String? = null,
    val progress: String? = null,
    val retried: Boolean = false
)

enum class JobStatus {
    QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, SKIPPED
}

enum class JobType {
    FETCH_PLAYER_MATCHES, FETCH_MATCH_STATS
}

