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

public final class VsCodeCommand {
    private final CommandContext context;
    private final VsCodeExtensionChecker extensionChecker;

    public VsCodeCommand(CommandContext context) {
        this(context, new CodeCliVsCodeExtensionChecker());
    }

    VsCodeCommand(CommandContext context, VsCodeExtensionChecker extensionChecker) {
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
            NonInteractiveOptions options = NonInteractiveOptions.from(commandLine, context.projectRoot());
            ProjectModel model = new ProjectDetector().detect(options.projectRoot(), options);
            if (options.interactive()) {
                options = new InteractiveOptionsPrompter(context).promptForOverrides(model, options);
                model = new ProjectDetector().detect(options.projectRoot(), options);
            }

            GenerationResult result = new InitGenerator(model.projectRoot()).generateVsCode(model);
            context.out().println("auto-ant vscode");
            context.out().println();
            for (GeneratedFile generatedFile : result.files()) {
                context.out().println(generatedFile.message());
            }
            context.out().println();
            context.out().println("Active auto-ant build file: " + model.projectRoot().relativize(result.buildFile()).toString().replace('\\', '/'));
            printFileWatcherExtensionStatus();
            return 0;
        } catch (IllegalArgumentException ex) {
            context.err().println("vscode: " + ex.getMessage());
            return 2;
        } catch (IOException ex) {
            context.err().println("vscode: failed to write VS Code files: " + ex.getMessage());
            return 1;
        }
    }

    private void printHelp() {
        context.out().println("Usage: auto-ant vscode [options]");
        context.out().println();
        context.out().println("Regenerates only .vscode/tasks.json and .vscode/settings.json.");
        context.out().println("Existing settings.json is merged so unrelated user settings are preserved.");
        context.out().println();
        context.out().println("Options match doctor/init detection overrides, including --root, --src, --web, --lib, --tomcat, and --java.");
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
    }
}