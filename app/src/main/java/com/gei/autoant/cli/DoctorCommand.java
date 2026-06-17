package com.gei.autoant.cli;

import com.gei.autoant.detect.ProjectDetector;
import com.gei.autoant.model.DetectionResult;
import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.model.ReloadStrategy;
import com.gei.autoant.model.ServletNamespace;
import com.gei.autoant.model.SourceRoot;
import com.gei.autoant.model.WebRoot;
import com.gei.autoant.prompt.NonInteractiveOptions;
import com.gei.autoant.util.PathUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
                options = promptForOverrides(model, options);
                model = new ProjectDetector().detect(options.projectRoot(), options);
            }
            printDoctor(model);
            return model.hasBlockingMissingValues() ? 1 : 0;
        } catch (IllegalArgumentException ex) {
            context.err().println("doctor: " + ex.getMessage());
            return 2;
        }
    }

    private NonInteractiveOptions promptForOverrides(ProjectModel model, NonInteractiveOptions options) {
        NonInteractiveOptions.Builder builder = options.toBuilder();
        builder.appName(promptString("app.name", model.appName(), builder.appName()));
        builder.contextPath(promptString("context.path", model.contextPath(), builder.contextPath()));
        builder.javaRelease(promptInteger("java.release", model.javaRelease(), builder.javaRelease()));
        builder.tomcatHome(promptPath("tomcat.home", model.tomcatHome(), builder.tomcatHome(), model.projectRoot()));
        builder.sourceRoots(promptPathList("src.dirs", model.sourceRoots(), builder.sourceRoots(), model.projectRoot()));
        builder.webRoot(promptWebRoot("web.dir", model.webRoot(), builder.webRoot(), model.projectRoot()));
        builder.webInf(promptWebInf("webinf.dir", model.webRoot(), builder.webInf(), model.projectRoot()));
        builder.libraryRoots(promptLibraryRoots("lib.dirs", model.libraryRoots(), builder.libraryRoots(), model.projectRoot()));
        builder.reloadStrategy(promptReloadStrategy("reload.strategy", model.reloadStrategy(), builder.reloadStrategy()));
        builder.tomcatManagerUrl(promptString("tomcat.manager.url", model.tomcatManagerUrl(), builder.tomcatManagerUrl()));
        return builder.build();
    }

    private Optional<String> promptString(String label, DetectionResult<String> detected, Optional<String> currentOverride) {
        String detectedValue = detected.value().orElse("");
        return context.promptService().promptOverride(label, detectedValue).filter(value -> !value.isBlank()).or(() -> currentOverride);
    }

    private Optional<Integer> promptInteger(String label, DetectionResult<Integer> detected, Optional<Integer> currentOverride) {
        String detectedValue = detected.value().map(String::valueOf).orElse("");
        Optional<String> answer = context.promptService().promptOverride(label, detectedValue);
        if (answer.isEmpty() || answer.get().isBlank()) {
            return currentOverride;
        }
        return Optional.of(Integer.parseInt(answer.get().trim()));
    }

    private Optional<Path> promptPath(String label, DetectionResult<Path> detected, Optional<Path> currentOverride, Path root) {
        String detectedValue = detected.value().map(path -> PathUtils.display(root, path)).orElse("");
        Optional<String> answer = context.promptService().promptOverride(label, detectedValue);
        if (answer.isEmpty() || answer.get().isBlank()) {
            return currentOverride;
        }
        return Optional.of(PathUtils.resolve(root, answer.get()));
    }

    private List<Path> promptPathList(String label, DetectionResult<List<SourceRoot>> detected, List<Path> currentOverride, Path root) {
        String detectedValue = detected.value().orElse(List.of()).stream()
                .map(SourceRoot::path)
                .map(path -> PathUtils.display(root, path))
                .collect(Collectors.joining(","));
        Optional<String> answer = context.promptService().promptOverride(label, detectedValue);
        if (answer.isEmpty() || answer.get().isBlank()) {
            return currentOverride;
        }
        return PathUtils.parsePathList(root, answer.get());
    }

    private Optional<Path> promptWebRoot(String label, DetectionResult<WebRoot> detected, Optional<Path> currentOverride, Path root) {
        String detectedValue = detected.value().map(WebRoot::path).map(path -> PathUtils.display(root, path)).orElse("");
        Optional<String> answer = context.promptService().promptOverride(label, detectedValue);
        if (answer.isEmpty() || answer.get().isBlank()) {
            return currentOverride;
        }
        return Optional.of(PathUtils.resolve(root, answer.get()));
    }

    private Optional<Path> promptWebInf(String label, DetectionResult<WebRoot> detected, Optional<Path> currentOverride, Path root) {
        String detectedValue = detected.value().map(WebRoot::webInfPath).map(path -> PathUtils.display(root, path)).orElse("");
        Optional<String> answer = context.promptService().promptOverride(label, detectedValue);
        if (answer.isEmpty() || answer.get().isBlank()) {
            return currentOverride;
        }
        return Optional.of(PathUtils.resolve(root, answer.get()));
    }

    private List<Path> promptLibraryRoots(String label, DetectionResult<List<com.gei.autoant.model.LibraryRoot>> detected, List<Path> currentOverride, Path root) {
        String detectedValue = detected.value().orElse(List.of()).stream()
                .map(com.gei.autoant.model.LibraryRoot::path)
                .map(path -> PathUtils.display(root, path))
                .collect(Collectors.joining(","));
        Optional<String> answer = context.promptService().promptOverride(label, detectedValue);
        if (answer.isEmpty() || answer.get().isBlank()) {
            return currentOverride;
        }
        return PathUtils.parsePathList(root, answer.get());
    }

    private Optional<ReloadStrategy> promptReloadStrategy(String label, DetectionResult<ReloadStrategy> detected, Optional<ReloadStrategy> currentOverride) {
        String detectedValue = detected.value().map(ReloadStrategy::propertyValue).orElse("");
        Optional<String> answer = context.promptService().promptOverride(label, detectedValue);
        if (answer.isEmpty() || answer.get().isBlank()) {
            return currentOverride;
        }
        return Optional.of(ReloadStrategy.parse(answer.get()));
    }

    private void printDoctor(ProjectModel model) {
        context.out().println("auto-ant doctor");
        context.out().println();

        printField("Project root", DetectionResult.confident(model.projectRoot(), "--root"), path -> PathUtils.toPortableString(path.toAbsolutePath().normalize()));
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
        context.out().println("  --reload-strategy <name>  manager, touch-webxml, or none.");
        context.out().println("  --tomcat-manager-url <url>");
    }
}