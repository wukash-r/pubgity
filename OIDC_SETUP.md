# Keycloak Setup Guide

Pubgity delegates authentication to Keycloak via OpenID Connect.
Roles (`ADMIN`, `MODERATOR`, `USER`) are managed as **Keycloak realm roles** and synced
into Pubgity's local user store on every login.

> **For local development** see [LOCAL_DEV.md](LOCAL_DEV.md) — a pre-configured realm JSON
> with 5 seed users is imported automatically.

---

## How roles work

| Role        | Access                                                              |
|-------------|---------------------------------------------------------------------|
| `ADMIN`     | Full access — admin panel, all jobs, all players                    |
| `MODERATOR` | Jobs page scoped to assigned players, subject to queue size limit   |
| `USER`      | Public pages + personal home/profile                                |

- Roles are assigned as **Keycloak realm roles** on the user's account.
- Pubgity reads `realm_access.roles` from the Keycloak **ID token** (injected by a
  protocol mapper — see Step 3 below) on every login and syncs the local `AppUser.role`.
- Role changes take effect on the user's **next login**.
- Moderator constraints (allowed players, max queue size) are configured in Pubgity's
  Admin panel at `/admin/users` — they are not managed in Keycloak.

---

## Production Setup

### Step 1 — Create a Realm

1. Log in to the Keycloak admin console
2. Click the realm selector (top-left) → **Create Realm**
3. Set **Realm name**: `pubgity` (or any name — update `OIDC_ISSUER_URI` accordingly)
4. **Create**

### Step 2 — Create realm roles

1. Go to **Realm roles → Create role**
2. Create three roles (names must match exactly):
   - `ADMIN`
   - `MODERATOR`
   - `USER`

### Step 3 — Create a Client

1. Go to **Clients → Create client**
2. Fill in:

   | Field           | Value                                              |
   |-----------------|----------------------------------------------------|
   | Client type     | `OpenID Connect`                                   |
   | Client ID       | `pubgity`                                          |

3. Click **Next**
4. Enable **Client authentication** (confidential client), disable **Direct access grants**
5. Click **Next**
6. Set **Valid redirect URIs**: `https://<your-host>/login/oauth2/code/keycloak`
7. Set **Valid post-logout redirect URIs**: `https://<your-host>/`
8. Set **Web origins**: `https://<your-host>`
9. **Save**
10. Go to the **Credentials** tab — copy the **Client secret**

### Step 4 — Add the realm-roles protocol mapper

This mapper injects realm roles into the ID token so Pubgity can read them.

1. In your client, go to **Client scopes** tab → click `pubgity-dedicated`
2. Click **Add mapper → By configuration → User Realm Role**
3. Fill in:

   | Field                       | Value                 |
   |-----------------------------|-----------------------|
   | Name                        | `realm roles`         |
   | Token Claim Name            | `realm_access.roles`  |
   | Add to ID token             | `On`                  |
   | Add to access token         | `On`                  |
   | Add to userinfo             | `On`                  |
   | Multivalued                 | `On`                  |

4. **Save**

### Step 5 — Assign roles to users

1. Go to **Users** → click a user → **Role mapping** tab
2. Click **Assign role** → filter by **realm roles**
3. Select `ADMIN`, `MODERATOR`, or `USER` as appropriate

### Step 6 — Set environment variables

| Variable            | Description                                              | Example                                              |
|---------------------|----------------------------------------------------------|------------------------------------------------------|
| `OIDC_ISSUER_URI`   | Keycloak realm URL                                       | `https://keycloak.example.com/realms/pubgity`        |
| `OIDC_CLIENT_ID`    | Client ID from Step 3                                    | `pubgity`                                            |
| `OIDC_CLIENT_SECRET`| Client secret from Step 3                               | `supersecret`                                        |

**Docker Compose example:**

```yaml
services:
  pubgity:
    environment:
      OIDC_ISSUER_URI: "https://keycloak.example.com/realms/pubgity"
      OIDC_CLIENT_ID: "pubgity"
      OIDC_CLIENT_SECRET: "supersecret"
```

---

## OIDC Requirements

Pubgity requires the following from the provider:

| Requirement               | Notes                                                              |
|---------------------------|--------------------------------------------------------------------|
| `sub` claim               | Stable unique user identifier — never changes for a given user     |
| `email` claim             | Synced to Pubgity on every login                                   |
| `preferred_username`      | Display name (falls back to `name`, `email`, then `sub`)          |
| `realm_access.roles`      | List of realm role names — must be present in the ID token         |
| `end_session_endpoint`    | Required for RP-initiated logout (Keycloak provides this natively) |

### Redirect URIs to register

| URI                                                | Purpose                   |
|----------------------------------------------------|---------------------------|
| `https://<your-host>/login/oauth2/code/keycloak`  | Authorization callback    |
| `https://<your-host>/`                             | Post-logout redirect       |
