package com.gei.autoant.watch;

import com.gei.autoant.run.CommandResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectWatcherTest {
    @TempDir
    Path tempDir;

    @Test
    void frontendBatchRunsSyncWebOnceWithoutReload() throws IOException, InterruptedException {
        Harness harness = new Harness(tempDir);

        harness.watcher().handleBatch(ChangeBatch.of(
                tempDir.resolve("web/index.jsp"),
                tempDir.resolve("web/assets/app.css")
        ));

        assertEquals(List.of("sync-web"), harness.targets());
        assertTrue(harness.stdout().contains("Detected frontend change."));
        assertTrue(harness.stdout().contains("Running: ant sync-web"));
        assertFalse(harness.stdout().toLowerCase().contains("reload"));
    }

    @Test
    void backendBatchDoesNotRunFrontendTarget() throws IOException, InterruptedException {
        Harness harness = new Harness(tempDir);

        harness.watcher().handleBatch(ChangeBatch.of(tempDir.resolve("src/com/example/UserServlet.java")));

        assertTrue(harness.targets().isEmpty());
        assertTrue(harness.stdout().contains("Detected backend change."));
    }

    private static final class Harness {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private final List<String> targets = new ArrayList<>();
        private final ProjectWatcher watcher;

        private Harness(Path projectRoot) {
            this.watcher = new ProjectWatcher(
                    projectRoot,
                    List.of(projectRoot.resolve("src"), projectRoot.resolve("web")),
                    Duration.ZERO,
                    target -> {
                        targets.add(target);
                        return new CommandResult(0);
                    },
                    new PrintStream(out),
                    new PrintStream(err)
            );
        }

        private ProjectWatcher watcher() {
            return watcher;
        }

        private List<String> targets() {
            return targets;
        }

        private String stdout() {
            return out.toString();
        }
    }
}