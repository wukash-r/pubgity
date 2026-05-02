package org.traanite.pubgity.user

import org.bson.types.ObjectId
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document

/**
 * Aggregate root for the user context.
 *
 * Represents a Pubgity application user whose identity is managed by Keycloak.
 * The [sub] claim (OIDC subject) is the stable, provider-issued identity key.
 * [roles] are synced from Keycloak realm roles on every login — they are never
 * modified locally.
 */
@Document(collection = "app_users")
data class AppUser(
    @Id val id: ObjectId? = null,

    /** OIDC `sub` claim — uniquely identifies this user across logins. */
    @Indexed(unique = true)
    val sub: String,

    val username: String,
    val email: String,

    /** All Keycloak realm roles assigned to this user, synced on every login. */
    val roles: Set<AppRole> = setOf(AppRole.USER),

    /** Reference to a [org.traanite.pubgity.player.Player] document the user has linked to their account. */
    val linkedPlayerId: ObjectId? = null,

    /** Only meaningful when [roles] contains [AppRole.MODERATOR]. */
    val moderatorConstraints: ModeratorConstraints? = null
) {
    fun hasRole(role: AppRole): Boolean = role in roles

    fun withRoles(newRoles: Set<AppRole>): AppUser = copy(roles = newRoles)

    fun withModeratorConstraints(constraints: ModeratorConstraints): AppUser =
        copy(moderatorConstraints = constraints)

    fun withLinkedPlayer(playerId: ObjectId): AppUser = copy(linkedPlayerId = playerId)

    fun withUsername(newUsername: String): AppUser = copy(username = newUsername)

    fun withEmail(newEmail: String): AppUser = copy(email = newEmail)
}

