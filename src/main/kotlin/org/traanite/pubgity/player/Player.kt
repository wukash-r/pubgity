package org.traanite.pubgity.player

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "players")
data class Player(
    @Id
    val id: ObjectId? = null,
    @Indexed(unique = true)
    val playerName: String,
    @Indexed(unique = true, sparse = true)
    val accountId: String? = null,
    val matches: Set<PlayerMatchRef> = emptySet()
)

data class PlayerMatchRef(
    val matchId: String
)

