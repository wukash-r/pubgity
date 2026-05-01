package org.traanite.pubgity.user

import org.bson.types.ObjectId
import org.slf4j.LoggerFactory
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

@Service
class AppUserService(private val repository: AppUserRepository) {

    companion object {
        private val logger = LoggerFactory.getLogger(AppUserService::class.java)
    }

    /**
     * Called on every OIDC login. Creates the user record on first login;
     * keeps the email current on subsequent logins.
     * Username is intentionally not overwritten from the OIDC claim after initial creation,
     * so that users can customise it in their profile.
     */
    fun upsertOnLogin(sub: String, defaultUsername: String, email: String): AppUser {
        val existing = repository.findBySub(sub)
        return if (existing == null) {
            val user = AppUser(sub = sub, username = defaultUsername, email = email)
            repository.save(user).also { logger.info("Created new AppUser for sub={}", sub) }
        } else {
            repository.save(existing.withEmail(email))
                .also { logger.debug("Updated email on login for sub={}", sub) }
        }
    }

    /**
     * Idempotent first-admin bootstrap.
     * Promotes [sub] to [AppRole.ADMIN] only when no ADMIN exists yet.
     * Creates a stub AppUser if the sub has not logged in yet.
     */
    fun promoteFirstAdmin(sub: String) {
        if (repository.existsByRole(AppRole.ADMIN)) {
            logger.debug("Admin already exists – skipping first-admin promotion for sub={}", sub)
            return
        }
        val existing = repository.findBySub(sub)
        val user = existing ?: AppUser(sub = sub, username = "admin", email = "")
        repository.save(user.withRole(AppRole.ADMIN))
        logger.info("Promoted first admin: sub={}", sub)
    }

    fun assignRole(id: ObjectId, role: AppRole): AppUser {
        val user = requireUser(id)
        return repository.save(user.withRole(role))
            .also { logger.info("Assigned role {} to user {}", role, id) }
    }

    fun updateModeratorConstraints(id: ObjectId, allowedPlayerIds: Set<ObjectId>, maxQueueSize: Int): AppUser {
        val user = requireUser(id)
        return repository.save(user.withModeratorConstraints(ModeratorConstraints(allowedPlayerIds, maxQueueSize)))
            .also { logger.info("Updated moderator constraints for user {}: players={}, maxQueue={}", id, allowedPlayerIds.size, maxQueueSize) }
    }

    fun linkPlayer(id: ObjectId, playerId: ObjectId): AppUser {
        val user = requireUser(id)
        return repository.save(user.withLinkedPlayer(playerId))
    }

    fun unlinkPlayer(id: ObjectId): AppUser {
        val user = requireUser(id)
        return repository.save(user.copy(linkedPlayerId = null))
    }

    fun updateUsername(id: ObjectId, username: String): AppUser {
        require(username.isNotBlank()) { "Username must not be blank" }
        val user = requireUser(id)
        return repository.save(user.withUsername(username.trim()))
    }

    fun findBySub(sub: String): AppUser? = repository.findBySub(sub)

    fun findByAuth(auth: Authentication): AppUser? {
        val sub = (auth.principal as? OidcUser)?.subject ?: return null
        return findBySub(sub)
    }

    fun findAll(): List<AppUser> = repository.findAll()

    fun findById(id: ObjectId): AppUser? = repository.findById(id).orElse(null)

    private fun requireUser(id: ObjectId): AppUser =
        repository.findById(id).orElseThrow { IllegalArgumentException("AppUser $id not found") }
}


