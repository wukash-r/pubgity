package org.traanite.pubgity.user

// First-admin bootstrapping via APP_ADMIN_SUB has been removed.
// Roles are now managed as Keycloak realm roles and synced into AppUser on every login.
// To make a user an admin, assign the ADMIN realm role in the Keycloak admin console.
// See KEYCLOAK_SETUP.md for step-by-step instructions.

// todo remove