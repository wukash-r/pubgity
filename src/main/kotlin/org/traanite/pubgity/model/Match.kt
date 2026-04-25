package org.traanite.pubgity.model

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

@Document(collection = "matches")
data class Match(
    @Id val id: ObjectId? = null,
    @Indexed(unique = true) val matchId: String,
    val createdAt: Instant,
    val gameMode: String,
    val mapName: String,
    val duration: Int,
    val botCount: Int = 0,
    val rosters: List<MatchRoster> = emptyList()
)

data class MatchRoster(
    val rosterId: String,
    val rank: Int,
    val won: Boolean,
    val participants: List<MatchParticipantSnapshot> = emptyList()
)

data class MatchParticipantSnapshot(
    val accountId: String,
    val playerName: String,
    val lifetimeStats: LifetimeStats? = null
)

