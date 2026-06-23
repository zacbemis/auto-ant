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
   auto-ant init --tomcat C:\path\to\tomcat
   ```

   `init` detects the project layout, generates or refreshes `auto-ant.build.xml`, `auto-ant.properties`, `auto-ant.local.properties`, `.vscode/tasks.json`, and `.vscode/settings.json`, then runs `deploy-exploded` unless `--no-deploy` is provided. Existing project `build.xml` files, including NetBeans builds, are left untouched.

4. There are generated VS Code tasks and CLI commands that may be needed. File Watcher will automatically run the Ant script to update the frontend, and Tomcat will auto restart on backend changes:

   ```powershell
   auto-ant run deploy-exploded
   auto-ant run sync-web
   auto-ant reload
   auto-ant vscode
   ```

## Troubleshooting Ant on Windows

When an Ant install includes `lib/ant-launcher.jar`, `auto-ant` runs Ant through Java directly instead of invoking `ant.bat`. This avoids Windows batch-script dependencies such as `find.exe`, which may be blocked by endpoint security tools on some machines.

If Ant still cannot start, make sure `ANT_HOME` points at the Ant install directory or pass `--ant C:\path\to\ant\bin\ant.bat` when running `auto-ant init`.
