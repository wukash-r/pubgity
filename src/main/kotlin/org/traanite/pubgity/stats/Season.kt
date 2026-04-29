package org.traanite.pubgity.stats

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

@Document(collection = "seasons")
data class Season(
    @Id
    val id: ObjectId? = null,
    @Indexed(unique = true)
    val seasonId: String,
    val isCurrentSeason: Boolean = false
)