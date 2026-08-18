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
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_level_positive CHECK (level >= 1),
    CONSTRAINT chk_users_xp_non_negative CHECK (xp >= 0),
    CONSTRAINT chk_users_wins_non_negative CHECK (wins >= 0),
    CONSTRAINT chk_users_losses_non_negative CHECK (losses >= 0)
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

CREATE INDEX idx_user_roles_role_id ON user_roles (role_id);

CREATE TABLE characters (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    hp              INTEGER NOT NULL,
    base_hp         INTEGER NOT NULL,
    attack          INTEGER NOT NULL,
    level_required  INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_characters_hp_positive CHECK (hp > 0),
    CONSTRAINT chk_characters_base_hp_positive CHECK (base_hp > 0),
    CONSTRAINT chk_characters_attack_positive CHECK (attack > 0),
    CONSTRAINT chk_characters_level_required_positive CHECK (level_required >= 1)
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
    CONSTRAINT chk_battles_next_turn CHECK (next_turn IN ('INITIATOR', 'OPPONENT')),
    -- Winner exclusivity truth table:
    --   winner_user_id IS NULL,     winner_is_machine = false -> valid (not finished yet)
    --   winner_user_id IS NOT NULL, winner_is_machine = false -> valid (a human won)
    --   winner_user_id IS NULL,     winner_is_machine = true  -> valid (the machine won, PVE only)
    --   winner_user_id IS NOT NULL, winner_is_machine = true  -> INVALID (contradictory: can't both win)
    CONSTRAINT chk_battles_winner_exclusivity CHECK (winner_user_id IS NULL OR winner_is_machine = false),
    CONSTRAINT chk_battles_initiator_hp_non_negative CHECK (initiator_current_hp >= 0),
    CONSTRAINT chk_battles_opponent_hp_non_negative CHECK (opponent_current_hp >= 0),
    CONSTRAINT chk_battles_turn_number_positive CHECK (turn_number >= 1)
);

CREATE INDEX idx_battles_initiator_user ON battles (initiator_user_id);
CREATE INDEX idx_battles_opponent_user ON battles (opponent_user_id);

-- Ranking query: top users by wins desc, losses asc, xp desc.
CREATE INDEX idx_users_ranking ON users (wins DESC, losses ASC, xp DESC);

-- updated_at maintenance is handled at the DB level via a shared trigger
-- function rather than relying on JPA @UpdateTimestamp, so it can never be
-- forgotten on a future entity. One function, reused by every table that has
-- an updated_at column.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_roles_set_updated_at
    BEFORE UPDATE ON roles
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_characters_set_updated_at
    BEFORE UPDATE ON characters
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_battles_set_updated_at
    BEFORE UPDATE ON battles
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
