# nba-fantasy-scout

A Spring Boot backend that finds NBA players who are breaking out (or falling off) for fantasy basketball.
It pulls real box scores every day, scores every player, and serves the results over a REST API.

**Work in progress:** the sync, the scoring and the API work on live 2026-27 data. Next: a concurrent sync and
keep/drop advice for your own roster.

## How it scores players

- **Hot score: is he better than before?** His last 3 games vs. his earlier ones, based on minutes, shot attempts
  and usage rather than shooting luck.
- **Value: is he good right now?** His averages vs. the whole league in the 9 Yahoo fantasy categories.

Together they give a label (`HOT`, `EARLY_HOT`, `RETURNING`, `GOOD`, `NEUTRAL`, `EARLY_COLD`, `COLD`), which is
compared with what people are adding and dropping on Sleeper.

```json
{
  "name": "Jayson Tatum",
  "form": "EARLY_HOT",
  "gamesThisSeason": 4,
  "value": 5.7,
  "hotScore": 0.161,
  "recent":   { "minutes": 35.7, "points": 33.0, "assists": 7.0 },
  "baseline": { "minutes": 32.6, "points": 21.8, "assists": 5.3 },
  "crowd": { "signal": "EARLY_PICKUP" }
}
```

## API

| | |
|---|---|
| `GET /api/v1/recommendations/hot?limit=10` | Rising players, best value first |
| `GET /api/v1/recommendations/{category}` | Biggest improvement in one category (`pts`, `reb`, `blk`, ...) |
| `GET /api/v1/players/{id}/trend` | One player by ESPN id |

Swagger UI: `/swagger-ui.html`

## Running it

Needs Java 21 and Docker.

```bash
docker compose up -d
./gradlew bootRun
```

The database starts empty. To load real data, run the sync once (2-3 minutes):

```bash
./gradlew bootRun --args="--spring.profiles.active=demo --spring.main.web-application-type=none"
```

Tests: `./gradlew test` (Docker must be running; repository tests use Testcontainers).

## Worth knowing

- The sync is idempotent: a single `INSERT ... ON CONFLICT ... WHERE ... IS DISTINCT FROM` per player inserts new
  games, applies stat corrections and leaves unchanged rows alone.
- Both data sources are unofficial, so failures are expected: bad players are skipped, a down source stops the sync
  cleanly, and nothing already saved is lost.
- Results are cached with Caffeine (~350 ms down to ~35 ms per request) and cleared after every sync.

## Stack

Java 21, Spring Boot 4.1, PostgreSQL 16, Flyway, Caffeine, springdoc-openapi, JUnit 5, Mockito, Testcontainers.

---

Data from ESPN's public site API and the [Sleeper API](https://docs.sleeper.com/). Personal project, not affiliated
with ESPN, Sleeper, Yahoo or the NBA.
