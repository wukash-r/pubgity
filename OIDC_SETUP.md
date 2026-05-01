# OIDC / SSO Setup Guide

Pubgity delegates authentication to an external OpenID Connect (OIDC) provider.
This document is split into two parts:

1. **Generic OIDC requirements** — what any conformant provider must supply.
2. **Authentik step-by-step** — concrete instructions for the default supported provider.

---

## Part 1 — Generic OIDC Requirements

### Required scopes

The provider must grant the following scopes when requested:

| Scope     | Purpose                              |
|-----------|--------------------------------------|
| `openid`  | Core OIDC — enables ID token         |
| `profile` | `preferred_username`, `name` claims  |
| `email`   | `email` claim                        |

### Required claims in the ID token / userinfo endpoint

| Claim                | Required | Notes                                                          |
|----------------------|----------|----------------------------------------------------------------|
| `sub`                | ✅       | Stable, unique user identifier. Never changes for a user.     |
| `email`              | ✅       | Displayed in profile; kept in sync on every login.            |
| `preferred_username` | Recommended | Used as the default display name. Falls back to `name`, then `email`, then `sub`. |

### Redirect URIs to register with your provider

| URI                                              | Purpose          |
|--------------------------------------------------|------------------|
| `https://<your-host>/login/oauth2/code/oidc`     | Authorization callback |
| `https://<your-host>/`                           | Post-logout redirect   |

Replace `<your-host>` with the public hostname of your Pubgity instance (e.g. `pubgity.example.com`).
For local development use `http://localhost:8080`.

### Environment variables

Set these before starting the application:

| Variable            | Description                                                                 | Example                                                          |
|---------------------|-----------------------------------------------------------------------------|------------------------------------------------------------------|
| `OIDC_ISSUER_URI`   | Provider issuer URL (must expose `/.well-known/openid-configuration`)       | `https://authentik.example.com/application/o/pubgity/`          |
| `OIDC_CLIENT_ID`    | OAuth2 client ID issued by the provider                                     | `abc123`                                                        |
| `OIDC_CLIENT_SECRET`| OAuth2 client secret issued by the provider                                 | `supersecret`                                                   |
| `APP_ADMIN_SUB`     | OIDC `sub` of the first user to bootstrap as ADMIN (one-time, idempotent)  | `a1b2c3d4-...` (UUID format for most providers)                 |

`APP_ADMIN_SUB` is only needed once. After the first ADMIN exists the variable is ignored.
See [Finding the `sub`](#finding-the-sub) below.

### Docker Compose / environment file example

```env
OIDC_ISSUER_URI=https://authentik.example.com/application/o/pubgity/
OIDC_CLIENT_ID=abc123def456
OIDC_CLIENT_SECRET=a-very-long-secret
APP_ADMIN_SUB=a1b2c3d4-e5f6-7890-abcd-ef1234567890
```

### Notes on role management

Roles (`ADMIN`, `MODERATOR`, `USER`) are managed **locally in Pubgity's MongoDB**, not via
provider groups or claims. This design means:

- No provider-specific group mapping is needed.
- Roles survive provider migrations.
- Changing a user's role takes effect on their **next login** (new session). Existing sessions
  retain the old role until they re-authenticate.

### OIDC RP-Initiated Logout

Pubgity supports OIDC RP-initiated logout (the user is redirected to the provider's
`end_session_endpoint` after signing out locally). This requires the provider's discovery
document to expose `end_session_endpoint`. Most compliant providers (Authentik, Keycloak,
Okta, Auth0) do this by default.

---

## Part 2 — Authentik Step-by-Step

### Prerequisites

- A running [Authentik](https://goauthentik.io) instance.
- Admin access to the Authentik interface.

---

### 1 — Create an OAuth2/OpenID Connect Provider

1. Log in to the Authentik admin interface → **Applications → Providers**.
2. Click **Create** → choose **OAuth2/OpenID Connect Provider**.
3. Fill in the form:

   | Field                         | Value                                              |
   |-------------------------------|----------------------------------------------------|
   | **Name**                      | `Pubgity`                                         |
   | **Authorization flow**        | `default-authorization-flow` (or your custom one) |
   | **Client type**               | `Confidential`                                    |
   | **Client ID**                 | (auto-generated — copy this for `OIDC_CLIENT_ID`) |
   | **Client Secret**             | (auto-generated — copy this for `OIDC_CLIENT_SECRET`) |
   | **Redirect URIs / Origins**   | `https://<your-host>/login/oauth2/code/oidc`      |
   | **Post logout redirect URIs** | `https://<your-host>/`                            |
   | **Scopes**                    | `openid`, `profile`, `email` (add via the Scope Mapping section) |
   | **Subject mode**              | `Based on the User's UUID` (recommended — gives a stable `sub`) |

4. Click **Finish / Save**.

---

### 2 — Create the Application

1. Go to **Applications → Applications** → click **Create**.
2. Fill in:

   | Field        | Value               |
   |--------------|---------------------|
   | **Name**     | `Pubgity`          |
   | **Slug**     | `pubgity`          |
   | **Provider** | Select the provider created above |

3. Save. The **Issuer URI** will now be:
   ```
   https://<authentik-host>/application/o/pubgity/
   ```
   Use this as `OIDC_ISSUER_URI`.

---

### 3 — Enable Self-Enrollment and Account Recovery (optional)

These are handled entirely by Authentik — no code changes in Pubgity are needed.

- **User registration (self-enrollment)**: In your authorization flow, add the
  *User enrollment* stage or use Authentik's `default-enrollment-flow`.
- **Password reset / recovery**: Enable the `default-recovery-flow` on the provider or
  point users to `https://<authentik-host>/if/flow/default-recovery-flow/`.

---

### 4 — Finding the `sub` for `APP_ADMIN_SUB`

The `sub` is the user's UUID in Authentik.

1. Go to **Directory → Users** → click the user you want to be the first admin.
2. Click the **User Info** tab (or check the **Attributes** section).
3. Find the `sub` field — it looks like `a1b2c3d4-e5f6-7890-abcd-ef1234567890`.

Alternatively, have the target user log in to Pubgity first (they will be created as
role `USER`), then query MongoDB:

```js
db.app_users.findOne({ email: "your@email.com" }, { sub: 1 })
```

---

### 5 — Promote the first admin

Set `APP_ADMIN_SUB` to the sub value found above **before** (or on) first application start.
The promotion is idempotent — it only runs if no ADMIN exists yet.

```yaml
# docker-compose.yml example
services:
  pubgity:
    environment:
      APP_ADMIN_SUB: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
      OIDC_ISSUER_URI: "https://authentik.example.com/application/o/pubgity/"
      OIDC_CLIENT_ID: "abc123"
      OIDC_CLIENT_SECRET: "supersecret"
```

After the first ADMIN exists, additional admins can be assigned via the Pubgity
**Admin → User Management** panel (`/admin/users`).

---

## Using a different OIDC provider

Any provider that:
- Is compatible with the OIDC Core 1.0 specification,
- Exposes a `/.well-known/openid-configuration` discovery document,
- Issues `sub`, `email`, and (ideally) `preferred_username` claims,

...will work with Pubgity without code changes. Simply set the three environment
variables to point at your provider.

Providers known to be compatible: **Keycloak**, **Okta**, **Auth0**, **Dex**,
**Microsoft Entra ID** (Azure AD), **Google**, **GitLab**, **GitHub** (via OIDC bridge).

