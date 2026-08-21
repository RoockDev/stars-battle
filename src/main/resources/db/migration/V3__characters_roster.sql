-- Star Wars character roster. Stats copied verbatim from the source's
-- prisma/seeds/characters.seed.cjs. This is core reference data required for
-- the app to function in every environment (PVE is unplayable without it),
-- so it lives in the main migration chain, not the dev-only seed (design D9).
-- Insertion order matches the source seed order and determines the id
-- sequence, which is what "GET /characters ordered by id asc" returns.
INSERT INTO characters (name, hp, base_hp, attack, level_required) VALUES
    ('Luke Skywalker',     100, 100, 20, 1),
    ('Han Solo',            90,  90, 18, 1),
    ('Leia Organa',         95,  95, 19, 1),
    ('Obi-Wan Kenobi',     120, 120, 24, 2),
    ('Boba Fett',          105, 105, 22, 2),
    ('Ahsoka Tano',        115, 115, 25, 2),
    ('Darth Vader',        140, 140, 30, 3),
    ('Mace Windu',         130, 130, 28, 3),
    ('Yoda',               110, 110, 32, 4),
    ('Darth Maul',         125, 125, 29, 4),
    ('Emperor Palpatine',  100, 100, 35, 5),
    ('Rey Skywalker',      118, 118, 31, 5);
