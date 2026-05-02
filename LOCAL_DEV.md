# Local Development Guide

Step-by-step guide to run Pubgity locally with Keycloak as the SSO provider.

## Prerequisites

- Docker with Compose plugin
- JDK 24

---

## Step 1 — Start infrastructure

```bash
# MongoDB
docker compose up -d

# Keycloak (starts on port 8090; imports pubgity realm automatically on first run)
docker compose -f docker-compose.keycloak.yml up -d
```

Keycloak takes about 30 seconds to be ready. Watch progress:

```bash
docker compose -f docker-compose.keycloak.yml logs -f keycloak
```

Wait until you see: `Keycloak 26.x.x on JVM (powered by Quarkus …) started`

---

## Step 2 — Verify the realm was imported

Open **http://localhost:8090** and log in with:

| Field    | Value   |
|----------|---------|
| Username | `admin` |
| Password | `admin` |

Go to **Realm selector (top-left) → pubgity**. You should see the realm with **3 roles** (ADMIN, MODERATOR, USER) and **5 pre-seeded users**.

> The realm is imported automatically from `keycloak/pubgity-realm.json` on first startup.
> On subsequent restarts the import is silently skipped (realm already exists).

---

## Step 3 — Configure `application-local.yml`

The local profile is **pre-configured** with the dev client credentials from the realm JSON.
Open `src/main/resources/application-local.yml` and confirm:

```yaml
client-id: "pubgity"
client-secret: "dev-client-secret"
issuer-uri: "http://localhost:8090/realms/pubgity"
```

No changes are needed for local development.

---

## Step 4 — Start Pubgity

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

Open **http://localhost:8080** — click **Sign in** to be redirected to Keycloak.

---

## Pre-seeded users

| Username        | Password     | Role      |
|-----------------|--------------|-----------|
| `pubgity_admin` | `Admin1234!` | ADMIN     |
| `mod_alpha`     | `Mod1234!`   | MODERATOR |
| `mod_beta`      | `Mod1234!`   | MODERATOR |
| `player_one`    | `User1234!`  | USER      |
| `player_two`    | `User1234!`  | USER      |

Login as `pubgity_admin` to access the Admin panel at **http://localhost:8080/admin/users**.

---

## Role management

Roles are defined as **Keycloak realm roles** (`ADMIN`, `MODERATOR`, `USER`) and synced into
Pubgity on every login. To change a user's role:

1. Open **http://localhost:8090** → log in as `admin`
2. Select the **pubgity** realm
3. Go to **Users** → click the user → **Role mapping** tab
4. Assign or remove the `ADMIN` / `MODERATOR` / `USER` realm role
5. The user's role in Pubgity updates on their **next login**

> The Pubgity Admin panel (`/admin/users`) manages **moderator constraints only**
> (allowed players and max queue size). Role assignment is always done in Keycloak.

---

## Quick reference

| URL                                         | Description                           |
|---------------------------------------------|---------------------------------------|
| `http://localhost:8080/`                    | Pubgity home                          |
| `http://localhost:8080/admin/users`         | Pubgity moderator constraints (ADMIN) |
| `http://localhost:8090`                     | Keycloak admin console                |
| `http://localhost:8090/realms/pubgity`      | Keycloak realm root                   |

---

## Reset Keycloak state

To start fresh (re-import the realm JSON from scratch):

```bash
docker compose -f docker-compose.keycloak.yml down -v
docker compose -f docker-compose.keycloak.yml up -d
```

---

## Stop everything

```bash
docker compose down
docker compose -f docker-compose.keycloak.yml down
```

