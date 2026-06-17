package com.gei.autoant.cli;

import com.gei.autoant.detect.ProjectDetector;
import com.gei.autoant.generate.GeneratedFile;
import com.gei.autoant.generate.GenerationResult;
import com.gei.autoant.generate.InitGenerator;
import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.prompt.NonInteractiveOptions;

import java.io.IOException;

public final class InitCommand {
    private final CommandContext context;

    public InitCommand(CommandContext context) {
        this.context = context;
    }

    public int run(String[] args) {
        CommandLine commandLine = CommandLine.parse(args);
        if (commandLine.hasAnyOption("help", "h")) {
            printHelp();
            return 0;
        }

        try {
            NonInteractiveOptions options = NonInteractiveOptions.from(commandLine, context.projectRoot());
            ProjectModel model = new ProjectDetector().detect(options.projectRoot(), options);
            GenerationResult result = new InitGenerator(model.projectRoot()).generate(model);

            context.out().println("auto-ant init");
            context.out().println();
            for (GeneratedFile generatedFile : result.files()) {
                context.out().println(generatedFile.message());
            }
            if (!model.warnings().isEmpty()) {
                context.out().println();
                context.out().println("Detection warnings:");
                for (String warning : model.warnings()) {
                    context.out().println("  - " + warning);
                }
            }
            return 0;
        } catch (IllegalArgumentException ex) {
            context.err().println("init: " + ex.getMessage());
            return 2;
        } catch (IOException ex) {
            context.err().println("init: failed to write files: " + ex.getMessage());
            return 1;
        }
    }

    private void printHelp() {
        context.out().println("Usage: auto-ant init [options]");
        context.out().println();
        context.out().println("Generates build.xml, auto-ant properties, VS Code tasks, and safe .gitignore entries.");
        context.out().println("Existing generated targets are never overwritten destructively.");
    }
}