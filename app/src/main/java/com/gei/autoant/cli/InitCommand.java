package com.gei.autoant.cli;

import com.gei.autoant.detect.ProjectDetector;
import com.gei.autoant.generate.GeneratedFile;
import com.gei.autoant.generate.GenerationResult;
import com.gei.autoant.generate.InitGenerator;
import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.prompt.NonInteractiveOptions;
import com.gei.autoant.run.AntRunner;
import com.gei.autoant.run.CommandResult;
import com.gei.autoant.vscode.CodeCliVsCodeExtensionChecker;
import com.gei.autoant.vscode.VsCodeExtensionChecker;

import java.io.IOException;
import java.nio.file.Path;

public final class InitCommand {
    private final CommandContext context;
    private final VsCodeExtensionChecker extensionChecker;
    private final AntTargetRunner antTargetRunner;

    public InitCommand(CommandContext context) {
        this(context, new CodeCliVsCodeExtensionChecker());
    }

    InitCommand(CommandContext context, VsCodeExtensionChecker extensionChecker) {
        this(context, extensionChecker, new AntRunner(context.out(), context.err())::runTarget);
    }

    InitCommand(CommandContext context, VsCodeExtensionChecker extensionChecker, AntTargetRunner antTargetRunner) {
        this.context = context;
        this.extensionChecker = extensionChecker;
        this.antTargetRunner = antTargetRunner;
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
            options = new InteractiveOptionsPrompter(context).promptForOverrides(model, options);
            model = new ProjectDetector().detect(options.projectRoot(), options);
            GenerationResult result = new InitGenerator(model.projectRoot()).generate(model);

            context.out().println("auto-ant init");
            context.out().println();
            for (GeneratedFile generatedFile : result.files()) {
                context.out().println(generatedFile.message());
            }
            printFileWatcherExtensionStatus();
            if (!model.warnings().isEmpty()) {
                context.out().println();
                context.out().println("Detection warnings:");
                for (String warning : model.warnings()) {
                    context.out().println("  - " + warning);
                }
            }
            return runInitialDeploy(model);
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
        context.out().println("Prompts to accept or override detected values, then generates build.xml, auto-ant properties,");
        context.out().println("VS Code tasks/settings, safe .gitignore entries, and runs deploy-exploded.");
        context.out().println("The VS Code settings include File Watcher commands and Java library paths.");
        context.out().println("Existing generated targets are never overwritten destructively.");
        context.out().println();
        context.out().println("Options:");
        context.out().println("  --root <path>             Project root. Defaults to current directory.");
        context.out().println("  --app <name>              Application name.");
        context.out().println("  --context <path>          Context path.");
        context.out().println("  --src <paths>             Comma-separated source roots.");
        context.out().println("  --web <path>              Web root.");
        context.out().println("  --webinf <path>           WEB-INF directory.");
        context.out().println("  --lib <paths>             Comma-separated library roots.");
        context.out().println("  --tomcat <path>           Tomcat home.");
        context.out().println("  --ant <path>              Ant executable.");
        context.out().println("  --java <release>          Java release.");
        context.out().println("  --reload-strategy <name>  manager, touch-webxml, or none.");
        context.out().println("  --tomcat-manager-url <url>");
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

    private int runInitialDeploy(ProjectModel model) {
        context.out().println();
        context.out().println("Running initial deploy-exploded...");
        try {
            CommandResult result = antTargetRunner.runTarget(model.projectRoot(), "deploy-exploded");
            if (result.exitCode() != 0) {
                context.err().println("init: deploy-exploded failed with exit code " + result.exitCode());
            }
            return result.exitCode();
        } catch (IOException ex) {
            context.err().println("init: failed to run deploy-exploded: " + ex.getMessage());
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            context.err().println("init: deploy-exploded interrupted");
            return 130;
        }
    }

    @FunctionalInterface
    interface AntTargetRunner {
        CommandResult runTarget(Path projectRoot, String target) throws IOException, InterruptedException;
    }
}