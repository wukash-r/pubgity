package org.traanite.pubgity.user

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Default [OidcRolesResolver] that navigates a dot-separated claim path to find
 * a list of role strings, then maps them to known [AppRole] values.
 *
 * The path is configured via `pubgity.oidc.roles-claim` (e.g. `realm_access.roles`
 * for Keycloak, or simply `roles` for providers that expose a flat top-level claim).
 *
 * Falls back to [AppRole.USER] when the path cannot be resolved or no recognised
 * role names are found.
 */
@Component
//@ConditionalOnMissingBean(OidcRolesResolver::class)
class ClaimPathRolesResolver(
    @Value("\${pubgity.oidc.roles-claim}") private val rolesClaimPath: String
) : OidcRolesResolver {

    override fun resolve(claims: Map<String, Any>): Set<AppRole> {
        val segments = rolesClaimPath.split(".")
        val roleStrings = navigate(claims, segments)
        val known = AppRole.entries.associateBy { it.name }
        val resolved = roleStrings.mapNotNull { known[it] }.toSet()
        return resolved.ifEmpty { setOf(AppRole.USER) }
    }

    /**
     * Recursively walks [segments] into [node].
     * Returns a list of strings when the final segment resolves to a list,
     * an empty list if any segment is missing or the wrong type.
     */
    @Suppress("UNCHECKED_CAST")
    private fun navigate(node: Any?, segments: List<String>): List<String> {
        if (node == null || segments.isEmpty()) return emptyList()
        val head = segments.first()
        val rest = segments.drop(1)
        val child = (node as? Map<*, *>)?.get(head) ?: return emptyList()
        return if (rest.isEmpty()) {
            (child as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        } else {
            navigate(child, rest)
        }
    }
}

