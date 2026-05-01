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
 * On every login the local user record is upserted, and the Spring Security authority
 * is derived from the locally-stored [AppRole] — not from any provider-specific claims or groups.
 * This keeps the authorisation model provider-agnostic.
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

        val appUser = appUserService.upsertOnLogin(sub, defaultUsername, email)

        val authority = SimpleGrantedAuthority("ROLE_${appUser.role.name}")
        logger.debug("Loaded OIDC user sub={}, granted authority={}", sub, authority.authority)

        // Use preferred_username as the principal name when available, otherwise fall back to sub.
        // Controllers that need the stable sub should use OidcUser.subject directly.
        val nameAttributeKey = if (oidcUser.preferredUsername != null) "preferred_username" else "sub"
        return DefaultOidcUser(listOf(authority), oidcUser.idToken, oidcUser.userInfo, nameAttributeKey)
    }
}

