package org.traanite.pubgity.user

import org.bson.types.ObjectId

/**
 * Constraints that apply when a user has [AppRole.MODERATOR] role.
 * Stored as an embedded document within [AppUser].
 */
data class ModeratorConstraints(
    /** Player ObjectIds this moderator is allowed to queue jobs for. */
    val allowedPlayerIds: Set<ObjectId> = emptySet(),
    /** Maximum number of QUEUED or RUNNING jobs across all allowed players at any point in time. */
    val maxQueueSize: Int = 5
)

