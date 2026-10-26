-- Sleeper trending adds/drops, one row per player per sync; kept as history ("adds rising 3 days").
CREATE TABLE trending_snapshots (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    player_id  BIGINT NOT NULL REFERENCES players (id),
    type       VARCHAR(4) NOT NULL CHECK (type IN ('ADD', 'DROP')),
    count      INTEGER NOT NULL,              -- leagues that added/dropped the player in the look-back window
    fetched_at TIMESTAMPTZ NOT NULL
);
