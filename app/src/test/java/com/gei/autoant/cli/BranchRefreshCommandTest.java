package com.gei.autoant.cli;

import com.gei.autoant.prompt.PromptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BranchRefreshCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void installsPostCheckoutHook() throws IOException {
        Files.createDirectories(tempDir.resolve(".git"));
        Harness harness = new Harness(tempDir);

        int exitCode = harness.command().run(new String[]{"--install-hook"});

        assertEquals(0, exitCode);
        Path hook = tempDir.resolve(".git/hooks/post-checkout");
        assertTrue(Files.exists(hook));
        String hookContent = Files.readString(hook);
        assertTrue(hookContent.contains("AUTO-ANT POST-CHECKOUT HOOK"));
        assertTrue(hookContent.contains("auto-ant branch-refresh --from-hook"));
        assertTrue(harness.stdout().contains("Installed .git/hooks/post-checkout"));
    }

    @Test
    void doesNotOverwriteExistingUserHook() throws IOException {
        Path hooks = tempDir.resolve(".git/hooks");
        Files.createDirectories(hooks);
        Path hook = hooks.resolve("post-checkout");
        Files.writeString(hook, "#!/bin/sh\necho user hook\n");
        Harness harness = new Harness(tempDir);

        int exitCode = harness.command().run(new String[]{"--install-hook"});

        assertEquals(1, exitCode);
        assertEquals("#!/bin/sh\necho user hook\n", Files.readString(hook));
        assertTrue(Files.exists(hooks.resolve("post-checkout.auto-ant-new")));
        assertTrue(harness.stdout().contains("Existing .git/hooks/post-checkout"));
    }

    @Test
    void helpDocumentsHookInstall() {
        Harness harness = new Harness(tempDir);

        int exitCode = harness.command().run(new String[]{"--help"});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("Usage: auto-ant branch-refresh"));
        assertTrue(harness.stdout().contains("--install-hook"));
        assertTrue(harness.stdout().contains("--no-reload"));
    }

    private static final class Harness {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private final BranchRefreshCommand command;

        private Harness(Path projectRoot) {
            PromptService promptService = (label, detectedValue) -> Optional.empty();
            CommandContext context = new CommandContext(projectRoot, new PrintStream(out), new PrintStream(err), promptService);
            this.command = new BranchRefreshCommand(context);
        }

        private BranchRefreshCommand command() {
            return command;
        }

        private String stdout() {
            return out.toString();
        }
    }
}