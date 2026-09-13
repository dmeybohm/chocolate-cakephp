# Parallelize the test suite with per-fork IDE sandboxes

## Context

The full JUnit suite (about 556 tests across cake2/3/4/5 and core, all JUnit 3 style through the vintage engine) runs in one JVM and takes several minutes. A previous attempt at parallelizing showed no speedup.

The cause is that the IntelliJ Platform Gradle Plugin (2.10.4) injects the same JVM args into every test fork:

```
-Didea.system.path=build/idea-sandbox/IU-2023.2/system-test
-Didea.config.path=build/idea-sandbox/IU-2023.2/config-test
-Didea.log.path=build/idea-sandbox/IU-2023.2/log-test
-Didea.plugins.path=build/idea-sandbox/IU-2023.2/plugins-test
```

With `maxParallelForks > 1`, every fork shares one `system-test` (VFS caches, file-based and stub indexes, local history). Concurrent platforms contend on or corrupt that storage and re-index, which eats the gain. Within-JVM parallelism is impossible (one Application, one EDT, and the `CakeNBaseTestCase.setUp()` methods mutate the shared `Settings` service), so fork-level is the only axis.

Fix: run multiple forks and give each fork its own system/config/log directory. No Docker needed. `plugins-test` stays shared (read only). `src/test/fixtures` is only ever copied into the light fixture's temp project (`Fixture.kt` `configureByFilePathAndText` → `copyFileToProject`), never written, so it is safe to share.

Machine: 8 cores, 15 GB RAM. CI runner (ubuntu-latest) has 4 cores, 16 GB. Each IU test JVM wants roughly 1.5 to 2 GB, so the fork count should be memory-capped, not core-capped.

## Setup: worktree and branch

1. `git worktree add worktree/parallel-tests -b parallel-tests main` (the `/worktree/` dir is already gitignored; create it via the worktree add). All work happens in `worktree/parallel-tests`.
2. Create the feature log `features/2026/09/parallel-tests.md` with this plan and an "Implementation Progress" section. Commit.

## Changes

### 1. `build.gradle.kts` test task

Inside the existing `tasks { test { ... } }` block (build.gradle.kts:141):

- Add `maxParallelForks` read from a Gradle property so it can be tuned per machine and CI. Default: `min(4, availableProcessors / 2)`, floor 1. Override with `-PtestForks=N`. Must be configuration-cache safe (use `providers.gradleProperty("testForks")`, no `project` access at execution time).
- No `forkEvery`. Test classes are distributed across forks by Gradle; each fork keeps one warm platform for its whole share.

### 2. Per-fork sandbox listener

Add `testImplementation("org.junit.platform:junit-platform-launcher:1.8.2")` (matches the jupiter 5.8.2 line; `LauncherSessionListener` exists since Platform 1.8).

New file `src/test/kotlin/com/daveme/chocolateCakePHP/test/ForkSandboxListener.kt`, a `LauncherSessionListener`. In `launcherSessionOpened`:

- Read `org.gradle.test.worker` (Gradle sets it in every fork JVM). If absent (IDE run, single fork), do nothing.
- For `idea.system.path` and `idea.log.path`: take the current value `X` and set the property to `X-fork-<worker>` (a sibling directory under `build/idea-sandbox/IU-2023.2/`), creating it.
- For `idea.config.path`: same, but first copy the shared `config-test` tree into the fork dir (overwrite). The plugin's `prepareTest` writes `bundled_plugins.txt`, `disabled_plugins.txt`, `options/updates.xml`, and `ssl/` there and the platform reads them at startup.
- Leave `idea.plugins.path` alone.

This runs before any test class loads, so `PathManager` has not yet cached the paths. It must not touch any `com.intellij` class itself.

New file `src/test/resources/META-INF/services/org.junit.platform.launcher.LauncherSessionListener` containing the listener's FQN. `src/test/resources` does not exist yet; Gradle picks it up by convention.

### 3. Kover

`build/kover/test.ic` is a single IntelliJ-agent binary report written by the test task. Verify it survives multiple forks (see verification). If the report is truncated or the coverage number collapses, switch Kover to the JaCoCo engine, which supports concurrent appenders. Only do this if the verification fails.

### 4. CI

`.github/workflows/build.yml` runs `./gradlew check`. The default fork count formula gives 2 on the 4-core runner. Leave the workflow alone unless the CI timing shows it needs `-PtestForks`.

## Verification

Run everything from the worktree. Record wall times in the feature log's Implementation Progress section.

1. Baseline: `time ./gradlew test -PtestForks=1` on a clean build dir. Note total and slowest suites from `build/reports/tests/test`.
2. `time ./gradlew test -PtestForks=2` and `-PtestForks=4`. Expect a clear drop. Confirm `build/idea-sandbox/IU-2023.2/system-test-fork-N`, `config-test-fork-N`, and `log-test-fork-N` exist and each has its own `idea.log`.
3. Repeat the 4-fork run twice more to check for flakiness. All tests must pass each time.
4. `./gradlew check` and compare line coverage in `build/reports/kover/xml/report.xml` (or the Kover HTML) between the 1-fork and 4-fork runs. They should match within noise.
5. Run a single class from the IDE or with `--tests` to confirm the listener is a no-op when `org.gradle.test.worker` is set to a single worker (still works, just one fork dir).
6. Watch memory during the 4-fork run (`free -g`); if it swaps, lower the default.

Commit after the Gradle change, after the listener, and after the measurements are written to the feature log.

## Implementation Progress

### Session #1

- Created worktree `worktree/parallel-tests` on branch `parallel-tests` from `main`.
- (measurements to follow)
