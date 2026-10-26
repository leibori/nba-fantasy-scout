# Demo runner

`DemoRunner` is a manual end-to-end check against the **real ESPN and Sleeper APIs** and the **dev Postgres**:
ESPN → database → `TrendCalculator`, plus Sleeper → `PlayerMatcher`. It prints an `[OK]` / `[FAIL]` line per check and ends with
`DEMO PASSED` or `DEMO FAILED: N check(s)`.

It runs only with the `demo` profile, so it never runs on a normal start or in the tests.

## What it checks

### 1. Players: ESPN rosters → `players` table
| Check | Proves |
|---|---|
| `ESPN returned 30 teams' worth of players (518)` | ESPN roster client and parser work; fails below 400 players |
| `players table holds them (518)` | `PlayerRepository.saveAll` wrote them to Postgres |
| `some players have an injury status` | Injury status parsing works |

### 2. Game logs: ESPN → `game_logs` table, inserted twice
For Luka Doncic, Nikola Jokic and Shai Gilgeous-Alexander:

| Check | Proves |
|---|---|
| `fetched 65 games, 65 stored` | Game log client works and the DB holds exactly what ESPN returned |
| `second upsert changed nothing (ON CONFLICT)` | Re-running adds no duplicates and rewrites nothing unchanged (`GameLogUpsert`) |

A player who is no longer on a current roster prints `[SKIP]` instead of failing.

### 3. DB → `TrendCalculator` → `HotStreakScoring`
| Check | Proves |
|---|---|
| `trend computed from 65 stored games` | Data read back from Postgres flows into the trend logic |

It also prints the recent and baseline averages and the hot score.

### 4. Sleeper trending adds → `PlayerMatcher` → ESPN players
Prints each of Sleeper's top 25 trending adds with the ESPN player it matched and the confidence.

| Check | Proves |
|---|---|
| `Sleeper returned trending adds (25)` | Sleeper client and trending parser work |
| `Sleeper player names loaded (2146)` | The ~2.5 MB player list parses (team entries skipped) |
| `most trending names matched an ESPN player (25/25)` | `PlayerMatcher` maps real Sleeper names to ESPN ids; fails below 90% sure matches |

Nothing from Sleeper is stored yet; `trending_snapshots` comes with Flyway V2.

## How to run it

**1. Start the dev DB** (once per boot):
```
docker compose up -d
```

**2. Run the demo**

Git Bash:
```
JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStoreType=Windows-ROOT" ./gradlew bootRun --args="--spring.profiles.active=demo --spring.main.web-application-type=none"
```

PowerShell:
```powershell
$env:JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"; ./gradlew bootRun --args="--spring.profiles.active=demo --spring.main.web-application-type=none"
```

- `--spring.profiles.active=demo` switches the demo on.
- `--spring.main.web-application-type=none` skips the web server, so the app exits when the demo is done.
- `JAVA_TOOL_OPTIONS=...Windows-ROOT` makes Java trust the Windows certificate store. Only needed on a machine
  whose antivirus or proxy inspects HTTPS (otherwise ESPN calls fail with `PKIX path building failed`).

IntelliJ: in the run configuration of `NbaFantasyScoutApplication` set
- Active profiles: `demo`
- VM options: `-Djavax.net.ssl.trustStoreType=Windows-ROOT`
- Program arguments: `--spring.main.web-application-type=none`

**3. Read the result.** Spring's log lines appear around the report; the demo lines start with `===` and
`[OK]` / `[FAIL]`.

**4. Optional: look at the data yourself**
```
docker compose exec postgres psql -U scout -d scout -c "SELECT count(*) FROM game_logs;"
```

## Notes
- It writes to the **dev** database (players and game logs), never the test database. Reset with
  `docker compose down -v`.
- It works on an empty or an already filled database.
- Keep it updated as features land: switch to the wired `SyncService` (step 5.6), check stored Sleeper
  snapshots (V2) and recommendations (step 6).
