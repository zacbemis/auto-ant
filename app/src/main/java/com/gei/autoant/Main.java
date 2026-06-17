package com.gei.autoant;

import com.gei.autoant.cli.CommandContext;
import com.gei.autoant.cli.CommandRouter;
import com.gei.autoant.prompt.ConsolePromptService;

import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        CommandContext context = new CommandContext(
                Path.of("").toAbsolutePath().normalize(),
                System.out,
                System.err,
                new ConsolePromptService(System.in, System.out)
        );

        int exitCode = new CommandRouter(context).run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}