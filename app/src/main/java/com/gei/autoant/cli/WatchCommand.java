package com.gei.autoant.cli;

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

        context.out().println("auto-ant watch");
        context.out().println();
        context.out().println("auto-ant does not run its own long-lived file watcher.");
        context.out().println("Run auto-ant init --file-watcher to generate .vscode/settings.json for the VS Code File Watcher extension.");
        context.out().println("Install the extension from https://github.com/appulate/vscode-file-watcher and let VS Code run ant sync-web on frontend saves.");
        return 0;
    }

    private void printHelp() {
        context.out().println("Usage: auto-ant watch");
        context.out().println();
        context.out().println("The watcher workflow is configured through generated .vscode/settings.json.");
        context.out().println("Install the VS Code File Watcher extension and run auto-ant init --file-watcher.");
    }
}