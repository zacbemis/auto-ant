## Final development plan for `auto-ant`

`auto-ant` should be a Java CLI that turns an existing legacy Tomcat web app source repo into a clean Ant-based project that works well from VS Code.

The core workflow should become:

```bash
auto-ant doctor
auto-ant init
ant clean-build
ant deploy-exploded
auto-ant watch
```

The tool should not try to recreate a full NetBeans `nbproject/` setup. It should create a plain, understandable Ant project with VS Code integration.

---

# 1. Project goals

`auto-ant` should do four main things:

```text
doctor
  Detect the project layout, Tomcat setup, Java version, servlet style, and likely build settings.

init
  Generate build.xml, auto-ant.properties, auto-ant.local.properties, .vscode/tasks.json, and optional .gitignore entries.

run
  Provide Java wrappers around Ant commands through AntRunner.

watch
  Watch source/frontend/backend files and run the right Ant target automatically.
```

The first production-quality milestone is:

```bash
auto-ant doctor
auto-ant init
ant clean-build
ant deploy-exploded
```

The second production-quality milestone is:

```bash
auto-ant watch
```

---

# 2. Recommended repository structure

Since you want to use Gradle, structure the project like this:

```text
auto-ant/
  build.gradle
  settings.gradle
  gradlew
  gradlew.bat
  gradle/
    wrapper/

  src/
    main/
      java/
        com/
          gei/
            autoant/
              Main.java

              cli/
                CommandRouter.java
                DoctorCommand.java
                InitCommand.java
                WatchCommand.java
                ReloadCommand.java
                RunCommand.java

              detect/
                ProjectDetector.java
                SourceRootDetector.java
                WebRootDetector.java
                LibraryDetector.java
                ServletNamespaceDetector.java
                TomcatDetector.java
                AntDetector.java
                JavaDetector.java

              model/
                ProjectModel.java
                DetectionResult.java
                SourceRoot.java
                WebRoot.java
                LibraryRoot.java
                ServletNamespace.java
                ReloadStrategy.java
                ToolConfig.java

              prompt/
                PromptService.java
                ConsolePromptService.java
                NonInteractiveOptions.java

              generate/
                InitGenerator.java
                BuildXmlWriter.java
                PropertiesWriter.java
                VsCodeTasksWriter.java
                GitignoreWriter.java
                TemplateRenderer.java

              run/
                AntRunner.java
                ProcessRunner.java
                CommandResult.java

              watch/
                ProjectWatcher.java
                RecursiveWatchRegistrar.java
                FileClassifier.java
                Debouncer.java
                ChangeBatch.java

              tomcat/
                TomcatManagerClient.java
                ReloadService.java

              util/
                PathUtils.java
                PropertiesUtils.java
                JsonUtils.java
                Console.java

    main/
      resources/
        templates/
          build.xml.template
          auto-ant.properties.template
          auto-ant.local.properties.template
          tasks.json.template

    test/
      java/
        com/
          gei/
            autoant/
              detect/
              generate/
              watch/
              run/

    test/
      resources/
        samples/
          simple-src-web/
          webcontent-layout/
          maven-style-layout/
          multiple-src-roots/
          missing-webxml/
          javax-servlet/
          jakarta-servlet/
          nested-webinf/
```

The package name can change, but I would avoid baking in too much company-specific naming unless this is intended to stay internal.

---

# 3. CLI command design

The initial command set should be:

```bash
auto-ant doctor
auto-ant init
auto-ant run clean-build
auto-ant run deploy-exploded
auto-ant run sync-web
auto-ant watch
auto-ant reload
```

Later, you can add command aliases:

```bash
auto-ant clean-build
auto-ant deploy
auto-ant sync-web
```

But the MVP can route everything through `run`.

---

# 4. `doctor` plan

`doctor` is the most important foundation. It should detect everything but write nothing.

## 4.1 Behavior

Run:

```bash
auto-ant doctor
```

The command should print detected values and mark each one as:

```text
detected confidently
detected with warning
not detected
user override available
```

Example output:

```text
auto-ant doctor

Project root:
  C:/repos/MyApp

App name:
  MyApp

Java source roots:
  src

Web root:
  web

WEB-INF:
  web/WEB-INF

Libraries:
  web/WEB-INF/lib
  lib

Servlet namespace:
  javax.servlet

Recommended Tomcat:
  Tomcat 9.x

Tomcat home:
  Not detected

Ant:
  Found: C:/tools/apache-ant/bin/ant.bat

Java:
  25
```

## 4.2 Defaults versus user input

`doctor` should support both modes:

```bash
auto-ant doctor
```

Uses automatic detection and prints results.

```bash
auto-ant doctor --interactive
```

Detects defaults, then asks the user to accept or override each major value.

Example:

```text
Detected source root: src
Use this? [Y/n]
```

If user says no:

```text
Enter source root path:
```

Same for:

```text
app.name
context.path
java.release
tomcat.home
src.dirs
web.dir
webinf.dir
lib.dirs
reload.strategy
tomcat.manager.url
```

## 4.3 Non-interactive overrides

Also support explicit overrides:

```bash
auto-ant doctor ^
  --app MyApp ^
  --src src ^
  --web WebContent ^
  --tomcat C:/apache-tomcat-9.0.118 ^
  --java 25
```

This matters for future automation and tests.

## 4.4 Detection rules

Source directory candidates:

```text
src/main/java
src/java
src
source
Source Packages
```

Web root candidates:

```text
src/main/webapp
web
WebContent
WebRoot
public
```

Best signal for web root:

```text
WEB-INF/
WEB-INF/web.xml
index.jsp
index.html
```

Library candidates:

```text
lib
WEB-INF/lib
web/WEB-INF/lib
WebContent/WEB-INF/lib
src/main/webapp/WEB-INF/lib
```

Servlet namespace scan:

```text
javax.servlet   -> Tomcat 9 recommended
jakarta.servlet -> Tomcat 10/11 recommended
both found      -> warning
neither found   -> unknown
```

Tomcat home detection:

```text
CATALINA_HOME
CATALINA_BASE
TOMCAT_HOME
common known local paths
manual input
```

Ant detection:

```text
ant on PATH
ANT_HOME
manual input later if needed
```

---

# 5. Doctor testing plan

The `doctor` testing should be extensive because every later feature depends on it.

Create sample projects under:

```text
src/test/resources/samples/
```

Use fixtures like:

```text
simple-src-web/
  src/
  web/
    WEB-INF/
      web.xml

webcontent-layout/
  src/
  WebContent/
    WEB-INF/
      web.xml

maven-style-layout/
  src/main/java/
  src/main/webapp/
    WEB-INF/
      web.xml

multiple-src-roots/
  src/
  generated-src/
  web/

javax-servlet/
  src/
    com/example/MyServlet.java  // imports javax.servlet
  web/WEB-INF/web.xml

jakarta-servlet/
  src/
    com/example/MyServlet.java  // imports jakarta.servlet
  web/WEB-INF/web.xml

missing-webxml/
  src/
  web/
    index.jsp

libs-in-webinf/
  src/
  web/
    WEB-INF/
      lib/
        example.jar

legacy-weird-layout/
  JavaSource/
  WebRoot/
```

Test cases should verify:

```text
correct source root detection
correct web root detection
correct WEB-INF detection
correct library folder detection
correct servlet namespace detection
correct app name inference
correct warnings when ambiguous
correct behavior when missing web.xml
correct behavior when multiple possible web roots exist
correct user overrides win over detection
```

Important principle:

```text
Detection should recommend.
User input should decide.
```

---

# 6. `init` plan

`init` turns the doctor result into actual files.

Run:

```bash
auto-ant init
```

or:

```bash
auto-ant init --interactive
```

or:

```bash
auto-ant init --app MyApp --src src --web WebContent --tomcat C:/apache-tomcat-9.0.118
```

## 6.1 Files generated

`init` should generate:

```text
build.xml
auto-ant.properties
auto-ant.local.properties
.vscode/tasks.json
.gitignore additions
```

Potentially later:

```text
.vscode/settings.json
tools/auto-ant.jar
```

For the first version, I would not copy the JAR into the project automatically unless you decide you want each repo to be self-contained.

## 6.2 Shared properties

Generate:

```properties
# auto-ant.properties

app.name=MyApp
context.path=/MyApp
java.release=25

src.dirs=src
web.dir=web
webinf.dir=web/WEB-INF
lib.dirs=web/WEB-INF/lib,lib

build.dir=build
build.web.dir=build/web
classes.dir=build/web/WEB-INF/classes
dist.dir=dist
war.name=MyApp.war

reload.strategy=manager
```

## 6.3 Local properties

Generate:

```properties
# auto-ant.local.properties

tomcat.home=C:/apache-tomcat-9.0.118
catalina.base=C:/apache-tomcat-9.0.118

tomcat.manager.url=http://localhost:8080/manager/text
tomcat.manager.user=
tomcat.manager.password=
```

`auto-ant.local.properties` should be ignored by Git.

## 6.4 Safe write behavior

`init` should not destructively overwrite existing files.

Behavior:

```text
If build.xml does not exist:
  create build.xml

If build.xml exists:
  create build.xml.auto-ant-new
  warn user

If auto-ant.properties exists:
  create auto-ant.properties.auto-ant-new

If .vscode/tasks.json exists:
  create .vscode/tasks.auto-ant-new.json

If .gitignore exists:
  append only missing auto-ant entries

If .gitignore does not exist:
  create it
```

This is important. A build tool should never surprise users by destroying existing project setup.

---

# 7. Generated Ant targets

The generated `build.xml` should expose a small set of predictable targets.

```text
clean
init
compile
copy-web
copy-libs
war
clean-build
deploy-exploded
deploy-war
sync-web
compile-hot
reload-hint
```

## 7.1 Target behavior

```text
clean
  Delete build/ and dist/.

init
  Create required build directories.

compile
  Compile Java source into build/web/WEB-INF/classes.

copy-web
  Copy web root into build/web.

copy-libs
  Copy project library JARs into build/web/WEB-INF/lib.

war
  Create dist/AppName.war.

clean-build
  Run clean, then war.

deploy-exploded
  Build exploded app and copy to Tomcat webapps/AppName.

deploy-war
  Build WAR and copy to Tomcat webapps.

sync-web
  Copy frontend/web files from web.dir to deployed exploded app.

compile-hot
  Compile Java and copy changed classes to deployed WEB-INF/classes.

reload-hint
  Print instructions for Tomcat Manager reload if not configured.
```

## 7.2 Immediate user value

After `init`, the user should be able to run:

```bash
ant clean-build
```

Then:

```bash
ant deploy-exploded
```

Then visit:

```text
http://localhost:8080/MyApp
```

That is the main acceptance test for the first version.

---

# 8. Init testing plan

Once `doctor` is stable, `init` should be locked down with golden-file tests.

For each sample project:

```text
run init into a temp directory
compare generated build.xml to expected output
compare generated auto-ant.properties to expected output
compare generated auto-ant.local.properties to expected output
compare generated .vscode/tasks.json to expected output
verify existing files are not overwritten
verify .gitignore entries are added once only
```

Golden-file tests should live under something like:

```text
src/test/resources/expected/
```

For example:

```text
expected/
  simple-src-web/
    build.xml
    auto-ant.properties
    auto-ant.local.properties
    tasks.json
```

This will make refactoring much safer.

---

# 9. VS Code task integration plan

Generated `.vscode/tasks.json` should include:

```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "auto-ant: clean build",
      "type": "shell",
      "command": "ant clean-build",
      "group": "build",
      "problemMatcher": "$javac"
    },
    {
      "label": "auto-ant: deploy exploded",
      "type": "shell",
      "command": "ant deploy-exploded",
      "problemMatcher": "$javac"
    },
    {
      "label": "auto-ant: sync web",
      "type": "shell",
      "command": "ant sync-web",
      "problemMatcher": []
    },
    {
      "label": "auto-ant: compile hot",
      "type": "shell",
      "command": "ant compile-hot",
      "problemMatcher": "$javac"
    },
    {
      "label": "auto-ant: watch",
      "type": "shell",
      "command": "auto-ant watch",
      "isBackground": true,
      "problemMatcher": "$javac",
      "presentation": {
        "reveal": "always",
        "panel": "dedicated"
      }
    }
  ]
}
```

Later, if you choose to copy the built JAR into each project, the watch command can become:

```json
"command": "java -jar tools/auto-ant.jar watch"
```

But I would start with:

```json
"command": "auto-ant watch"
```

because it keeps generated projects cleaner.

---

# 10. `AntRunner` plan

`AntRunner` should be the single place where `auto-ant` runs Ant.

It should support:

```java
runTarget("clean-build")
runTarget("deploy-exploded")
runTarget("sync-web")
runTarget("compile-hot")
```

Internally it should use `ProcessBuilder`.

Responsibilities:

```text
Set working directory to project root.
Find ant or ant.bat.
Pass target names safely.
Stream stdout/stderr to console.
Return exit code.
Handle Ctrl+C cleanly.
```

Command examples:

```bash
auto-ant run clean-build
auto-ant run deploy-exploded
auto-ant run sync-web
```

The watcher should call `AntRunner` instead of shelling out manually.

---

# 11. Watcher plan

The watcher comes after generation and AntRunner.

Run:

```bash
auto-ant watch
```

## 11.1 Watch behavior

Frontend changes:

```text
.jsp
.jspf
.html
.htm
.css
.js
.ts
.png
.jpg
.jpeg
.gif
.svg
.webp
.ico
.woff
.woff2
```

Action:

```bash
ant sync-web
```

Backend changes:

```text
.java
```

Action:

```bash
ant compile-hot
auto-ant reload
```

Config or library changes:

```text
WEB-INF/web.xml
context.xml
*.properties
*.xml
*.jar
```

Action:

```bash
ant deploy-exploded
auto-ant reload
```

## 11.2 Debouncing

The watcher should debounce events.

Example:

```text
User saves one Java file.
Editor writes temp file.
Editor replaces file.
Compiler maybe touches files.

auto-ant waits 750ms.
Groups all changes.
Runs one command.
```

Start with a debounce delay around:

```text
750ms to 1000ms
```

Make it configurable later:

```properties
watch.debounce.ms=750
```

## 11.3 Watcher output

Example:

```text
auto-ant watch

Watching:
  src
  web

Changed:
  web/index.jsp

Detected frontend change.
Running: ant sync-web
Done.
```

For Java:

```text
Changed:
  src/com/example/UserServlet.java

Detected backend change.
Running: ant compile-hot
Reloading Tomcat context /MyApp
Done.
```

---

# 12. Reload plan

Support reload as a standalone command:

```bash
auto-ant reload
```

## 12.1 Reload strategies

Use:

```properties
reload.strategy=manager
```

Supported strategies:

```text
manager
  Call Tomcat Manager text API.

touch-webxml
  Touch deployed WEB-INF/web.xml to trigger reload behavior.

none
  Print a message telling user to reload/restart manually.
```

## 12.2 Manager reload

Use properties:

```properties
tomcat.manager.url=http://localhost:8080/manager/text
tomcat.manager.user=dev
tomcat.manager.password=dev-password
context.path=/MyApp
```

Call:

```text
GET /manager/text/reload?path=/MyApp
```

This should only use credentials from `auto-ant.local.properties`.

Never write credentials to `auto-ant.properties`.

---

# 13. Testing strategy

Testing should be a first-class part of the project.

## 13.1 Unit tests

Use unit tests for:

```text
ProjectDetector
SourceRootDetector
WebRootDetector
LibraryDetector
ServletNamespaceDetector
TomcatDetector
PropertiesWriter
VsCodeTasksWriter
FileClassifier
Debouncer
```

## 13.2 Golden-file tests

Use golden-file tests for generation:

```text
Given sample project X
When init runs
Then generated build.xml matches expected build.xml
And generated tasks.json matches expected tasks.json
And generated properties match expected properties
```

## 13.3 Integration tests

Use integration-style tests for:

```text
auto-ant doctor on sample projects
auto-ant init into temp directories
AntRunner command construction
safe overwrite behavior
```

For true Tomcat deployment tests, I would wait. Those are heavier and can come later.

## 13.4 Manual acceptance tests

For your actual repo:

```text
1. Run auto-ant doctor.
2. Confirm detected values.
3. Run auto-ant init.
4. Run ant clean-build.
5. Confirm dist/AppName.war exists.
6. Run ant deploy-exploded.
7. Confirm app loads in browser.
8. Edit JSP/CSS/JS.
9. Run ant sync-web.
10. Confirm browser sees change.
11. Run auto-ant watch.
12. Edit JSP/CSS/JS and confirm auto-sync.
13. Edit Java and confirm compile-hot/reload.
```

---

# 14. Build plan with Gradle

Use Gradle to build `auto-ant` itself.

Gradle should produce:

```text
build/libs/auto-ant.jar
```

and ideally a runnable distribution:

```text
build/install/auto-ant/bin/auto-ant
build/install/auto-ant/bin/auto-ant.bat
```

Use the Gradle `application` plugin so you can run:

```bash
./gradlew run --args="doctor"
```

and install locally with:

```bash
./gradlew installDist
```

The executable command would eventually be:

```bash
auto-ant doctor
```

instead of:

```bash
java -jar auto-ant.jar doctor
```

## Suggested Gradle tasks

```bash
./gradlew test
./gradlew run --args="doctor"
./gradlew run --args="init --interactive"
./gradlew installDist
./gradlew distZip
```

## Suggested dependencies

For MVP, keep dependencies minimal.

Good starting point:

```text
JUnit for tests
```

Maybe later:

```text
Picocli for command parsing
Jackson or Gson for JSON generation
```

But for the first implementation, you can manually generate JSON and parse simple args yourself. Once the command surface grows, Picocli becomes worthwhile.

---

# 15. Development milestones

## Milestone 1: Gradle skeleton

Deliverables:

```text
Gradle Java application project
Main class
Basic command routing
Tests running
```

Commands:

```bash
./gradlew test
./gradlew run --args="doctor"
```

Acceptance:

```text
auto-ant starts and prints help.
```

---

## Milestone 2: `doctor` detection

Deliverables:

```text
ProjectDetector
SourceRootDetector
WebRootDetector
LibraryDetector
ServletNamespaceDetector
DoctorCommand
```

Acceptance:

```text
doctor correctly detects your real repo.
doctor correctly detects sample projects.
doctor allows defaults or user overrides.
```

---

## Milestone 3: `init` generation

Deliverables:

```text
auto-ant.properties
auto-ant.local.properties
build.xml
.vscode/tasks.json
.gitignore updates
safe write behavior
```

Acceptance:

```text
auto-ant init creates the expected files.
Generated Ant targets appear in Ant Target Runner.
ant clean-build works.
```

---

## Milestone 4: AntRunner

Deliverables:

```text
AntRunner
auto-ant run clean-build
auto-ant run deploy-exploded
auto-ant run sync-web
```

Acceptance:

```text
auto-ant can run generated Ant targets successfully.
```

---

## Milestone 5: frontend watcher

Deliverables:

```text
WatchService integration
recursive directory watch
debounce
frontend file classification
sync-web on change
```

Acceptance:

```text
Saving JSP/CSS/JS triggers ant sync-web once.
No Tomcat reload occurs for frontend-only changes.
```

---

## Milestone 6: backend watcher and reload

Deliverables:

```text
backend file classification
compile-hot on Java changes
deploy-exploded on config/JAR changes
Tomcat Manager reload
touch-webxml fallback
```

Acceptance:

```text
Saving Java file compiles classes and reloads app context.
Changing web.xml redeploys and reloads.
```

---

## Milestone 7: polish

Deliverables:

```text
Better help output
Better error messages
Interactive prompts
Non-interactive flags
README
Sample projects
Troubleshooting guide
```

Acceptance:

```text
A new user can run doctor -> init -> clean-build -> deploy-exploded without reading source code.
```

---

# 16. Is Gradle a good idea for this project?

Yes — **Gradle is a good idea for building `auto-ant` itself**.

It gives you:

```text
easy test setup
easy runnable application packaging
easy Windows .bat and Unix script generation through installDist
easy dependency management if you later add Picocli or JSON libraries
easy CI setup
```

The only downside is conceptual irony: you are using Gradle to build a tool that generates Ant projects. That is fine. The generated user projects are Ant projects because they are legacy Tomcat apps. The tool itself is a modern Java application, and Gradle is a good fit for that.

My recommendation:

```text
Use Gradle for auto-ant.
Generate Ant for target projects.
Do not require Gradle in the generated projects.
```

That gives you the best of both worlds: modern development for the tool, simple Ant compatibility for legacy apps.
