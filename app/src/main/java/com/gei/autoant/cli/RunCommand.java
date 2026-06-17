package com.gei.autoant.cli;

import com.gei.autoant.run.AntRunner;
import com.gei.autoant.run.CommandResult;

import java.io.IOException;

public final class RunCommand {
    private final CommandContext context;

    public RunCommand(CommandContext context) {
        this.context = context;
    }

    public int run(String[] args) {
        CommandLine commandLine = CommandLine.parse(args);
        if (commandLine.hasAnyOption("help", "h") || commandLine.positionals().isEmpty()) {
            printHelp();
            return commandLine.positionals().isEmpty() ? 2 : 0;
        }

        try {
            CommandResult result = new AntRunner(context.out(), context.err()).runTargets(context.projectRoot(), commandLine.positionals());
            return result.exitCode();
        } catch (IOException ex) {
            context.err().println("run: failed to execute Ant: " + ex.getMessage());
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            context.err().println("run: interrupted");
            return 130;
        }
    }

    private void printHelp() {
        context.out().println("Usage: auto-ant run <ant-target> [more-targets]");
        context.out().println();
        context.out().println("Examples:");
        context.out().println("  auto-ant run clean-build");
        context.out().println("  auto-ant run deploy-exploded");
        context.out().println("  auto-ant run sync-web");
    }
}