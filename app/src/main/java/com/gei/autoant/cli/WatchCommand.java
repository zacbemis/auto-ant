package com.gei.autoant.cli;

public final class WatchCommand {
    private final CommandContext context;

    public WatchCommand(CommandContext context) {
        this.context = context;
    }

    public int run(String[] args) {
        CommandLine commandLine = CommandLine.parse(args);
        if (commandLine.hasAnyOption("help", "h")) {
            context.out().println("Usage: auto-ant watch");
            context.out().println();
            context.out().println("Watches project files and runs the appropriate Ant target. Planned for the watcher milestone.");
            return 0;
        }
        context.err().println("watch: not implemented yet. This command is planned after doctor/init/run are stable.");
        return 2;
    }
}