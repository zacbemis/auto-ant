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
    void installsPostCheckoutHook() throws Exception {
        git("init");
        Harness harness = new Harness(tempDir);

        int exitCode = harness.command().run(new String[]{"--install-hook"});

        assertEquals(0, exitCode);
        Path hook = tempDir.resolve(".git/hooks/post-checkout");
        assertTrue(Files.exists(hook));
        String hookContent = Files.readString(hook);
        assertTrue(hookContent.contains("AUTO-ANT RECONCILE"));
        assertTrue(hookContent.contains("auto-ant reconcile --from-hook"));
        assertTrue(hookContent.contains("branch ($3=1) and path ($3=0)"));
        assertTrue(Files.exists(tempDir.resolve(".git/hooks/post-merge")));
        assertTrue(Files.exists(tempDir.resolve(".git/hooks/post-rewrite")));
        assertTrue(Files.exists(tempDir.resolve(".git/hooks/post-commit")));
        assertTrue(harness.stdout().contains("Installed/composed"));
    }

    @Test
    void doesNotOverwriteExistingUserHook() throws Exception {
        git("init");
        Path hooks = tempDir.resolve(".git/hooks");
        Files.createDirectories(hooks);
        Path hook = hooks.resolve("post-checkout");
        Files.writeString(hook, "#!/bin/sh\necho user hook\n");
        Harness harness = new Harness(tempDir);

        int exitCode = harness.command().run(new String[]{"--install-hook"});

        assertEquals(0, exitCode);
        assertTrue(Files.readString(hook).contains("echo user hook"));
        assertTrue(Files.readString(hook).contains("AUTO-ANT RECONCILE"));
    }

    @Test
    void honorsCoreHooksPath() throws Exception {
        git("init");
        git("config", "core.hooksPath", ".custom-hooks");
        Harness harness = new Harness(tempDir);
        assertEquals(0, harness.command().run(new String[]{"--install-hook"}));
        assertTrue(Files.exists(tempDir.resolve(".custom-hooks/post-checkout")));
        assertTrue(Files.notExists(tempDir.resolve(".git/hooks/post-checkout")));
    }

    @Test
    void installedPostCommitHookInvokesControlledFakeAutoAnt() throws Exception {
        git("init");
        Harness harness = new Harness(tempDir);
        assertEquals(0, harness.command().run(new String[]{"--install-hook"}));

        Path bin = tempDir.resolve("fake-bin");
        Files.createDirectories(bin);
        Path invocation = tempDir.resolve("hook-invocation.txt");
        Path fake = bin.resolve("auto-ant");
        Files.writeString(fake, "#!/bin/sh\nprintf '%s\\n' \"$*\" > '" + invocation.toString().replace('\\', '/') + "'\n");
        if (!isWindows()) assertTrue(fake.toFile().setExecutable(true, false));
        Path installedHook = tempDir.resolve(".git/hooks/post-commit");
        String hookContent = Files.readString(installedHook);
        int firstNewline = hookContent.indexOf('\n') + 1;
        Files.writeString(installedHook, hookContent.substring(0, firstNewline)
                + "PATH=\"$PWD/fake-bin:$PATH\"\nexport PATH\n" + hookContent.substring(firstNewline));
        git("config", "user.email", "auto-ant@example.invalid");
        git("config", "user.name", "Auto Ant Test");
        Files.writeString(tempDir.resolve("tracked.txt"), "content");
        git("add", "tracked.txt");
        ProcessBuilder commit = new ProcessBuilder("git", "commit", "-m", "trigger hook").directory(tempDir.toFile());
        Process process = commit.redirectErrorStream(true).start();
        assertEquals(0, process.waitFor(), new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(Files.exists(invocation));
        assertTrue(Files.readString(invocation).contains("reconcile --from-hook"));
    }

    private void git(String... args) throws Exception {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(java.util.List.of(args));
        Process process = new ProcessBuilder(command).directory(tempDir.toFile()).start();
        assertEquals(0, process.waitFor());
    }

    private boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    @Test
    void helpDocumentsHookInstall() {
        Harness harness = new Harness(tempDir);

        int exitCode = harness.command().run(new String[]{"--help"});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("compatibility alias"));
        assertTrue(harness.stdout().contains("Usage: auto-ant reconcile"));
        assertTrue(harness.stdout().contains("--install-hook"));
        assertTrue(harness.stdout().contains("--confirm-stopped"));
    }

    @Test
    void explicitlyRejectsLegacyNoReload() {
        Harness harness = new Harness(tempDir);
        assertEquals(2, harness.command().run(new String[]{"--no-reload"}));
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
