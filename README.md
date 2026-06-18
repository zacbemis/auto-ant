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

1. Clone and build `auto-ant`:

   ```powershell
   git clone https://github.com/zacbemis/auto-ant.git
   cd auto-ant
   .\gradlew.bat installDist
   ```

2. Add Tomcat to Runtime Server Protocol UI and start the server.

3. From your Ant web project, run `init` using the generated CLI script:

   ```powershell
   C:\path\to\auto-ant\app\build\install\auto-ant\bin\auto-ant.bat init --tomcat C:\path\to\tomcat
   ```

   `init` detects the project layout, generates or refreshes `auto-ant.build.xml`, `auto-ant.properties`, `auto-ant.local.properties`, `.vscode/tasks.json`, and `.vscode/settings.json`, then runs `deploy-exploded` unless `--no-deploy` is provided. Existing project `build.xml` files, including NetBeans builds, are left untouched.

4. There are generated VS Code tasks and CLI commands that may be needed File Watcher will automatically run the ant script to update the frontend and tomcatt will auto restart on backend changes:

   ```powershell
   auto-ant run deploy-exploded
   auto-ant run sync-web
   auto-ant reload
   auto-ant vscode
   ```
