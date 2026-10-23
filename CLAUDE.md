# nba-fantasy-scout

Spring Boot backend for NBA fantasy basketball. It detects breakout and declining players from recent form (3-game rolling average vs. season baseline), and advises who to keep, watch or drop in the user's squad, including from roster screenshots.

Purpose: CV project for a mid-level Java backend role. Focus on Java: a testable domain layer, design patterns, concurrency, tests. The database stays simple (storage + simple queries); all analytics run in Java. The UI stays minimal.

## How to work with me
- Work in small steps, one build-order step at a time. Do not jump ahead.
- Before writing code for a step, say in 2-3 lines what you are about to create and why.
- I am a Java developer; keep explanations short, but explain non-obvious SQL and any new library the first time it appears.
- Do not add features, dependencies or files that are not in this document without asking.
- Write tests only where there is real logic (JUnit 5, Mockito, Testcontainers); setup steps like skeleton and schema need none.
- Keep tests lean: one JUnit 5 test class per logic class, covering the main cases and the edge cases that actually break; no near-duplicate tests.
- Log important actions (INFO), skipped/odd data (WARN) and failures with stack trace (ERROR) via SLF4J (`LoggerFactory.getLogger(X.class)`), no custom log wrapper. Logs go to `logs/scout.log`, rotated daily, kept 2 days (config in `application.yml`).
- Never commit secrets. API keys come from environment variables (e.g. `${BALLDONTLIE_API_KEY}`), never hard-coded.
- After finishing a step, update the "Build order" status below.

## Stack
- Java 21, Spring Boot 4.x, Gradle (Kotlin DSL)
- Spring Web, Spring Data JPA, Validation
- PostgreSQL 16 (Docker Compose), Flyway migrations (`ddl-auto: validate`; Flyway owns the schema)
- Resilience4j (rate limiting, retry with exponential backoff)
- Tests: JUnit 5, Mockito, Testcontainers
- Logging: SLF4J/Logback with MDC correlation IDs
- Package: `com.ori_leibo.nbafantasyscout`

## Local setup
- `docker-compose.yml` in the project root runs `postgres:16`: db `scout`, user `scout`, password `scout`, port 5432 (dev only)
- Start DB: `docker compose up -d`; reset: `docker compose down -v`
- `src/main/resources/application.yml`: datasource `jdbc:postgresql://localhost:5432/scout`, `ddl-auto: validate`
- Run: `./gradlew bootRun`; tests: `./gradlew test`
- Spring Initializr generated test helpers (`TestcontainersConfiguration`, `Test...Application`); keep them for the Testcontainers step

## Architecture
Layered, package-by-feature. Each layer only calls the layer below it.
- `api`: controllers + request/response DTO records (`api/dto`), `@RestControllerAdvice` error handler
- `sync`: ingestion, scheduler, job status
- `client`: `NbaDataClient` interface + ESPN adapter; `SleeperClient` (trending adds/drops); both share a JDK `java.net.http.HttpClient` + Resilience4j config (plain Java; Spring only wires them)
- `trend`: `TrendCalculator`, scoring strategies
- `recommend`: recommendation and keep/watch/drop rules
- `roster`: `RosterService`, `RosterExtractor`, `PlayerMatcher`
- `domain`: entities and records (`Player`, `GameLog`, `StatCategory`)
- `config`: thread pool, Resilience4j, MDC, `@ConfigurationProperties`

Controllers never touch repositories. APIs return DTO records, never JPA entities.

## Data model
- Target league: Yahoo H2H 9-cat (FG%, FT%, 3PTM, PTS, REB, AST, ST, BLK, TO)
- `players`: id (ESPN athlete id), name, team, position, status (injury, from ESPN roster `injuries[0].status`, e.g. `Out`, `Day-To-Day`; null = healthy)
- `game_logs`: id, player_id (FK), game_date, minutes, fgm, fga, fg3m, ftm, fta, reb, ast, stl, blk, tov, pts; UNIQUE (player_id, game_date)
- `trending_snapshots` (Flyway V2): player_id (FK), type (ADD/DROP), count, fetched_at; keeps history ("adds rising 3 days")
- Only raw counting stats are stored; FG%/FT% (volume-weighted) and per-minute rates are computed in Java
- No cache table: computed trends are cached in memory (Spring Cache + Caffeine)
- SQL is kept minimal: Flyway schema, simple JPA derived queries (e.g. `findByPlayerIdOrderByGameDateAsc`), one upsert

## Core logic (pure Java, no Spring/DB in the domain classes)
- Domain types: `GameLog`/`StatLine` records, `StatCategory` enum
- `TrendCalculator` (Streams): 3-game rolling average, season baseline from games BEFORE the window (so the delta isn't diluted), delta per category
- Detects both directions: breakout (positive deltas) and decline (negative deltas)
- Scoring via Strategy pattern: `ScoringStrategy` interface; `HotStreakScoring` (weighted minutes + FGA + opportunity = (FGA + FTA + TOV) per minute, a usage-rate stand-in), `CategoryScoring`; weights from `@ConfigurationProperties`
- Noise filter: minimum baseline games, and minimum minutes in the recent OR baseline period (so a benched starter still shows as a decline)
- Recommendations: `Recommendation` sealed interface with `Keep` / `Watch` / `Drop(replacement)` records; pattern-matching `switch`
- Keep/drop rules as an ordered list of `RosterRule` (Chain of Responsibility)
- Crowd cross-check: `CrowdSignal` classifies (crowd direction from Sleeper adds/drops, model direction from trend), both ways:
  - adds + breakout = confirmed breakout; adds + nothing = hype; no adds + breakout = early pickup
  - drops + decline = confirmed decline; drops + stats fine = panic drop (hold / buy low); no drops + decline = early warning
  - Crowd data is evidence shown to the user, never part of the score
- Injury and crowd rules in the `RosterRule` chain: never suggest an `Out`/`IR` free agent as replacement; a heavily dropped player on the user's roster is judged by injury status first (injured = by injury length, healthy + decline = drop, healthy + fine = keep)
- `SyncService` publishes `SyncCompletedEvent` (Observer); a listener recomputes trends, cross-checks them with the latest trending snapshot, and evicts the cache
- Trend computation for all players runs in parallel on the thread pool (CPU work)

## Ingestion
- Source: ESPN's unofficial API (free, no key). balldontlie stats are paid; stats.nba.com blocks non-browser clients.
  - Player game log: `https://site.web.api.espn.com/apis/common/v3/sports/basketball/nba/athletes/{id}/gamelog?season=2026` (season = end year)
  - `stats` is a string array ordered like `labels`: MIN, FG, FG%, 3PT, 3P%, FT, FT%, REB, AST, BLK, STL, PF, TO, PTS
  - Made-attempted values are strings like `"8-18"`; game date is in `events[eventId].gameDate` (UTC, convert to US date); keep regular season only
  - Player list (verified): `https://site.api.espn.com/apis/site/v2/sports/basketball/nba/teams` (30 teams at `sports[0].leagues[0].teams[].team`, `id` + `abbreviation`), then `.../teams/{id}/roster` (`athletes[]`: `id`, `fullName`, `position.abbreviation`, `injuries[]`); rosters are always the current season
  - Injury status comes from the roster too (same ESPN id, no name matching)
- Secondary source: Sleeper API (free, no key, base `https://api.sleeper.app/v1`), used for crowd signals only
  - `GET /players/nba/trending/{add|drop}?lookback_hours=24&limit=25`: `[{player_id, count}]`
  - `GET /players/nba` (~2.5 MB, at most once a day): needed to turn Sleeper `player_id` into a name; also has `injury_body_part` if injury detail is wanted later
  - No ESPN ids in Sleeper data: map players by name via `PlayerMatcher`
  - Runs on a schedule inside the daily sync (add/drop snapshots), not on user request
- API client behind an interface so the source can be swapped (or a CSV dataset loaded)
- Idempotent inserts: `INSERT ... ON CONFLICT DO NOTHING`
- Concurrency (added after single-threaded version works): `@Async` + `CompletableFuture` on a custom `ThreadPoolTaskExecutor`, Resilience4j rate limiter, MDC propagated with a `TaskDecorator`
- Measure sync time before/after concurrency (the number goes on the CV)

## Endpoints
- `POST /api/v1/sync/daily`: returns 202 + job id; also `GET /api/v1/sync/jobs/{jobId}` and a `@Scheduled` trigger
- `GET /api/v1/recommendations/hot?limit=10`: top by hot score
- `GET /api/v1/recommendations/{category}`
- `GET /api/v1/players/{id}/trend`
- `POST /api/v1/roster`: list of player names, returns keep / watch / drop + best replacement
- `POST /api/v1/roster/from-image`: `MultipartFile[]` screenshots, extracts names, returns detected players with confidence for user review, then recommendations

## Roster from screenshots
- `RosterExtractor` interface: vision-model API implementation (primary), Tesseract/Tess4J (free fallback)
- `PlayerMatcher` in Java: normalization (case, accents via `java.text.Normalizer`, "J. Smith") + own Levenshtein implementation; low confidence = flagged "unsure"
- User can correct names before recommendations
- Multiple screenshots: merge and dedupe
- Images processed in memory, never stored
- Decision rule: keep = high hot score; drop = low score and a free agent clearly higher; otherwise watch; thresholds in config

## UI
- Swagger UI (springdoc), added with the first controller
- One static HTML page in `src/main/resources/static/` with vanilla JS: hot list table, category dropdown, screenshot upload, editable detected names, results table
- Optional: daily top-10 push to Telegram/email

## Design patterns used
Layered architecture, Repository, DTO, Strategy (scoring, extractor), Adapter (API client), Chain of Responsibility (roster rules), Observer (sync events), Cache-aside (Caffeine), Dependency Injection, Retry/Rate limiter (Resilience4j).

## Build order (update status as you go)
1. Skeleton: project, docker-compose, config, README stub: DONE
2. Schema: Flyway `V1__init.sql` (players, game_logs); app starts and Flyway applies V1: DONE
3. Call the data API once and inspect the real JSON; adjust schema: DONE (switched to ESPN, V1 reworked for Yahoo 9-cat)
4. Domain records + `TrendCalculator` + scoring strategies, test-first, with unit tests (can start with fake games): DONE
5. Single-threaded ingestion (ESPN players, injuries and game logs + Sleeper trending snapshots, Flyway V2); `PlayerMatcher` built here (needed for Sleeper name mapping), reused in step 9
6. Recommendation endpoints + Caffeine cache + Swagger; `CrowdSignal` cross-check and crowd evidence in responses
7. Tests: unit tests for domain logic, Testcontainers for repositories/ingestion, Mockito for services, `@WebMvcTest` for controllers
8. Concurrency + Resilience4j + MDC + `SyncCompletedEvent`; optional comparison of platform threads vs virtual threads for fetching
9. Roster endpoint (typed names), then screenshot input
10. Polish: 202 sync, scheduler, static page, README with demo GIF
11. CI: GitHub Actions running tests

## Current task
Finish step 2: make sure `application.yml` matches docker-compose, create `src/main/resources/db/migration/V1__init.sql` with the two tables, run the app and confirm Flyway applied V1 with no errors. Then commit. Do not start step 3 until I confirm.

## Conventions
- Small commits, push often
- Tests for logic, not for setup steps
- Config values (weights, thresholds, pool size) in `application.yml`, not hard-coded
