package com.gei.autoant.cli;

public final class ReloadCommand {
    private final CommandContext context;

    public ReloadCommand(CommandContext context) {
        this.context = context;
    }

    public int run(String[] args) {
        CommandLine commandLine = CommandLine.parse(args);
        if (commandLine.hasAnyOption("help", "h")) {
            context.out().println("Usage: auto-ant reload");
            context.out().println();
            context.out().println("Reloads the configured Tomcat context. Planned for the reload milestone.");
            return 0;
        }
        context.err().println("reload: not implemented yet. This command is planned after AntRunner and watcher support.");
        return 2;
    }
}