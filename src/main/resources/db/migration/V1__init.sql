CREATE TABLE players (
    id       BIGINT PRIMARY KEY,          -- ESPN athlete id, so the upsert can key on it
    name     VARCHAR(100) NOT NULL,
    team     VARCHAR(10),
    position VARCHAR(10),
    status   VARCHAR(20)                  -- injury status from ESPN rosters
);

-- Raw counting stats only; percentages and per-minute rates are computed in Java.
CREATE TABLE game_logs (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players (id),
    game_date DATE NOT NULL,
    minutes   SMALLINT NOT NULL,
    fgm       SMALLINT NOT NULL,
    fga       SMALLINT NOT NULL,
    fg3m      SMALLINT NOT NULL,
    ftm       SMALLINT NOT NULL,
    fta       SMALLINT NOT NULL,
    reb       SMALLINT NOT NULL,
    ast       SMALLINT NOT NULL,
    stl       SMALLINT NOT NULL,
    blk       SMALLINT NOT NULL,
    tov       SMALLINT NOT NULL,
    pts       SMALLINT NOT NULL,
    CONSTRAINT uq_game_logs_player_date UNIQUE (player_id, game_date)
);
