package com.gei.autoant.cli;

import com.gei.autoant.model.DetectionResult;
import com.gei.autoant.model.LibraryRoot;
import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.model.ReloadStrategy;
import com.gei.autoant.model.SourceRoot;
import com.gei.autoant.model.WebRoot;
import com.gei.autoant.prompt.NonInteractiveOptions;
import com.gei.autoant.util.PathUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class InteractiveOptionsPrompter {
    private final CommandContext context;

    InteractiveOptionsPrompter(CommandContext context) {
        this.context = context;
    }

    NonInteractiveOptions promptForOverrides(ProjectModel model, NonInteractiveOptions options) {
        NonInteractiveOptions.Builder builder = options.toBuilder();
        builder.appName(promptString("app.name", model.appName(), builder.appName()));
        builder.contextPath(promptString("context.path", model.contextPath(), builder.contextPath()));
        builder.javaRelease(promptInteger("java.release", model.javaRelease(), builder.javaRelease()));
        builder.jdkHome(promptRequiredPath("jdk.home", model.jdkHome(), builder.jdkHome(), model.projectRoot()));
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

    private Optional<Path> promptRequiredPath(String label, DetectionResult<Path> detected, Optional<Path> currentOverride, Path root) {
        String detectedValue = detected.value().map(path -> PathUtils.display(root, path)).orElse("");
        Optional<String> answer = context.promptService().promptRequired(label, detectedValue);
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

    private List<Path> promptLibraryRoots(String label, DetectionResult<List<LibraryRoot>> detected, List<Path> currentOverride, Path root) {
        String detectedValue = detected.value().orElse(List.of()).stream()
                .map(LibraryRoot::path)
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
}