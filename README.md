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

2. Add Tomcat to Runtime Server Protocol UI and start the server.

3. From your Ant web project, run `init`:

   ```powershell
   auto-ant init --tomcat C:\path\to\tomcat --jdk C:\path\to\jdk
   ```

   `init` detects the project layout, asks for the JDK home directory if `--jdk` is not provided, generates or refreshes `auto-ant.build.xml`, `auto-ant.user.xml`, `auto-ant.properties`, `auto-ant.local.properties`, `.vscode/tasks.json`, and `.vscode/settings.json`, then runs `deploy-exploded` unless `--no-deploy` is provided. The generated VS Code settings set the selected JDK for Java tooling and integrated-terminal Ant commands. Existing project `build.xml` files, including NetBeans builds, are left untouched.

   `auto-ant.build.xml` and auto-ant VS Code tasks/settings are managed by auto-ant and include comments explaining what not to edit directly. Put custom Ant properties, paths, targets, or helper macros in `auto-ant.user.xml`; auto-ant creates that file if missing and leaves it untouched afterward.

   After upgrading auto-ant, run this from an existing project to refresh generated build files and VS Code configuration without deploying:

   ```powershell
   auto-ant update
   ```

   `update` refreshes `auto-ant.build.xml` with a backup, preserves `auto-ant.user.xml`, appends only missing keys to existing properties files, refreshes `auto-ant:` VS Code tasks while preserving custom tasks, and preserves unrelated VS Code settings.

4. Useful commands:

   ```powershell
   auto-ant doctor
   auto-ant run deploy-exploded
   auto-ant run sync-web
   auto-ant run sync-web-inf
   auto-ant run compile-hot
   auto-ant reload
   auto-ant vscode
   ```

   - `auto-ant doctor` detects the project root, app name, context path, source roots, web root, `WEB-INF`, libraries, servlet namespace, Tomcat, Ant, Java, JDK, reload strategy, and Tomcat Manager URL. It writes nothing. Add `--interactive` to override detected values or `--strict` to fail when required values are missing.
   - `auto-ant init` prompts for detected values, writes the generated auto-ant files, checks the File Watcher extension, and runs `deploy-exploded` unless `--no-deploy` is used.
   - `auto-ant update` refreshes generated auto-ant build/config/VS Code files for the current CLI version. It preserves existing local/shared properties, custom tasks/settings, and does not deploy.
   - `auto-ant vscode` regenerates only `.vscode/tasks.json` and `.vscode/settings.json`, preserving unrelated settings. `refresh-vscode` is an alias.
   - `auto-ant run <target>` runs one or more targets from `auto-ant.build.xml`, not the project `build.xml`.
   - `auto-ant reload` reloads Tomcat using `reload.strategy`: Tomcat Manager, touching deployed `WEB-INF/web.xml`, or printing a manual reload message.
   - `auto-ant branch-refresh` runs the generated `branch-refresh` Ant target, then reloads Tomcat unless `--no-reload` is used.
   - `auto-ant branch-refresh --install-hook` installs/updates `.git/hooks/post-checkout` so branch checkouts automatically run `auto-ant branch-refresh --from-hook`.

   Common generated Ant targets:

   - `clean` deletes build and dist output.
   - `init` creates build output directories.
   - `compile` compiles Java into `WEB-INF/classes`.
   - `copy-web` copies the web root into the exploded build directory.
   - `copy-libs` copies project JARs into `WEB-INF/lib`.
   - `war` builds the WAR file.
   - `clean-build` cleans and builds the WAR.
   - `deploy-exploded` cleans, builds, copies the exploded app to Tomcat `webapps`, and writes the Tomcat context descriptor.
   - `deploy-war` copies the WAR to Tomcat `webapps`.
   - `branch-refresh` runs `deploy-exploded` and `sync-web` after a branch checkout.
   - `sync-web` copies frontend/web files to the deployed exploded app, excluding `WEB-INF` and `META-INF`.
   - `sync-web-inf` copies JSP/view/static resources without replacing classes, libraries, `web.xml`, or `META-INF`.
   - `compile-hot` recompiles Java and replaces deployed `WEB-INF/classes`.
   - `write-context-descriptor` writes the Tomcat context descriptor.
   - `reload-hint` prints reload instructions.

## Automatically recover after changing Git branches

Changing branches can leave Tomcat's exploded deployment and compiled classes out of sync with the checked-out files. `auto-ant` can install a Git `post-checkout` hook that runs after branch checkouts:

```powershell
auto-ant branch-refresh --install-hook
```

After that one-time setup, every branch checkout runs:

```powershell
auto-ant branch-refresh
```

The generated `branch-refresh` Ant target performs a clean exploded redeploy and web sync, then `auto-ant branch-refresh` reloads the configured Tomcat context using your `reload.strategy`. To run the same recovery manually:

```powershell
auto-ant branch-refresh
```

## Troubleshooting Ant on Windows

When an Ant install includes `lib/ant-launcher.jar`, `auto-ant` runs Ant through Java directly instead of invoking `ant.bat`. This avoids Windows batch-script dependencies such as `find.exe`, which may be blocked by endpoint security tools on some machines.

If Ant still cannot start, make sure `ANT_HOME` points at the Ant install directory or pass `--ant C:\path\to\ant\bin\ant.bat` when running `auto-ant init`.
