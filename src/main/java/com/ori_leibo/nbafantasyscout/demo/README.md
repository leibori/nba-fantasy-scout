# Demo runner

`DemoRunner` is a manual end-to-end check against the **real ESPN and Sleeper APIs** and the **dev Postgres**:
the wired `SyncService` fills the database, then the data flows into `TrendCalculator`. It prints an
`[OK]` / `[FAIL]` line per check and ends with `DEMO PASSED` or `DEMO FAILED: N check(s)`.

It runs only with the `demo` profile, so it never runs on a normal start or in the tests.
A full run takes about 2.5 minutes: the sync fetches every rostered player's season, one call at a time.

## What it checks

### 1. Daily sync: ESPN players + game logs, Sleeper trending → DB
Prints the sync summary, e.g. `518 players, 0 game logs new or changed, last season fetched for 80, 0 failed players,
49 snapshots, took 120 s`. The time is the single-threaded baseline to compare with the concurrent sync (step 8).
On an empty database the first sync also fetches last season for every player (about 250 s); later syncs only
re-ask the players who have no last-season games (rookies), about 80.

| Check | Proves |
|---|---|
| `ESPN returned 30 teams' worth of players (518)` | ESPN roster client and parser work; fails below 400 players |
| `players table holds them (518)` | Players were saved to Postgres |
| `some players have an injury status` | Injury status parsing works |
| `no player's game logs failed` | Every player's game log parsed (rookies and players out all season return no games, not an error) |
| `game_logs holds games for the roster (23569)` | Game logs were stored; fails at one game per player or fewer, so it also holds early in a season |
| `Sleeper trending stored as snapshots (49, 98 in total)` | Sleeper adds/drops were matched to ESPN players and appended to `trending_snapshots` |

On an already filled database "game logs new or changed" is 0: the upsert only writes real changes.
Sleeper names with no sure ESPN match (e.g. a free agent) are logged as WARN and skipped.

### 2. Upsert one player's season again
| Check | Proves |
|---|---|
| `Luka Doncic: second upsert changed nothing (ON CONFLICT)` | Re-running adds no duplicates and rewrites nothing unchanged (`GameLogUpsert`) |

### 3. DB → `TrendCalculator` → `HotStreakScoring`
| Check | Proves |
|---|---|
| `trend computed from 3 games this season, 64 last season` | Data read back from Postgres flows into the trend logic; early in a season the baseline is last season (printed as `LAST_SEASON, provisional`) |

It also prints the recent and baseline averages and the hot score. If Luka Doncic is not stored, sections 2
and 3 print `[SKIP]`.

### 4. Form of every rostered player (`TrendService`)
Prints the count per form (e.g. `{EARLY_HOT=25, GOOD=69, NEUTRAL=350, EARLY_COLD=74}`), the top 10 by value,
every EARLY_HOT player, and the value of the 50th / 100th / 150th player (used to set `scout.trend.high-value`).

| Check | Proves |
|---|---|
| `every rostered player has a form (518)` | Every player with a team gets trend, value and form |
| `players with games this season have a value (408)` | The value league was built and scored |

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
- It writes to the **dev** database (players, game logs, snapshots), never the test database. Reset with
  `docker compose down -v`.
- It works on an empty or an already filled database.
- Each run appends a new set of Sleeper snapshots (that is the history).
- Keep it updated as features land: recommendations and crowd signals (step 6).
