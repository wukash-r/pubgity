package org.traanite.pubgity.user

/**
 * Strategy for extracting [AppRole]s from raw OIDC token claims.
 *
 * The default implementation ([ClaimPathRolesResolver]) navigates a configurable
 * dot-separated claim path. Override this bean to handle any provider-specific
 * claim structure that cannot be expressed as a simple path.
 *
 * If the implementation returns an empty set, the caller should fall back to
 * [AppRole.USER].
 */
fun interface OidcRolesResolver {
    fun resolve(claims: Map<String, Any>): Set<AppRole>
}

