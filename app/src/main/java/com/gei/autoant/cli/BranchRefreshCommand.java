package com.gei.autoant.cli;

import com.gei.autoant.generate.InitGenerator;
import com.gei.autoant.run.AntRunner;
import com.gei.autoant.run.CommandResult;
import com.gei.autoant.util.PathUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class BranchRefreshCommand {
    private static final String HOOK_MARKER = "AUTO-ANT POST-CHECKOUT HOOK";

    private final CommandContext context;

    public BranchRefreshCommand(CommandContext context) {
        this.context = context;
    }

    public int run(String[] args) {
        CommandLine commandLine = CommandLine.parse(args);
        if (commandLine.hasAnyOption("help", "h")) {
            printHelp();
            return 0;
        }

        try {
            Path projectRoot = projectRoot(commandLine);
            if (commandLine.hasOption("install-hook")) {
                return installHook(projectRoot);
            }
            return refresh(projectRoot, commandLine.hasOption("no-reload"), commandLine.hasOption("from-hook"));
        } catch (IllegalArgumentException ex) {
            context.err().println("branch-refresh: " + ex.getMessage());
            return 2;
        } catch (IOException ex) {
            context.err().println("branch-refresh: " + ex.getMessage());
            return 1;
        }
    }

    private int refresh(Path projectRoot, boolean noReload, boolean fromHook) throws IOException {
        Path buildFile = projectRoot.resolve(InitGenerator.AUTO_ANT_BUILD_FILE);
        if (Files.notExists(buildFile)) {
            context.err().println("branch-refresh: " + InitGenerator.AUTO_ANT_BUILD_FILE + " not found in "
                    + PathUtils.toPortableString(projectRoot) + ". Run auto-ant init first.");
            return fromHook ? 0 : 1;
        }

        context.out().println("auto-ant branch-refresh");
        context.out().println();
        context.out().println("Refreshing exploded deployment after branch checkout using "
                + projectRoot.relativize(buildFile).toString().replace('\\', '/') + "...");
        try {
            CommandResult result = new AntRunner(context.out(), context.err()).runTarget(projectRoot, buildFile, "branch-refresh");
            if (result.exitCode() != 0) {
                context.err().println("branch-refresh: Ant target branch-refresh failed with exit code " + result.exitCode());
                return result.exitCode();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            context.err().println("branch-refresh: interrupted");
            return 130;
        }

        if (noReload) {
            context.out().println("Skipping Tomcat reload because --no-reload was provided.");
            return 0;
        }

        context.out().println();
        context.out().println("Reloading Tomcat context after branch refresh...");
        return new ReloadCommand(context).run(new String[]{"--root", projectRoot.toString()});
    }

    private int installHook(Path projectRoot) throws IOException {
        Path hooksDirectory = hooksDirectory(projectRoot);
        Files.createDirectories(hooksDirectory);
        Path hook = hooksDirectory.resolve("post-checkout");
        String content = hookContent();

        if (Files.exists(hook)) {
            String existing = Files.readString(hook, StandardCharsets.UTF_8);
            if (!existing.contains(HOOK_MARKER)) {
                Path alternate = nextAvailable(hooksDirectory.resolve("post-checkout.auto-ant-new"));
                Files.writeString(alternate, content, StandardCharsets.UTF_8);
                makeExecutable(alternate);
                context.out().println("Existing .git/hooks/post-checkout does not look auto-ant generated.");
                context.out().println("Wrote " + PathUtils.display(projectRoot, alternate) + " instead; merge it into the existing hook when ready.");
                return 1;
            }
        }

        Files.writeString(hook, content, StandardCharsets.UTF_8);
        makeExecutable(hook);
        context.out().println("Installed .git/hooks/post-checkout auto-ant branch refresh hook.");
        context.out().println("After each branch checkout, Git will run auto-ant branch-refresh automatically.");
        return 0;
    }

    private Path projectRoot(CommandLine commandLine) {
        return commandLine.option("root")
                .map(value -> PathUtils.resolve(context.projectRoot(), value))
                .orElse(context.projectRoot())
                .toAbsolutePath()
                .normalize();
    }

    private Path hooksDirectory(Path projectRoot) throws IOException {
        Path gitPath = projectRoot.resolve(".git");
        if (Files.isDirectory(gitPath)) {
            return gitPath.resolve("hooks");
        }
        if (Files.isRegularFile(gitPath)) {
            String firstLine = Files.readString(gitPath, StandardCharsets.UTF_8).lines().findFirst().orElse("").trim();
            if (firstLine.toLowerCase(Locale.ROOT).startsWith("gitdir:")) {
                Path gitDirectory = PathUtils.resolve(projectRoot, firstLine.substring("gitdir:".length()).trim());
                return gitDirectory.resolve("hooks");
            }
        }
        throw new IllegalArgumentException(".git directory not found in " + PathUtils.toPortableString(projectRoot) + ". Run from the Git project root.");
    }

    private String hookContent() {
        return "#!/bin/sh\n"
                + "# " + HOOK_MARKER + "\n"
                + "# Generated by auto-ant branch-refresh --install-hook.\n"
                + "# Git passes: $1 old HEAD, $2 new HEAD, $3 branch checkout flag.\n"
                + "if [ \"$3\" = \"1\" ]; then\n"
                + "  echo \"auto-ant: branch checkout detected; refreshing exploded deployment...\"\n"
                + "  auto-ant branch-refresh --from-hook || echo \"auto-ant: branch refresh failed; run auto-ant branch-refresh after fixing the issue.\"\n"
                + "fi\n";
    }

    private Path nextAvailable(Path path) {
        if (Files.notExists(path)) {
            return path;
        }
        Path parent = path.getParent();
        String fileName = path.getFileName().toString();
        int counter = 2;
        while (true) {
            Path candidate = parent.resolve(fileName + "." + counter);
            if (Files.notExists(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    private void makeExecutable(Path path) {
        path.toFile().setExecutable(true, false);
    }

    private void printHelp() {
        context.out().println("Usage: auto-ant branch-refresh [options]");
        context.out().println();
        context.out().println("Runs the generated branch-refresh Ant target, then reloads the configured Tomcat context.");
        context.out().println("Use --install-hook once per project to run this automatically after Git branch checkouts.");
        context.out().println();
        context.out().println("Options:");
        context.out().println("  --root <path>       Project root. Defaults to current directory.");
        context.out().println("  --install-hook      Install or update .git/hooks/post-checkout for automatic branch refresh.");
        context.out().println("  --no-reload         Run the clean rebuild/redeploy/sync step without auto-ant reload.");
    }
}