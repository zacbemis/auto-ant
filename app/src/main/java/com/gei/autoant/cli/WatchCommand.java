package com.gei.autoant.cli;

import com.gei.autoant.run.AntRunner;
import com.gei.autoant.watch.ProjectWatcher;
import com.gei.autoant.watch.WatchConfiguration;

import java.io.IOException;

public final class WatchCommand {
    private final CommandContext context;

    public WatchCommand(CommandContext context) {
        this.context = context;
    }

    public int run(String[] args) {
        CommandLine commandLine = CommandLine.parse(args);
        if (commandLine.hasAnyOption("help", "h")) {
            printHelp();
            return 0;
        }

        try {
            WatchConfiguration configuration = WatchConfiguration.load(context.projectRoot(), commandLine);
            AntRunner antRunner = new AntRunner(context.out(), context.err());
            ProjectWatcher watcher = new ProjectWatcher(
                    configuration.projectRoot(),
                    configuration.watchRoots(),
                    configuration.debounceDelay(),
                    target -> antRunner.runTarget(configuration.projectRoot(), target),
                    context.out(),
                    context.err()
            );
            watcher.watch();
            return 0;
        } catch (IllegalArgumentException ex) {
            context.err().println("watch: " + ex.getMessage());
            return 2;
        } catch (IOException ex) {
            context.err().println("watch: failed to watch project: " + ex.getMessage());
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            context.err().println("watch: interrupted");
            return 130;
        }
    }

    private void printHelp() {
        context.out().println("Usage: auto-ant watch [options]");
        context.out().println();
        context.out().println("Watches configured source and web roots from auto-ant.properties.");
        context.out().println("Frontend changes run: ant sync-web");
        context.out().println();
        context.out().println("Options:");
        context.out().println("  --root <path>             Project root. Defaults to current directory.");
        context.out().println("  --debounce-ms <ms>        Debounce delay. Defaults to watch.debounce.ms or 750.");
    }
}