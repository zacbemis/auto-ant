package com.gei.autoant.cli;

import com.gei.autoant.prompt.PromptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRouterTest {
    @TempDir
    Path tempDir;

    @Test
    void printsHelpWhenNoCommandIsProvided() {
        Harness harness = new Harness(tempDir);

        int exitCode = harness.router().run(new String[]{});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("auto-ant branch-refresh"));
        assertTrue(harness.stdout().contains("auto-ant doctor"));
        assertTrue(harness.stdout().contains("auto-ant init"));
        assertFalse(harness.stdout().contains("auto-ant watch"));
    }

    @Test
    void unknownCommandReturnsUsageError() {
        Harness harness = new Harness(tempDir);

        int exitCode = harness.router().run(new String[]{"unknown"});

        assertEquals(2, exitCode);
        assertTrue(harness.stderr().contains("Unknown command: unknown"));
        assertTrue(harness.stdout().contains("Usage:"));
    }

    @Test
    void commandHelpDoesNotRequireProjectDetection() {
        Harness harness = new Harness(tempDir);

        int exitCode = harness.router().run(new String[]{"doctor", "--help"});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("Usage: auto-ant doctor"));
    }

    @Test
    void doctorDoesNotFailByDefaultWhenUserChoicesAreRequired() {
        Harness harness = new Harness(tempDir);

        int exitCode = harness.router().run(new String[]{"doctor"});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("user input required"));
    }

    @Test
    void doctorStrictFailsWhenUserChoicesAreRequired() {
        Harness harness = new Harness(tempDir);

        int exitCode = harness.router().run(new String[]{"doctor", "--strict"});

        assertEquals(1, exitCode);
        assertTrue(harness.stdout().contains("user input required"));
    }

    @Test
    void watchCommandIsNoLongerRouted() {
        Harness harness = new Harness(tempDir);

        int exitCode = harness.router().run(new String[]{"watch"});

        assertEquals(2, exitCode);
        assertTrue(harness.stderr().contains("Unknown command: watch"));
        assertFalse(harness.stdout().contains("auto-ant watch"));
    }

    private static final class Harness {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private final CommandRouter router;

        private Harness(Path projectRoot) {
            PromptService promptService = (label, detectedValue) -> Optional.empty();
            CommandContext context = new CommandContext(projectRoot, new PrintStream(out), new PrintStream(err), promptService);
            this.router = new CommandRouter(context);
        }

        private CommandRouter router() {
            return router;
        }

        private String stdout() {
            return out.toString();
        }

        private String stderr() {
            return err.toString();
        }
    }
}