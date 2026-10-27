-- NBA seasons played before this one, from ESPN rosters (0 = rookie); tells a rookie from a player back from a lost season.
ALTER TABLE players ADD COLUMN experience_years SMALLINT;
