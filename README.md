# Stars Battle

A turn-based, Star Wars-themed battle API — register, pick a character, fight
PVE against a machine opponent or PVP against another player, climb the
ranking. Built as a from-scratch architecture port: the original
implementation ([`batalla-friki-nestJS`](https://github.com/RoockDev/batalla-friki-nestJS))
is a NestJS + TypeScript + Prisma + PostgreSQL backend; this repository is a
greenfield rebuild of the same domain rules and behavior in **Java 21 /
Spring Boot 3**, done as a deliberate architecture exercise (not a literal
line-by-line translation). The lineage is intentional and documented, not
hidden — see [Deliberate deviations](#deliberate-deviations-from-the-reference-implementation)
below for where and why the port improves on the original.

## Tech stack

- Java 21, Spring Boot 3.5 (Web, Security, OAuth2 Resource Server, Data JPA,
  WebSocket, Validation, Actuator)
- PostgreSQL 16, Flyway migrations
- JWT (HS256) authentication, method-level RBAC via `@PreAuthorize`
- STOMP over native WebSocket for realtime battle updates
- springdoc-openapi (interactive API docs)
- Maven, JUnit 5, Mockito, Testcontainers, AssertJ — Strict TDD throughout

## Prerequisites

- Java 21
- Maven (or use the included `mvnw` if present, otherwise a local `mvn`
  install)
- Docker (for the local Postgres container and for running the test suite —
  the integration tests use Testcontainers, not an embedded/in-memory
  database, so Docker must be running)

## Running locally

1. Start Postgres:

   ```bash
   docker compose up -d
   ```

2. Set the JWT signing secret (the app fails fast at startup if this is
   missing — there is no insecure fallback):

   ```bash
   export JWT_SECRET="a-long-random-string-at-least-32-bytes"
   ```

3. Run the app:

   ```bash
   mvn spring-boot:run
   ```

   The API listens on `http://localhost:3000` by default (override with the
   `PORT` env var). Database connection details default to the
   `docker-compose.yml` values and can be overridden via
   `SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` /
   `SPRING_DATASOURCE_PASSWORD`.

### Dev profile (seeded demo data)

Run with the `dev` Spring profile active to get a set of ready-to-use demo
accounts seeded on startup, plus a one-click reset endpoint:

```bash
SPRING_PROFILES_ACTIVE=dev JWT_SECRET="..." mvn spring-boot:run
```

Under `dev`, Flyway additionally applies `classpath:db/seed` (demo accounts
only — the character roster is core reference data and is always present,
in every profile) and enables `POST /dev/reset`, which re-runs
`Flyway.clean()` + `Flyway.migrate()` to restore a clean seeded state. That
endpoint requires an authenticated `ADMIN` JWT and, outside the `dev`
profile, does not exist at all (`404`, not just `403`).

### Seeded demo accounts (`dev` profile only)

| Email | Password | Role |
|---|---|---|
| `admin@batalla.com` | `123456` | ADMIN |
| `user1@batalla.com` | `123456` | USER |
| `user2@batalla.com` | `123456` | USER |
| `user3@batalla.com` | `123456` | USER |
| `user4@batalla.com` | `123456` | USER |
| `user5@batalla.com` | `123456` | USER |

## Running tests

```bash
mvn test
```

The suite includes real Testcontainers-backed Postgres integration tests
(no H2 substitute — the migrations are Postgres SQL), so **Docker must be
running**. Pure unit tests (damage roll, reward formulas, battle rules) need
no external dependency.

## API overview

Full request/response shapes are available at runtime via the interactive
docs (see below); this is just the surface map.

| Group | Routes | Notes |
|---|---|---|
| Auth | `POST /auth/register`, `POST /auth/login` | Public. Returns a JWT + user summary. |
| Characters | `GET /characters` | Authenticated. Full Star Wars roster, ordered by id. |
| Users | `GET /users/ranking?limit=` | Authenticated. Top players by wins/losses/xp, `limit` clamped to 1-100. |
| Battles | `POST /battles/start/pve`, `POST /battles/start/pvp`, `POST /battles/{id}/join/pvp`, `GET /battles/{id}`, `POST /battles/{id}/turn`, `POST /battles/{id}/turn/pve` | Authenticated. PVE resolves both attacks per call; PVP resolves one attack and alternates turns. |
| WebSocket | STOMP over `/ws`, subscribe `/topic/battles/{id}` | JWT required on `CONNECT`; subscribing to a battle's topic requires being that battle's initiator, opponent, or an ADMIN. |
| Devtools | `POST /dev/reset` | `dev` profile + ADMIN only. Absent (404) everywhere else. |

### Interactive API docs

With the app running, open `http://localhost:3000/swagger-ui.html` (raw
OpenAPI document at `/v3/api-docs`). Both routes are intentionally public —
they only describe the API surface, never real data — same rationale as
`/actuator/health`.

## Deliberate deviations from the reference implementation

These are conscious improvements over the original NestJS implementation,
not accidental drift:

- **JWT secret fails fast.** The source falls back to a hardcoded secret
  (`process.env.SECRET_KEY || 'secretKey'`) if the env var is unset. This
  port has no fallback: the application refuses to start without
  `JWT_SECRET` configured, in both the signing and verification paths.
- **Ranking/leaderboard and battle view responses omit email.** The
  reference NestJS app exposed every ranked player's email to any
  authenticated user, and battle responses (`GET /battles/:id`) exposed both
  participants' emails to each other; this port drops it in both places as a
  privacy hardening, since neither a leaderboard nor a battle opponent/admin
  has a legitimate need to see peer contact info.
- **Optimistic locking on battles.** `Battle` carries a JPA `@Version`
  column. The source had no concurrency guard on turn resolution at all;
  here, two concurrent turn requests on the same battle race on that
  version, and the loser gets an explicit `409 Conflict` instead of silently
  corrupting HP/rewards through a lost update.
- **No unauthenticated seed/reset surface.** The source's grading-workflow
  endpoints (`POST /demo/seed`, `POST /demo/clear`, `GET /demo/overview`)
  were open and unauthenticated. The equivalent here — a Flyway dev-profile
  seed plus a single `POST /dev/reset` — requires BOTH the `dev` Spring
  profile AND an authenticated ADMIN JWT, through the same security filter
  chain as every other route.
- **Spanish API messages kept by design**, not translated — a deliberate
  choice to preserve the source's user-facing convention rather than an
  oversight.
- **Damage-roll and reward-formula behavior kept byte-identical** to the
  original (same tier thresholds, multipliers, `max(1, round(...))` floor,
  `xp += 10` / `level = floor(xp / 100) + 1` on win) — this is a parity
  requirement, not a deviation, called out here because it's easy to assume
  a rewrite silently "improved" the numbers. It didn't.
