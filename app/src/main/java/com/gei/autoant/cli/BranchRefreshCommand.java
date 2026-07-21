package com.gei.autoant.cli;

public final class BranchRefreshCommand {
    private final CommandContext context;

    public BranchRefreshCommand(CommandContext context) {
        this.context = context;
    }

    public int run(String[] args) {
        CommandLine line = CommandLine.parse(args);
        if (line.hasOption("no-reload")) {
            context.err().println("branch-refresh: --no-reload is unsupported by safe reconciliation and is not silently ignored. Use auto-ant reconcile with a configured lifecycle policy.");
            return 2;
        }
        context.out().println("branch-refresh is a compatibility alias for reconcile.");
        return new ReconcileCommand(context).run(args);
    }
}
