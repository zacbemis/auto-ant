package com.gei.autoant.cli;

import com.gei.autoant.prompt.PromptService;
import com.gei.autoant.run.CommandResult;
import com.gei.autoant.vscode.VsCodeExtensionChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void initAlwaysGeneratesSettingsAndWarnsWhenExtensionIsMissing() throws IOException {
        createSimpleProject();
        Harness harness = new Harness(tempDir, extensionId -> false);

        int exitCode = harness.command().run(new String[]{"--app", "MyApp", "--java", "25"});

        assertEquals(0, exitCode);
        assertTrue(Files.exists(tempDir.resolve(".vscode/settings.json")));
        assertTrue(harness.stdout().contains("VS Code File Watcher extension not detected"));
        assertTrue(harness.stdout().contains(VsCodeExtensionChecker.FILE_WATCHER_EXTENSION_ID));
        assertTrue(harness.stdout().contains("code --install-extension " + VsCodeExtensionChecker.FILE_WATCHER_EXTENSION_ID));
        assertEquals(List.of("deploy-exploded"), harness.deployedTargets());
        assertEquals(List.of(tempDir.toAbsolutePath().normalize()), harness.deployedRoots());
        assertEquals(List.of(tempDir.resolve("build.xml").toAbsolutePath().normalize()), harness.deployedBuildFiles());
    }

    @Test
    void initAlwaysReportsWhenFileWatcherExtensionIsInstalled() throws IOException {
        createSimpleProject();
        Harness harness = new Harness(tempDir, extensionId -> true);

        int exitCode = harness.command().run(new String[]{"--app", "MyApp", "--java", "25"});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("VS Code File Watcher extension detected"));
        assertFalse(harness.stdout().contains("code --install-extension"));
        assertTrue(harness.stdout().contains("Running initial deploy-exploded"));
    }

    @Test
    void initAlwaysChecksFileWatcherExtension() throws IOException {
        createSimpleProject();
        AtomicBoolean checked = new AtomicBoolean(false);
        Harness harness = new Harness(tempDir, extensionId -> {
            checked.set(true);
            return true;
        });

        int exitCode = harness.command().run(new String[]{"--app", "MyApp", "--java", "25"});

        assertEquals(0, exitCode);
        assertTrue(Files.exists(tempDir.resolve(".vscode/settings.json")));
        assertTrue(checked.get());
        assertTrue(harness.stdout().contains("VS Code File Watcher extension"));
    }

    @Test
    void interactiveInitPromptsForOverridesBeforeGeneratingFiles() throws IOException {
        createSimpleProject();
        Harness harness = new Harness(tempDir, extensionId -> true, (label, detectedValue) -> Optional.ofNullable(Map.of(
                "java.release", "17",
                "tomcat.home", "tomcat",
                "lib.dirs", "web/WEB-INF/lib"
        ).get(label)));

        int exitCode = harness.command().run(new String[]{});

        assertEquals(0, exitCode);
        String sharedProperties = Files.readString(tempDir.resolve("auto-ant.properties"));
        String localProperties = Files.readString(tempDir.resolve("auto-ant.local.properties"));
        assertTrue(sharedProperties.contains("java.release=17"));
        assertTrue(sharedProperties.contains("lib.dirs=web/WEB-INF/lib"));
        assertTrue(localProperties.contains("tomcat.home=" + portable(tempDir.resolve("tomcat").toAbsolutePath().normalize())));
    }

    @Test
    void initReturnsDeployExplodedFailureCode() throws IOException {
        createSimpleProject();
        Harness harness = new Harness(tempDir, extensionId -> true, (label, detectedValue) -> Optional.empty(), 7);

        int exitCode = harness.command().run(new String[]{"--app", "MyApp", "--java", "25"});

        assertEquals(7, exitCode);
        assertEquals(List.of("deploy-exploded"), harness.deployedTargets());
        assertTrue(harness.stderr().contains("deploy-exploded failed with exit code 7"));
    }

    @Test
    void initUsesAutoAntBuildFileWhenExistingBuildXmlBelongsToNetBeans() throws IOException {
        createSimpleProject();
        Files.writeString(tempDir.resolve("build.xml"), "<project name=\"NetBeans\"/>\n");
        Harness harness = new Harness(tempDir, extensionId -> true);

        int exitCode = harness.command().run(new String[]{"--app", "MyApp", "--java", "25"});

        assertEquals(0, exitCode);
        assertEquals("<project name=\"NetBeans\"/>\n", Files.readString(tempDir.resolve("build.xml")));
        assertTrue(Files.exists(tempDir.resolve("auto-ant.build.xml")));
        assertEquals(List.of(tempDir.resolve("auto-ant.build.xml").toAbsolutePath().normalize()), harness.deployedBuildFiles());
        assertTrue(Files.readString(tempDir.resolve(".vscode/tasks.json")).contains("auto-ant.build.xml"));
        assertTrue(Files.readString(tempDir.resolve(".vscode/settings.json")).contains("auto-ant.build.xml"));
        assertTrue(harness.stdout().contains("Running initial deploy-exploded using auto-ant.build.xml"));
    }

    @Test
    void initHelpDocumentsAlwaysInteractiveSettingsAndOverrideOptions() {
        Harness harness = new Harness(tempDir, extensionId -> true);

        int exitCode = harness.command().run(new String[]{"--help"});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("Prompts to accept or override"));
        assertTrue(harness.stdout().contains("VS Code tasks/settings"));
        assertTrue(harness.stdout().contains("deploy-exploded"));
        assertTrue(harness.stdout().contains("--java <release>"));
        assertTrue(harness.stdout().contains("--tomcat <path>"));
        assertFalse(harness.stdout().contains("--file-watcher"));
    }

    private void createSimpleProject() throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve("web/WEB-INF"));
        Files.writeString(tempDir.resolve("web/WEB-INF/web.xml"), "<web-app/>\n");
    }

    private static String portable(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static final class Harness {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private final List<String> deployedTargets = new ArrayList<>();
        private final List<Path> deployedRoots = new ArrayList<>();
        private final List<Path> deployedBuildFiles = new ArrayList<>();
        private final InitCommand command;

        private Harness(Path projectRoot, VsCodeExtensionChecker extensionChecker) {
            this(projectRoot, extensionChecker, (label, detectedValue) -> Optional.empty());
        }

        private Harness(Path projectRoot, VsCodeExtensionChecker extensionChecker, PromptService promptService) {
            this(projectRoot, extensionChecker, promptService, 0);
        }

        private Harness(Path projectRoot, VsCodeExtensionChecker extensionChecker, PromptService promptService, int deployExitCode) {
            CommandContext context = new CommandContext(projectRoot, new PrintStream(out), new PrintStream(err), promptService);
            this.command = new InitCommand(context, extensionChecker, (deployRoot, buildFile, target) -> {
                deployedRoots.add(deployRoot.toAbsolutePath().normalize());
                deployedBuildFiles.add(buildFile.toAbsolutePath().normalize());
                deployedTargets.add(target);
                return new CommandResult(deployExitCode);
            });
        }

        private InitCommand command() {
            return command;
        }

        private String stdout() {
            return out.toString();
        }

        private String stderr() {
            return err.toString();
        }

        private List<String> deployedTargets() {
            return deployedTargets;
        }

        private List<Path> deployedRoots() {
            return deployedRoots;
        }

        private List<Path> deployedBuildFiles() {
            return deployedBuildFiles;
        }
    }
}