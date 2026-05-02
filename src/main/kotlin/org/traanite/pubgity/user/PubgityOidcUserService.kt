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
 *  - Roles are extracted from the ID-token claims via the configured [OidcRolesResolver].
 *  - All recognised [AppRole] values are resolved and synced into the local [AppUser] record.
 *  - If no recognised role is present the user defaults to [AppRole.USER].
 */
@Service
class PubgityOidcUserService(
    private val appUserService: AppUserService,
    private val rolesResolver: OidcRolesResolver
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

        val roles = rolesResolver.resolve(oidcUser.claims)
        val appUser = appUserService.upsertOnLogin(sub, defaultUsername, email, roles)

        val authorities = appUser.roles.map { SimpleGrantedAuthority("ROLE_${it.name}") }
        logger.debug("Loaded OIDC user sub={}, oidc roles={}, granted authorities={}", sub, roles, authorities.map { it.authority })

        // Use preferred_username as the principal name when available, otherwise fall back to sub.
        val nameAttributeKey = if (oidcUser.preferredUsername != null) "preferred_username" else "sub"
        return DefaultOidcUser(authorities, oidcUser.idToken, oidcUser.userInfo, nameAttributeKey)
    }
}
