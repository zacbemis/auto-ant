package com.gei.autoant.cli;

import com.gei.autoant.run.ProcessRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

final class GitHookInstaller {
    private static final String START = "# >>> AUTO-ANT RECONCILE >>>";
    private static final String END = "# <<< AUTO-ANT RECONCILE <<<";
    private static final List<String> HOOKS = List.of("post-checkout", "post-merge", "post-rewrite", "post-commit");
    private final CommandContext context;

    GitHookInstaller(CommandContext context) {
        this.context = context;
    }

    int install(Path projectRoot) throws IOException {
        Path hooks = hooksDirectory(projectRoot);
        Files.createDirectories(hooks);
        for (String name : HOOKS) {
            compose(hooks.resolve(name), name);
        }
        context.out().println("Installed/composed auto-ant reconcile blocks in post-checkout, post-merge, post-rewrite, and post-commit.");
        context.out().println("Existing hook content was preserved. Duplicate triggers are lock- and fingerprint-safe.");
        return 0;
    }

    private void compose(Path hook, String hookName) throws IOException {
        String existing = Files.exists(hook) ? Files.readString(hook, StandardCharsets.UTF_8) : "#!/bin/sh\n";
        String block = block(hookName);
        String updated;
        int start = existing.indexOf(START);
        int end = existing.indexOf(END);
        if (start >= 0 && end >= start) {
            updated = existing.substring(0, start) + block + existing.substring(end + END.length());
        } else if (existing.startsWith("#!") && existing.contains("\n")) {
            int firstNewline = existing.indexOf('\n') + 1;
            updated = existing.substring(0, firstNewline) + block + existing.substring(firstNewline);
        } else {
            updated = existing + (existing.endsWith("\n") ? "" : "\n") + block;
        }
        Files.writeString(hook, updated, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            if (!hook.toFile().setExecutable(true, false) || !Files.isExecutable(hook)) {
                throw new IOException("Installed Git hook is not executable: " + hook);
            }
        }
    }

    private String block(String hookName) {
        String guard = "post-checkout".equals(hookName)
                ? "# Covers both branch ($3=1) and path ($3=0) checkout.\n"
                : "";
        return START + "\n"
                + guard
                + "echo \"auto-ant: Git " + hookName + " detected; reconciling deployment...\"\n"
                + "if ! auto-ant reconcile --from-hook; then\n"
                + "  echo \"auto-ant: reconcile did not complete. DeploymentState records actionable stale/critical failures; lock contention and current no-ops do not create false stale markers.\" >&2\n"
                + "  echo \"auto-ant: recovery: run auto-ant reconcile (or --confirm-stopped only after stopping Tomcat).\" >&2\n"
                + "fi\n"
                + END + "\n";
    }

    private Path hooksDirectory(Path projectRoot) throws IOException {
        java.io.ByteArrayOutputStream stdout = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream stderr = new java.io.ByteArrayOutputStream();
        try {
            int exit = new ProcessRunner(new java.io.PrintStream(stdout), new java.io.PrintStream(stderr))
                    .run(projectRoot, List.of("git", "rev-parse", "--path-format=absolute", "--git-path", "hooks")).exitCode();
            if (exit != 0) {
                throw new IllegalArgumentException("Git could not resolve the effective hooks path: " + stderr.toString(StandardCharsets.UTF_8).trim());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while resolving Git hooks path.", ex);
        }
        String output = stdout.toString(StandardCharsets.UTF_8).trim();
        if (output.isBlank()) throw new IllegalArgumentException("Git returned an empty hooks path.");
        Path hooks = Path.of(output).toAbsolutePath().normalize();
        Path parent = hooks.getParent();
        if (parent == null) throw new IllegalArgumentException("Git returned an invalid hooks path.");
        return hooks;
    }
}
