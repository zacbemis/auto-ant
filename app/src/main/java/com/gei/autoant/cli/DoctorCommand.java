package com.gei.autoant.cli;

import com.gei.autoant.detect.ProjectDetector;
import com.gei.autoant.model.DetectionResult;
import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.model.ReloadStrategy;
import com.gei.autoant.model.ServletNamespace;
import com.gei.autoant.model.SourceRoot;
import com.gei.autoant.prompt.NonInteractiveOptions;
import com.gei.autoant.util.PathUtils;
import com.gei.autoant.deploy.DeploymentState;
import com.gei.autoant.deploy.ReconcileConfiguration;

import java.util.function.Function;
import java.util.stream.Collectors;

public final class DoctorCommand {
    private final CommandContext context;

    public DoctorCommand(CommandContext context) {
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
            if (options.interactive()) {
                options = new InteractiveOptionsPrompter(context).promptForOverrides(model, options);
                model = new ProjectDetector().detect(options.projectRoot(), options);
            }
            printDoctor(model);
            printDeploymentStatus(model.projectRoot());
            return commandLine.hasOption("strict") && model.hasBlockingMissingValues() ? 1 : 0;
        } catch (IllegalArgumentException ex) {
            context.err().println("doctor: " + ex.getMessage());
            return 2;
        }
    }

    private void printDeploymentStatus(java.nio.file.Path projectRoot) {
        context.out().println("Deployment reconciliation:");
        try {
            ReconcileConfiguration configuration = ReconcileConfiguration.load(projectRoot);
            DeploymentState state = new DeploymentState(configuration);
            if (state.isStale()) {
                context.out().println("  STALE: " + state.staleReason());
                context.out().println("  Recovery: if critical transaction paths are reported, keep Tomcat stopped and recover those artifacts first; otherwise run safe reconcile.");
            } else if (state.isCurrent(state.fingerprint(configuration), configuration.deployDirectory())) {
                context.out().println("  Current: resolved build inputs match and live snapshot integrity equals the last successful reconcile.");
            } else {
                context.out().println("  STALE/UNKNOWN: resolved build inputs or verified live snapshot integrity differs from the last success.");
                context.out().println("  Recovery: run auto-ant reconcile using a safe server policy.");
            }
        } catch (Exception ex) {
            context.out().println("  Not validated: " + ex.getMessage());
            context.out().println("  Run auto-ant update, then auto-ant doctor again.");
        }
        context.out().println();
    }

    private void printDoctor(ProjectModel model) {
        context.out().println("auto-ant doctor");
        context.out().println();

        printField("Project root", model.projectRootResult(), path -> PathUtils.toPortableString(path.toAbsolutePath().normalize()));
        printField("App name", model.appName(), Function.identity());
        printField("Context path", model.contextPath(), Function.identity());
        printField("Java source roots", model.sourceRoots(), roots -> roots.stream()
                .map(SourceRoot::path)
                .map(path -> PathUtils.display(model.projectRoot(), path))
                .collect(Collectors.joining(System.lineSeparator() + "  ")));
        printField("Web root", model.webRoot(), webRoot -> PathUtils.display(model.projectRoot(), webRoot.path()));
        printField("WEB-INF", model.webRoot(), webRoot -> PathUtils.display(model.projectRoot(), webRoot.webInfPath()));
        printField("Libraries", model.libraryRoots(), roots -> roots.stream()
                .map(com.gei.autoant.model.LibraryRoot::path)
                .map(path -> PathUtils.display(model.projectRoot(), path))
                .collect(Collectors.joining(System.lineSeparator() + "  ")));
        printField("Servlet namespace", model.servletNamespace(), ServletNamespace::displayName);
        printField("Recommended Tomcat", model.recommendedTomcat(), Function.identity());
        printField("Tomcat home", model.tomcatHome(), path -> PathUtils.display(model.projectRoot(), path));
        printField("Ant", model.antExecutable(), path -> "Found: " + PathUtils.toPortableString(path));
        printField("Java", model.javaRelease(), String::valueOf);
        printField("JDK home", model.jdkHome(), path -> PathUtils.display(model.projectRoot(), path));
        printField("Reload strategy", model.reloadStrategy(), ReloadStrategy::propertyValue);
        printField("Tomcat manager URL", model.tomcatManagerUrl(), Function.identity());

        if (!model.warnings().isEmpty()) {
            context.out().println();
            context.out().println("Warnings:");
            for (String warning : model.warnings()) {
                context.out().println("  - " + warning);
            }
        }
    }

    private <T> void printField(String label, DetectionResult<T> result, Function<T, String> formatter) {
        context.out().println(label + ":");
        if (result.value().isPresent()) {
            context.out().println("  " + formatter.apply(result.value().get()));
        } else {
            context.out().println("  Not detected");
        }
        context.out().println("  Status: " + result.status().displayName());
        result.overrideFlag().ifPresent(flag -> context.out().println("  User override available: " + flag));
        if (result.status() == com.gei.autoant.model.DetectionStatus.USER_REQUIRED) {
            result.overrideFlag().ifPresent(flag -> context.out().println("  Action required: rerun with " + flag + " or use --interactive."));
        }
        for (String warning : result.warnings()) {
            context.out().println("  Warning: " + warning);
        }
        context.out().println();
    }

    private void printHelp() {
        context.out().println("Usage: auto-ant doctor [options]");
        context.out().println();
        context.out().println("Detects project layout, Tomcat setup, Java version, servlet namespace, and likely build settings.");
        context.out().println("Writes nothing.");
        context.out().println();
        context.out().println("Options:");
        context.out().println("  --interactive             Prompt to accept or override detected values.");
        context.out().println("  --strict                  Exit non-zero when required user choices are missing.");
        context.out().println("  --root <path>             Project root.");
        context.out().println("  --app <name>              Application name.");
        context.out().println("  --context <path>          Context path.");
        context.out().println("  --src <paths>             Comma-separated source roots.");
        context.out().println("  --web <path>              Web root.");
        context.out().println("  --webinf <path>           WEB-INF directory.");
        context.out().println("  --lib <paths>             Comma-separated library roots.");
        context.out().println("  --tomcat <path>           Tomcat home.");
        context.out().println("  --ant <path>              Ant executable.");
        context.out().println("  --java <release>          Java release.");
        context.out().println("  --jdk <path>              JDK home directory used by generated VS Code settings.");
        context.out().println("  --reload-strategy <name>  manager, touch-webxml, or none.");
        context.out().println("  --tomcat-manager-url <url>");
    }
}
