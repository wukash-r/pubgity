package org.traanite.pubgity.user

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Aggregate root for the user context.
 *
 * Represents a Pubgity application user whose identity is managed by an external OIDC provider.
 * The [sub] claim (OIDC subject) is the stable, provider-issued identity key.
 * Roles and access constraints are owned and enforced by this application.
 */
@Document(collection = "app_users")
data class AppUser(
    @Id val id: ObjectId? = null,

    /** OIDC `sub` claim — uniquely identifies this user across logins. Provider-agnostic. */
    @Indexed(unique = true)
    val sub: String,

    val username: String,
    val email: String,

    val role: AppRole = AppRole.USER,

    /** Reference to a [org.traanite.pubgity.player.Player] document the user has linked to their account. */
    val linkedPlayerId: ObjectId? = null,

    /** Only meaningful when [role] is [AppRole.MODERATOR]. */
    val moderatorConstraints: ModeratorConstraints? = null
) {
    fun withRole(newRole: AppRole): AppUser = copy(role = newRole)

    fun withModeratorConstraints(constraints: ModeratorConstraints): AppUser =
        copy(moderatorConstraints = constraints)

    fun withLinkedPlayer(playerId: ObjectId): AppUser = copy(linkedPlayerId = playerId)

    fun withUsername(newUsername: String): AppUser = copy(username = newUsername)

    fun withEmail(newEmail: String): AppUser = copy(email = newEmail)
}

