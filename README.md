# Stars Battle

Java/Spring Boot port of the Stars Battle NestJS reference implementation.

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
