# auto-ant

`auto-ant` is a utility CLI that makes setting up and managing Ant + Tomcat projects in VS Code more seamless.

## Requirements

- Java 21
- VS Code
- [File Watcher](https://marketplace.visualstudio.com/items?itemName=appulate.filewatcher) extension
- Runtime Server Protocol UI extension

## Nice to have VS Code extensions

- Debugger for Java
- Extension Pack for Java
- Language Support for Java
- Project Manager for Java
- Test Runner for Java

## Getting started

1. Clone and install `auto-ant` as a user-level CLI:

   ```powershell
   git clone https://github.com/zacbemis/auto-ant.git
   cd auto-ant
   powershell -ExecutionPolicy Bypass -File .\install-auto-ant.ps1
   ```

   The installer builds the Gradle application distribution, copies it to `%LOCALAPPDATA%\Programs\auto-ant`, and adds `%LOCALAPPDATA%\Programs\auto-ant\bin` to your user `PATH`. Open a new terminal after installation and verify it is available:

   ```powershell
   auto-ant --help
   ```

   To update an existing install later, pull the latest code and run `powershell -ExecutionPolicy Bypass -File .\install-auto-ant.ps1` again.

2. Add Tomcat to Runtime Server Protocol UI. Keep it stopped for the first deployment unless Tomcat Manager lifecycle credentials are configured.

3. From your Ant web project, run `init`:

   ```powershell
   auto-ant init --tomcat C:\path\to\tomcat --jdk C:\path\to\jdk
   ```

   `init` detects the project layout, asks for the JDK home directory if `--jdk` is not provided, and generates or refreshes `auto-ant.build.xml`, `auto-ant.user.xml`, `auto-ant.properties`, `auto-ant.local.properties`, `.vscode/tasks.json`, and `.vscode/settings.json`. It does not mutate a live deployment directly. Run `auto-ant reconcile --confirm-stopped` while Tomcat is stopped, or configure Tomcat Manager lifecycle. Existing project `build.xml` files, including NetBeans builds, are left untouched.

   `auto-ant.build.xml` and auto-ant VS Code tasks/settings are managed by auto-ant and include comments explaining what not to edit directly. Put custom Ant properties, paths, targets, or helper macros in `auto-ant.user.xml`; auto-ant creates that file if missing and leaves it untouched afterward.

   After upgrading auto-ant, run this from an existing project to refresh generated build files and VS Code configuration without deploying:

   ```powershell
   auto-ant update
   ```

   `update` refreshes `auto-ant.build.xml` with a backup, preserves `auto-ant.user.xml`, merges every required property before applying explicit CLI overrides, refreshes `auto-ant:` VS Code tasks while preserving custom tasks, and preserves unrelated VS Code settings. Legacy/unversioned and schema-1 shared properties are backed up, upgraded to schema version `2`, and validated. Unknown future schema versions are refused. Projects generated before the reconcile architecture must run `auto-ant update` before deployment.

4. Useful commands:

   ```powershell
   auto-ant doctor
   auto-ant reconcile --confirm-stopped
   auto-ant develop --kind frontend
   auto-ant develop --kind classes
   auto-ant reconcile --install-hook
   auto-ant reload
   auto-ant vscode
   ```

   - `auto-ant doctor` detects project settings and reports verified source freshness plus live snapshot integrity, stale/critical state, configuration/schema problems, and recovery guidance. It does not mutate the deployment.
   - `auto-ant init` prompts for detected values, writes the generated auto-ant files, and checks the File Watcher extension. Safe deployment is a separate `reconcile` operation.
   - `auto-ant update` refreshes generated auto-ant build/config/VS Code files for the current CLI version. It preserves existing local/shared properties, custom tasks/settings, and does not deploy.
   - `auto-ant vscode` regenerates only `.vscode/tasks.json` and `.vscode/settings.json`, preserving unrelated settings. `refresh-vscode` is an alias.
   - `auto-ant run <target>` runs non-live-mutating targets from `auto-ant.build.xml`. Unsafe legacy live targets are blocked and direct users to `reconcile`.
   - `auto-ant reconcile` validates configuration, acquires an OS file lock, builds a complete snapshot, and promotes it with backup/rollback semantics. `branch-refresh` remains a compatibility alias.
   - `auto-ant develop --kind frontend|views|classes|config` acquires the same deployment lock, validates/recover-checks configuration, builds a trusted complete snapshot into CLI-controlled output, and incrementally mirrors only the selected owned category into an existing safe live directory. It never stops Tomcat, renames/promotes the deployment, accepts `--confirm-stopped`, or records a successful full reconcile. Frontend/views changes do not reload; changed classes/config use the configured `touch-webxml`, Manager, or manual `none` reload behavior. Build/validation failures happen before mutation, and post-mutation failures mark deployment state stale.
   - `auto-ant reload` reloads Tomcat using `reload.strategy`. Manager reload now polls Manager state with a bounded timeout. `touch-webxml` remains compatibility-only because it cannot prove readiness.

   Common generated Ant targets:

   - `clean` deletes build and dist output.
   - `init` creates build output directories.
   - `compile` compiles Java into `WEB-INF/classes`.
   - `copy-web` copies the web root into the exploded build directory.
   - `copy-libs` copies project JARs into `WEB-INF/lib`.
   - `war` builds the WAR file.
   - `clean-build` cleans and builds the WAR.
   - `reconcile-snapshot` builds and validates the complete exploded snapshot only at a unique CLI-controlled output supplied as highest-precedence Ant user properties. It does not invoke the general clean target.
   - `deploy-war` refuses unsafe direct WAR deployment.
   - `deploy-exploded`, `branch-refresh`, `sync-web`, `sync-web-inf`, and `compile-hot` are compatibility guards that refuse unsafe direct live mutation. Generated VS Code watchers synchronously run category-specific `develop` commands for change/create/delete events. Tasks retain an explicit full reconcile and provide all four category-specific develop commands; none emit `--confirm-stopped`.
   - `write-context-descriptor` writes the Tomcat context descriptor.
   - `reload-hint` prints reload instructions.

## Reconciliation safety and Git coverage

Install composed Git hooks without replacing existing custom hook content:

```powershell
auto-ant reconcile --install-hook
```

This adds managed blocks to `post-checkout`, `post-merge`, `post-rewrite`, and `post-commit`. Git resolves the effective hooks directory, so `core.hooksPath` and linked worktrees are honored. Existing content is preserved around the managed marker block, although line endings inside the managed block are normalized by Java. On POSIX-like filesystems installation fails if the hook cannot be made executable; Windows relies on Git's hook launcher semantics. `post-checkout` handles both branch and path checkout. `post-commit` catches ordinary commits and returns through the verified no-op. Duplicate triggers are serialized by the same deployment-target lock; contention/no-op does not itself create a stale marker.

```powershell
auto-ant reconcile
```

Git has no post-restore hook, so arbitrary `git restore` operations cannot be covered completely without a Git wrapper. Auto-ant intentionally does not provide or require a Git wrapper. Run `auto-ant reconcile` after such restores. Hook failures defer to the same `DeploymentState` stale/critical records used by normal reconciliation; no separate contradictory hook marker is created.

The lock identity and lock-file namespace are derived only from the real canonical deployment parent and normalized target name, not the Catalina base, project root, or context path. The lock lives in the controlled `.auto-ant-locks` sibling directory under that canonical parent, never inside the live directory that is renamed. Thus different projects and Catalina bases targeting one external live directory contend on one OS lock. Windows target-name case is normalized where feasible; canonical parent resolution collapses aliases that Java exposes through `toRealPath`. Lock acquisition waits up to 30 seconds by default; use `--lock-wait-seconds 0` for fail-fast. Owner PID, start time, project, and context are written into the lock file for diagnostics. Operation is refused if the lock namespace itself is an exposed symbolic-link alias. Network filesystems that do not provide correct Java/NIO file-lock semantics are unsupported.

Reconcile validates configured input roots before building, builds before touching live, stages on the deployment volume, journals each rename phase with same-directory atomic replacement where supported, renames live to backup, promotes the stage, and rolls back on promotion/lifecycle/readiness failure. Configured input roots, nested inputs, deployment artifacts, and transaction artifacts that Java exposes as links or special files are rejected rather than silently omitted. Legitimate non-linked external source/library roots remain fingerprinted. A successful full snapshot removes files deleted or renamed in source. The last-success manifest stores both the exact resolved-input fingerprint and a live tree digest. “Current” means both source freshness and verified live integrity, not source fingerprint alone. Junction/link guarantees are limited to what the host Java filesystem provider reports; if canonical containment or no-link status cannot be established, the operation is refused.

When Manager lifecycle is configured (`reload.strategy=manager` plus Manager URL/credentials), reconcile accepts only an HTTP success carrying a valid Tomcat text Manager `OK - ...` body. It parses list output by exact context path into `RUNNING`, `STOPPED`, `MISSING`, or `UNKNOWN/ERROR`; missing, malformed, duplicate, HTTP-200 `FAIL`, and unknown states fail closed. A running context must return a successful stop and then be listed as stopped before promotion. Reconcile then performs promote → start → bounded exact-context readiness polling under the same lock. Otherwise it conservatively refuses mutation unless Tomcat has been stopped and `--confirm-stopped` is supplied. It never infers safety from VS Code watcher behavior.

Every transaction move and journal persistence contributes a structured outcome. A failure after filesystem mutation is unsafe until postconditions and the previous live tree digest prove exact restoration. If restoration is incomplete or uncertain, auto-ant does not restart Tomcat: it writes critical state, leaves the context stopped, preserves live/stage/backup/journal paths, and prints exact manual-recovery paths. Journal recovery validates the exact canonical live target, transaction ID, controlled stage/backup names, canonical parent, digests, and no-link artifact types before any mutation; corrupt, stale, mismatched, or neighboring-application paths fail ambiguous with zero mutation. A verified restored filesystem may retain its journal when the final journal write/cleanup failed, so later recovery can finish safely. Rollback restart readiness failure is reported explicitly.

Recovery after any stale/failure report:

```powershell
auto-ant doctor
# stop Tomcat first when Manager lifecycle is unavailable
auto-ant reconcile --confirm-stopped --force
```

For a critical transaction report, do not immediately force reconciliation: keep the target context stopped, inspect the printed live/stage/backup/journal paths, restore exactly one known-good live directory, retain evidence until verified, and then rerun `doctor` and `reconcile`.

## Troubleshooting Ant on Windows

When an Ant install includes `lib/ant-launcher.jar`, `auto-ant` runs Ant through Java directly instead of invoking `ant.bat`. This avoids Windows batch-script dependencies such as `find.exe`, which may be blocked by endpoint security tools on some machines.

If Ant still cannot start, make sure `ANT_HOME` points at the Ant install directory or pass `--ant C:\path\to\ant\bin\ant.bat` when running `auto-ant init`.
