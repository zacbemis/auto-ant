package com.gei.autoant.cli;

import com.gei.autoant.detect.ProjectDetector;
import com.gei.autoant.generate.GeneratedFile;
import com.gei.autoant.generate.GenerationResult;
import com.gei.autoant.generate.InitGenerator;
import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.prompt.NonInteractiveOptions;
import com.gei.autoant.vscode.CodeCliVsCodeExtensionChecker;
import com.gei.autoant.vscode.VsCodeExtensionChecker;

import java.io.IOException;

public final class InitCommand {
    private final CommandContext context;
    private final VsCodeExtensionChecker extensionChecker;

    public InitCommand(CommandContext context) {
        this(context, new CodeCliVsCodeExtensionChecker());
    }

    InitCommand(CommandContext context, VsCodeExtensionChecker extensionChecker) {
        this.context = context;
        this.extensionChecker = extensionChecker;
    }

    public int run(String[] args) {
        CommandLine commandLine = CommandLine.parse(args);
        if (commandLine.hasAnyOption("help", "h")) {
            printHelp();
            return 0;
        }

        try {
            boolean fileWatcherEnabled = commandLine.hasAnyOption("file-watcher", "vscode-file-watcher");
            NonInteractiveOptions options = NonInteractiveOptions.from(commandLine, context.projectRoot());
            ProjectModel model = new ProjectDetector().detect(options.projectRoot(), options);
            GenerationResult result = new InitGenerator(model.projectRoot()).generate(model, fileWatcherEnabled);

            context.out().println("auto-ant init");
            context.out().println();
            for (GeneratedFile generatedFile : result.files()) {
                context.out().println(generatedFile.message());
            }
            if (fileWatcherEnabled) {
                printFileWatcherExtensionStatus();
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
        context.out().println();
        context.out().println("Options:");
        context.out().println("  --file-watcher           Generate .vscode/settings.json for the VS Code File Watcher extension.");
        context.out().println("  --vscode-file-watcher    Alias for --file-watcher.");
    }

    private void printFileWatcherExtensionStatus() {
        context.out().println();
        if (extensionChecker.isInstalled(VsCodeExtensionChecker.FILE_WATCHER_EXTENSION_ID)) {
            context.out().println("VS Code File Watcher extension detected: " + VsCodeExtensionChecker.FILE_WATCHER_EXTENSION_ID + ".");
            return;
        }
        context.out().println("VS Code File Watcher extension not detected: " + VsCodeExtensionChecker.FILE_WATCHER_EXTENSION_ID + ".");
        context.out().println("Install it before relying on the generated .vscode/settings.json watcher integration:");
        context.out().println("  code --install-extension " + VsCodeExtensionChecker.FILE_WATCHER_EXTENSION_ID);
        context.out().println("  https://github.com/appulate/vscode-file-watcher");
    }
}