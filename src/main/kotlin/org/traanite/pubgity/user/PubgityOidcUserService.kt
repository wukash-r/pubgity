package org.traanite.pubgity.user

import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

/**
 * Bridges OIDC identity with the local [AppUser] aggregate.
 *
 * On every login:
 *  - Realm roles are read from the Keycloak `realm_access.roles` claim in the ID token
 *    (injected via a protocol mapper configured on the Keycloak client).
 *  - All recognised [AppRole] values are resolved and synced into the local [AppUser] record.
 *  - If no recognised role is present the user defaults to [AppRole.USER].
 */
@Service
class PubgityOidcUserService(
    private val appUserService: AppUserService
) : OidcUserService() {

    companion object {
        private val logger = LoggerFactory.getLogger(PubgityOidcUserService::class.java)
    }

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val oidcUser = super.loadUser(userRequest)

        val sub = oidcUser.subject
        val defaultUsername = oidcUser.preferredUsername
            ?: oidcUser.fullName
            ?: oidcUser.email
            ?: sub
        val email = oidcUser.email ?: ""

        val roles = resolveRoles(oidcUser)
        val appUser = appUserService.upsertOnLogin(sub, defaultUsername, email, roles)

        val authorities = appUser.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }
        logger.debug("Loaded OIDC user sub={}, keycloak roles={}, granted authorities={}", sub, roles, authorities.map { it.authority })

        // Use preferred_username as the principal name when available, otherwise fall back to sub.
        val nameAttributeKey = if (oidcUser.preferredUsername != null) "preferred_username" else "sub"
        return DefaultOidcUser(authorities, oidcUser.idToken, oidcUser.userInfo, nameAttributeKey)
    }

    /**
     * Reads `realm_access.roles` from the Keycloak ID token (added by the
     * "realm roles" protocol mapper on the Keycloak client) and maps each entry
     * to a known [AppRole].
     *
     * Unrecognised role names are silently ignored.
     * Defaults to [AppRole.USER] when no match is found.
     */
    @Suppress("UNCHECKED_CAST")
    private fun resolveRoles(oidcUser: OidcUser): Set<AppRole> {
        val realmAccess = oidcUser.claims["realm_access"] as? Map<*, *>
        val realmRoles = (realmAccess?.get("roles") as? List<*>)
            ?.filterIsInstance<String>()
            ?: emptyList()

        val known = AppRole.entries.associateBy { it.name }
        val resolved = realmRoles.mapNotNull { known[it] }.toSet()
        return resolved.ifEmpty { setOf(AppRole.USER) }
    }
}
