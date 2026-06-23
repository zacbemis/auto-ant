package com.gei.autoant.generate;

import com.gei.autoant.model.LibraryRoot;
import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.model.ReloadStrategy;
import com.gei.autoant.model.SourceRoot;
import com.gei.autoant.model.WebRoot;
import com.gei.autoant.util.PathUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

final class ModelValues {
    private ModelValues() {
    }

    static String appName(ProjectModel model) {
        return model.appName().value().orElseGet(() -> model.projectRoot().getFileName() == null ? "app" : model.projectRoot().getFileName().toString());
    }

    static String contextPath(ProjectModel model) {
        return model.contextPath().value().orElse("/" + appName(model));
    }

    static String contextDeployName(ProjectModel model) {
        return contextDeployName(contextPath(model));
    }

    static String contextDescriptorFileName(ProjectModel model) {
        return contextDeployName(model) + ".xml";
    }

    private static String contextDeployName(String contextPath) {
        String normalized = contextPath.trim();
        if (normalized.isBlank() || normalized.equals("/")) {
            return "ROOT";
        }

        String deployName = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        while (deployName.endsWith("/")) {
            deployName = deployName.substring(0, deployName.length() - 1);
        }
        deployName = deployName.replace('/', '#');
        return deployName.isBlank() ? "ROOT" : deployName;
    }

    static int javaRelease(ProjectModel model) {
        return model.javaRelease().value().orElse(8);
    }

    static List<Path> sourceRoots(ProjectModel model) {
        return model.sourceRoots().value().orElse(List.of(new SourceRoot(model.projectRoot().resolve("src")))).stream()
                .map(SourceRoot::path)
                .toList();
    }

    static Path webRoot(ProjectModel model) {
        return model.webRoot().value().map(WebRoot::path).orElse(model.projectRoot().resolve("web"));
    }

    static Path webInf(ProjectModel model) {
        return model.webRoot().value().map(WebRoot::webInfPath).orElse(webRoot(model).resolve("WEB-INF"));
    }

    static List<Path> libraryRoots(ProjectModel model) {
        List<Path> detected = model.libraryRoots().value().orElse(List.of()).stream()
                .map(LibraryRoot::path)
                .toList();
        if (!detected.isEmpty()) {
            return detected;
        }
        return List.of(webInf(model).resolve("lib"), model.projectRoot().resolve("lib"));
    }

    static String commaPaths(ProjectModel model, List<Path> paths) {
        return paths.stream()
                .map(path -> PathUtils.display(model.projectRoot(), path))
                .collect(Collectors.joining(","));
    }

    static String relativePath(ProjectModel model, Path path) {
        return PathUtils.display(model.projectRoot(), path);
    }

    static String tomcatHome(ProjectModel model) {
        return model.tomcatHome().value()
                .map(path -> PathUtils.toPortableString(path.toAbsolutePath().normalize()))
                .orElse("");
    }

    static String jdkHome(ProjectModel model) {
        return model.jdkHome().value()
                .map(path -> PathUtils.toPortableString(path.toAbsolutePath().normalize()))
                .orElse("");
    }

    static String reloadStrategy(ProjectModel model) {
        return model.reloadStrategy().value().orElse(ReloadStrategy.TOUCH_WEBXML).propertyValue();
    }

    static String managerUrl(ProjectModel model) {
        return model.tomcatManagerUrl().value().orElse("http://localhost:8080/manager/text");
    }
}