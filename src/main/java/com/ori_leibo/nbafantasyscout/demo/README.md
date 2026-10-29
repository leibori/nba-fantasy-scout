# Demo runner

`DemoRunner` is my manual end-to-end check. It runs the real sync against ESPN and Sleeper into the dev database, then
pushes that data through the trend logic and prints a report with an `[OK]` / `[FAIL]` line per check. It ends with
`DEMO PASSED` or `DEMO FAILED: N check(s)`.

It only runs with the `demo` profile, so it never kicks in on a normal start or during tests.

## Running it

```
docker compose up -d
./gradlew bootRun --args="--spring.profiles.active=demo --spring.main.web-application-type=none"
```

`web-application-type=none` skips the web server, so the app exits when the report is done. In IntelliJ: set the active
profile to `demo` and add the same program argument.

A full run takes 2-3 minutes because the sync fetches every player's games one call at a time. On an empty database the
first run also pulls last season for everyone, so it takes about twice as long.

> If ESPN calls fail with `PKIX path building failed`, something on the machine (antivirus, corporate proxy) is
> inspecting HTTPS. Add `-Djavax.net.ssl.trustStoreType=Windows-ROOT` to the JVM options to use the Windows
> certificate store.

## What it does

1. **Sync.** Runs the daily sync and prints the summary, e.g.
   `518 players, 0 game logs new or changed, last season fetched for 80, 0 failed players, 49 snapshots, took 120 s`.
   Then checks that ESPN returned a full league (400+ players), that injury statuses came through, that no player
   failed, and that Sleeper trending rows were matched and stored.
2. **Upsert again.** Re-saves Luka Doncic's season and checks that nothing changed. If the upsert ever starts
   rewriting unchanged rows, this fails.
3. **Trend for one player.** Reads his games back from Postgres and runs `TrendCalculator` and `HotStreakScoring` on
   them. Early in a season the baseline is last season, printed as `LAST_SEASON, provisional`.
4. **Everyone.** Runs `TrendService` for all rostered players and prints the count per form, the top 10 by value,
   all `EARLY_HOT` / `RETURNING` / `EARLY_COLD` players, the crowd signal for everyone in the latest Sleeper snapshot,
   and the value of the 50th, 100th and 150th player. I used those last numbers to pick `scout.trend.high-value`.

On a database that's already filled, "game logs new or changed" should be 0 unless ESPN posted new games or stat
corrections. Sleeper names without a confident ESPN match (usually free agents) are logged as WARN and skipped.

## Notes

- It writes to the dev database, never the test one. `docker compose down -v` resets it.
- Every run appends a new batch of Sleeper snapshots on purpose: that's the history.
- To poke at the data: `docker compose exec postgres psql -U scout -d scout -c "SELECT count(*) FROM game_logs;"`
