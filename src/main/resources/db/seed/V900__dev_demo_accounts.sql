-- Dev-profile-only demo accounts (design D9, proposal decision #1). This file
-- lives under classpath:db/seed, which is only added to spring.flyway.locations
-- under the "dev" Spring profile (see application.yml) -- it never runs in
-- any other profile. Character roster is core reference data and stays in
-- the main migration chain (V3__characters_roster.sql), not here.
--
-- Password for every account is "123456" (documented plaintext for demo
-- convenience, matching the source's demo credentials), bcrypt-hashed at
-- cost 10 -- the same cost PasswordEncoderConfig uses everywhere else in the
-- app, so these hashes verify correctly against the real login flow.

INSERT INTO users (email, password_hash) VALUES
    ('admin@batalla.com', '$2a$10$3ItKuZJI4Snua/5XbTPKXe70K8hWiPh6fXzeW/ICIxhufAopxOU0W'),
    ('user1@batalla.com', '$2a$10$3ItKuZJI4Snua/5XbTPKXe70K8hWiPh6fXzeW/ICIxhufAopxOU0W'),
    ('user2@batalla.com', '$2a$10$3ItKuZJI4Snua/5XbTPKXe70K8hWiPh6fXzeW/ICIxhufAopxOU0W'),
    ('user3@batalla.com', '$2a$10$3ItKuZJI4Snua/5XbTPKXe70K8hWiPh6fXzeW/ICIxhufAopxOU0W'),
    ('user4@batalla.com', '$2a$10$3ItKuZJI4Snua/5XbTPKXe70K8hWiPh6fXzeW/ICIxhufAopxOU0W'),
    ('user5@batalla.com', '$2a$10$3ItKuZJI4Snua/5XbTPKXe70K8hWiPh6fXzeW/ICIxhufAopxOU0W');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@batalla.com' AND r.name = 'ADMIN';

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email IN ('user1@batalla.com', 'user2@batalla.com', 'user3@batalla.com',
                   'user4@batalla.com', 'user5@batalla.com')
  AND r.name = 'USER';
