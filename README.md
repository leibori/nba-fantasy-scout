# nba-fantasy-scout

A Spring Boot backend that looks for NBA players who are breaking out (or falling off) for fantasy basketball.
It pulls real box scores every day, works out who's trending, and serves it over a REST API.

**Work in progress.** Ingestion, the analytics, the API and caching all work on live 2026-27 data. Next up is making
the sync concurrent, then keep/drop advice for your own roster. See [what's next](#whats-next).

## The idea

In fantasy basketball the free pickups that matter are players whose role just grew: more minutes, more shots. The
trick is telling that apart from three lucky games. So the service asks two separate questions about every player:

- **Is he better than before?** Compare his last 3 games with his earlier games. This gives a *hot score* built from
  minutes, shot attempts and usage, not shooting percentage, which is mostly noise over a few games.
- **Is he good right now?** Compare his season averages with the rest of the league in the 9 Yahoo fantasy categories
  (z-scores). This gives a *value*.

I ended up needing both because one number couldn't cover the obvious cases: a veteran playing exactly like last year
is good but not "hot", and a rookie has no "before" at all. The two numbers are combined into a label (`HOT`,
`EARLY_HOT`, `RETURNING`, `GOOD`, `NEUTRAL`, `EARLY_COLD`, `COLD`) and then compared with what people are actually
adding and dropping on Sleeper, so you can see "the model likes him and nobody has noticed yet" vs. "everyone is
already adding him".

Here's a real response from four games into the season (`GET /api/v1/recommendations/hot`, trimmed):

```json
{
  "name": "Jayson Tatum",
  "team": "BOS",
  "form": "EARLY_HOT",
  "gamesThisSeason": 4,
  "value": 5.7,
  "hotScore": 0.161,
  "baselineSource": "LAST_SEASON",
  "provisional": true,
  "recent":   { "minutes": 35.7, "points": 33.0, "assists": 7.0, "fieldGoalPct": 0.492 },
  "baseline": { "minutes": 32.6, "points": 21.8, "assists": 5.3, "fieldGoalPct": 0.411 },
  "crowd": { "signal": "EARLY_PICKUP", "move": null, "count": null }
}
```

Four games isn't enough for a baseline from this season, so it's compared with last season and flagged `provisional`.

## How it's put together

```
  ESPN (rosters, box scores) ──┐
                               ├──> SyncService ──upsert──> PostgreSQL
  Sleeper (adds / drops) ──────┘         │                      │
                                         │ clears cache         │
                                         v                      v
  REST controllers ──> RecommendationService ──> TrendService (cached) ──> trend / scoring classes
```

The sync and the API are separate on purpose: requests never call ESPN or Sleeper, so a slow or broken source can't slow
down a request. The database only stores raw counting stats; every average, percentage and score is computed in Java.

Packages, roughly bottom to top:

- `domain`: plain records and enums (`Player`, `GameLog`, `StatLine`, `StatCategory`). No Spring, no JPA.
- `trend`: the analytics. `TrendCalculator`, a `ScoringStrategy` interface with `HotStreakScoring` and
  `CategoryScoring`, `ValueScoring` (z-scores) and `FormClassifier`. All plain Java, so the tests run in milliseconds.
- `client`: ESPN and Sleeper. The rest of the app only sees an `NbaDataClient` interface, and the JSON parsing lives in
  separate mappers that are tested on saved real responses.
- `roster`: `PlayerMatcher`, because Sleeper doesn't share ESPN's player ids. It normalizes names (accents, "Jr.",
  initials) and uses my own Levenshtein implementation; weak or tied matches are skipped.
- `persistence`: JPA entities (kept separate from the domain records), Spring Data repositories and one hand-written
  upsert in `JdbcTemplate`.
- `sync`, `recommend`, `api`: the daily ingestion, the ranking logic and the controllers.
- `config`: the services are plain classes wired with `@Bean` methods; settings come from a typed
  `@ConfigurationProperties` record, so every threshold and weight lives in `application.yml`.

## API

| | |
|---|---|
| `GET /api/v1/recommendations/hot?limit=10` | Rising players, best value first |
| `GET /api/v1/recommendations/{category}?limit=10` | Biggest improvement in one category (`pts`, `reb`, `ast`, `stl`, `blk`, `threes`, `to`, `fg_pct`, `ft_pct`) |
| `GET /api/v1/players/{id}/trend` | One player by ESPN id |

Swagger UI is at `/swagger-ui.html`. Errors come back as RFC 9457 problem details:

```json
{
  "title": "Bad Request",
  "status": 400,
  "detail": "Unknown category \"dunks\"; use one of [FG_PCT, FT_PCT, THREES, PTS, REB, AST, STL, BLK, TO]",
  "instance": "/api/v1/recommendations/dunks"
}
```

## Things that were harder than expected

**Re-running the sync safely.** The sync re-downloads whole seasons every day, so most games are already stored. It's
one batch per player with `INSERT ... ON CONFLICT (player_id, game_date) DO UPDATE ... WHERE (...) IS DISTINCT FROM (...)`:
new games get inserted, ESPN's stat corrections get applied, and unchanged rows aren't touched at all.

**The All-Star game.** After that was in place, a second sync of an unchanged day still rewrote 61 rows. Turns out ESPN
lists the All-Star games under "Regular Season", up to three on the same date, and they kept overwriting each other
under the `(player_id, game_date)` key. They don't count in fantasy anyway, so the mapper now drops them, along with
the Play-In and the NBA Cup final.

**External APIs fail.** Both sources are free and unofficial, so I treat failures as normal. A bad row gets skipped,
a player whose request fails gets skipped, 10 failures in a row stop the sync (the source is clearly down), an empty
roster response is refused instead of marking every player as gone, and if Sleeper is down the sync just finishes
without crowd data. There's deliberately no transaction around the whole sync: if it dies at player 400, the first 399
are kept and the next run fills in the rest.

**The start of the season.** Three games in, there's nothing to compare with. The trend falls back to last season and
says so. Rookies are judged on value alone. Players who missed all of last season are labelled `RETURNING` (I added
ESPN's experience count to the schema for that). And an early slump only counts if a player with a real role (18+
minutes) lost at least 15% of his minutes, since minutes settle within a few games and shot volume doesn't. I tuned
these against real data; the two minutes rules took the early "cold" list from 74 players down to 47.

**`@CacheEvict` and exceptions.** Recomputing every player took ~350 ms per request, so the result is cached with
Caffeine (~35 ms now) and cleared after each sync. My first version cleared it with `@CacheEvict` on the sync method.
While writing tests for the failure path I noticed that `@CacheEvict` doesn't evict when the method throws, and the
sync throws when it aborts, after it has already saved new data. So the API would have served stale results for up to
a day. `beforeInvocation = true` doesn't help either, because a request during the sync would cache half-updated data.
The sync now clears the cache in a `finally` block, and there's a test for the abort case.

## Running it

You need Java 21 and Docker.

```bash
docker compose up -d     # Postgres 16
./gradlew bootRun        # app on :8080, Flyway creates the schema
```

The database starts empty. To fill it with real data, run the demo profile once (about 2-3 minutes; it also prints a
report, see [`demo/README.md`](src/main/java/com/ori_leibo/nbafantasyscout/demo/README.md)):

```bash
./gradlew bootRun --args="--spring.profiles.active=demo --spring.main.web-application-type=none"
```

Then start the app normally and open <http://localhost:8080/swagger-ui.html>.

## Tests

```bash
./gradlew test    # needs Docker running
```

66 tests. Plain JUnit for the domain and analytics, Mockito for the sync's failure handling, Testcontainers with a real
Postgres 16 for the repositories and the upsert (the upsert is Postgres-specific SQL, so an in-memory DB wouldn't prove
anything), and `@WebMvcTest` for the controllers.

## Stack

Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Validation, Cache), PostgreSQL 16, Flyway, Caffeine, the JDK
`HttpClient`, Jackson, springdoc-openapi, JUnit 5, Mockito, Testcontainers, Gradle (Kotlin DSL), Docker Compose.

## What's next

- Make the sync concurrent: `CompletableFuture` on a bounded pool, a Resilience4j rate limiter and retries (ESPN
  shouldn't get hammered), correlation ids in the logs, and a `SyncCompletedEvent`. Today a full sync of ~520 players
  takes about 2 minutes one call at a time; I want to measure how far that drops, and compare platform vs. virtual
  threads while I'm at it.
- Roster advice: send your roster, get keep / watch / drop plus the best free agent to replace a drop. Later, reading
  the roster from a screenshot.
- Async sync endpoint with job status, a scheduler, and a small web page.
- CI on GitHub Actions.

---

Data comes from ESPN's public (undocumented) site API and the [Sleeper API](https://docs.sleeper.com/), both free and
keyless. This is a personal project and isn't affiliated with ESPN, Sleeper, Yahoo or the NBA.
