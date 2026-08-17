-- Baseline schema for stars-battle: users, roles, user_roles, characters, battles.
-- Enums are modeled as varchar + CHECK (Hibernate 6 native-enum JDBC types are
-- brittle across drivers/Testcontainers; varchar+CHECK is portable and simple).
-- Timestamps use TIMESTAMPTZ to avoid timezone ambiguity in a greenfield build.

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255) NOT NULL,
    level          INTEGER NOT NULL DEFAULT 1,
    xp             INTEGER NOT NULL DEFAULT 0,
    wins           INTEGER NOT NULL DEFAULT 0,
    losses         INTEGER NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE user_roles (
    user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id  BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE characters (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    hp              INTEGER NOT NULL,
    base_hp         INTEGER NOT NULL,
    attack          INTEGER NOT NULL,
    level_required  INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE battles (
    id                       BIGSERIAL PRIMARY KEY,
    mode                     VARCHAR(10) NOT NULL,
    status                   VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    initiator_user_id        BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    opponent_user_id         BIGINT REFERENCES users(id) ON DELETE SET NULL,
    winner_user_id           BIGINT REFERENCES users(id) ON DELETE SET NULL,
    winner_is_machine        BOOLEAN NOT NULL DEFAULT false,
    initiator_character_id   BIGINT NOT NULL REFERENCES characters(id) ON DELETE RESTRICT,
    opponent_character_id    BIGINT REFERENCES characters(id) ON DELETE SET NULL,
    initiator_current_hp     INTEGER NOT NULL DEFAULT 1,
    opponent_current_hp      INTEGER NOT NULL DEFAULT 1,
    turn_number              INTEGER NOT NULL DEFAULT 1,
    next_turn                VARCHAR(10) NOT NULL DEFAULT 'INITIATOR',
    ended_at                 TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_battles_mode CHECK (mode IN ('PVP', 'PVE')),
    CONSTRAINT chk_battles_status CHECK (status IN ('WAITING', 'IN_PROGRESS', 'FINISHED')),
    CONSTRAINT chk_battles_next_turn CHECK (next_turn IN ('INITIATOR', 'OPPONENT'))
);

CREATE INDEX idx_battles_initiator_user ON battles (initiator_user_id);
CREATE INDEX idx_battles_opponent_user ON battles (opponent_user_id);

-- Ranking query: top users by wins desc, losses asc, xp desc.
CREATE INDEX idx_users_ranking ON users (wins DESC, losses ASC, xp DESC);
