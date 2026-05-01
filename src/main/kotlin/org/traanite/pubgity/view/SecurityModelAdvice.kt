package org.traanite.pubgity.view

import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication
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

    @ModelAttribute("username")
    fun username(auth: Authentication?): String? =
        if (auth != null && auth.isAuthenticated && auth !is AnonymousAuthenticationToken) auth.name else null
}

