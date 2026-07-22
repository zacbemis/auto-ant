package com.gei.autoant.cli;

import java.util.Arrays;
import java.util.Locale;

public final class CommandRouter {
    private final CommandContext context;

    public CommandRouter(CommandContext context) {
        this.context = context;
    }

    public int run(String[] args) {
        if (args.length == 0 || isHelp(args[0])) {
            printHelp();
            return 0;
        }

        if (looksLikeTopLevelUpdateOptions(args)) {
            return new UpdateCommand(context).run(args);
        }

        String command = args[0].toLowerCase(Locale.ROOT);
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);

        return switch (command) {
            case "branch-refresh" -> new BranchRefreshCommand(context).run(commandArgs);
            case "reconcile" -> new ReconcileCommand(context).run(commandArgs);
            case "doctor" -> new DoctorCommand(context).run(commandArgs);
            case "init" -> new InitCommand(context).run(commandArgs);
            case "update" -> new UpdateCommand(context).run(commandArgs);
            case "run" -> new RunCommand(context).run(commandArgs);
            case "vscode", "refresh-vscode" -> new VsCodeCommand(context).run(commandArgs);
            case "reload" -> new ReloadCommand(context).run(commandArgs);
            default -> {
                context.err().println("Unknown command: " + args[0]);
                context.err().println();
                printHelp();
                yield 2;
            }
        };
    }

    private boolean isHelp(String value) {
        return "help".equalsIgnoreCase(value) || "--help".equals(value) || "-h".equals(value);
    }

    private boolean looksLikeTopLevelUpdateOptions(String[] args) {
        if (!args[0].startsWith("-") || isHelp(args[0])) {
            return false;
        }
        CommandLine commandLine = CommandLine.parse(args);
        return commandLine.hasAnyCommonUpdateOption();
    }

    private void printHelp() {
        context.out().println("auto-ant");
        context.out().println();
        context.out().println("Usage:");
        context.out().println("  auto-ant reconcile [options]");
        context.out().println("  auto-ant branch-refresh [options]");
        context.out().println("  auto-ant doctor [options]");
        context.out().println("  auto-ant init [options]");
        context.out().println("  auto-ant update [options]");
        context.out().println("  auto-ant vscode [options]");
        context.out().println("  auto-ant run <ant-target>");
        context.out().println("  auto-ant reload");
        context.out().println();
        context.out().println("Common options:");
        context.out().println("  --root <path>             Project root. Defaults to current directory.");
        context.out().println("  --app <name>              Application name override.");
        context.out().println("  --context <path>          Context path override, for example /MyApp.");
        context.out().println("  --src <paths>             Comma-separated Java source roots.");
        context.out().println("  --web <path>              Web root override.");
        context.out().println("  --webinf <path>           WEB-INF directory override.");
        context.out().println("  --lib <paths>             Comma-separated library directories.");
        context.out().println("  --tomcat <path>           Tomcat home override.");
        context.out().println("  --java <release>          Java release override.");
        context.out().println("  --interactive             Prompt in doctor/update mode; init always prompts before writing.");
    }
}
