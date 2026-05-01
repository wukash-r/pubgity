package org.traanite.pubgity.view

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class SecurityModelAdvice {

    @ModelAttribute("isAuthenticated")
    fun isAuthenticated(auth: Authentication?): Boolean =
        auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken

    @ModelAttribute("isAdmin")
    fun isAdmin(auth: Authentication?): Boolean =
        auth?.authorities?.any { it.authority == "ROLE_ADMIN" } ?: false

    @ModelAttribute("isModerator")
    fun isModerator(auth: Authentication?): Boolean =
        auth?.authorities?.any { it.authority == "ROLE_MODERATOR" } ?: false

    @ModelAttribute("username")
    fun username(auth: Authentication?): String? {
        if (auth == null || !auth.isAuthenticated || auth is AnonymousAuthenticationToken) return null
        return when (val principal = auth.principal) {
            is OidcUser -> principal.preferredUsername ?: principal.email ?: auth.name
            else -> auth.name
        }
    }
}
