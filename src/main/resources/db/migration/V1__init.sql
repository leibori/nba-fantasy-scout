CREATE TABLE players (
    id       BIGINT PRIMARY KEY,          -- balldontlie player id, so the upsert can key on it
    name     VARCHAR(100) NOT NULL,
    team     VARCHAR(10),
    position VARCHAR(10),
    status   VARCHAR(20)
);

CREATE TABLE game_logs (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_id  BIGINT NOT NULL REFERENCES players (id),
    game_date  DATE NOT NULL,
    minutes    NUMERIC(5,2) NOT NULL,
    pts        SMALLINT NOT NULL,
    reb        SMALLINT NOT NULL,
    ast        SMALLINT NOT NULL,
    stl        SMALLINT NOT NULL,
    blk        SMALLINT NOT NULL,
    fga        SMALLINT NOT NULL,
    usage_rate NUMERIC(5,2),              -- nullable: the API may not provide it
    CONSTRAINT uq_game_logs_player_date UNIQUE (player_id, game_date)
);
