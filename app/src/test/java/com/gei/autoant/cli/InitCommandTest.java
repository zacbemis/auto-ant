package com.gei.autoant.cli;

import com.gei.autoant.prompt.PromptService;
import com.gei.autoant.vscode.VsCodeExtensionChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void fileWatcherOptionGeneratesSettingsAndWarnsWhenExtensionIsMissing() throws IOException {
        createSimpleProject();
        Harness harness = new Harness(tempDir, extensionId -> false);

        int exitCode = harness.command().run(new String[]{"--file-watcher", "--app", "MyApp", "--java", "25"});

        assertEquals(0, exitCode);
        assertTrue(Files.exists(tempDir.resolve(".vscode/settings.json")));
        assertTrue(harness.stdout().contains("VS Code File Watcher extension not detected"));
        assertTrue(harness.stdout().contains(VsCodeExtensionChecker.FILE_WATCHER_EXTENSION_ID));
        assertTrue(harness.stdout().contains("code --install-extension " + VsCodeExtensionChecker.FILE_WATCHER_EXTENSION_ID));
    }

    @Test
    void fileWatcherOptionReportsWhenExtensionIsInstalled() throws IOException {
        createSimpleProject();
        Harness harness = new Harness(tempDir, extensionId -> true);

        int exitCode = harness.command().run(new String[]{"--file-watcher", "--app", "MyApp", "--java", "25"});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("VS Code File Watcher extension detected"));
        assertFalse(harness.stdout().contains("code --install-extension"));
    }

    @Test
    void initWithoutFileWatcherOptionDoesNotGenerateSettingsOrCheckExtension() throws IOException {
        createSimpleProject();
        AtomicBoolean checked = new AtomicBoolean(false);
        Harness harness = new Harness(tempDir, extensionId -> {
            checked.set(true);
            return true;
        });

        int exitCode = harness.command().run(new String[]{"--app", "MyApp", "--java", "25"});

        assertEquals(0, exitCode);
        assertTrue(Files.notExists(tempDir.resolve(".vscode/settings.json")));
        assertFalse(checked.get());
        assertFalse(harness.stdout().contains("VS Code File Watcher extension"));
    }

    private void createSimpleProject() throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve("web/WEB-INF"));
        Files.writeString(tempDir.resolve("web/WEB-INF/web.xml"), "<web-app/>\n");
    }

    private static final class Harness {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private final InitCommand command;

        private Harness(Path projectRoot, VsCodeExtensionChecker extensionChecker) {
            PromptService promptService = (label, detectedValue) -> Optional.empty();
            CommandContext context = new CommandContext(projectRoot, new PrintStream(out), new PrintStream(err), promptService);
            this.command = new InitCommand(context, extensionChecker);
        }

        private InitCommand command() {
            return command;
        }

        private String stdout() {
            return out.toString();
        }
    }
}