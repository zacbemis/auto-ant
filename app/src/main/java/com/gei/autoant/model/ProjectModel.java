package com.gei.autoant.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ProjectModel {
    private final Path projectRoot;
    private final DetectionResult<Path> projectRootResult;
    private final DetectionResult<String> appName;
    private final DetectionResult<String> contextPath;
    private final DetectionResult<List<SourceRoot>> sourceRoots;
    private final DetectionResult<WebRoot> webRoot;
    private final DetectionResult<List<LibraryRoot>> libraryRoots;
    private final DetectionResult<ServletNamespace> servletNamespace;
    private final DetectionResult<String> recommendedTomcat;
    private final DetectionResult<Path> tomcatHome;
    private final DetectionResult<Path> antExecutable;
    private final DetectionResult<Integer> javaRelease;
    private final DetectionResult<Path> jdkHome;
    private final DetectionResult<ReloadStrategy> reloadStrategy;
    private final DetectionResult<String> tomcatManagerUrl;
    private final List<String> warnings;

    public ProjectModel(
            Path projectRoot,
            DetectionResult<Path> projectRootResult,
            DetectionResult<String> appName,
            DetectionResult<String> contextPath,
            DetectionResult<List<SourceRoot>> sourceRoots,
            DetectionResult<WebRoot> webRoot,
            DetectionResult<List<LibraryRoot>> libraryRoots,
            DetectionResult<ServletNamespace> servletNamespace,
            DetectionResult<String> recommendedTomcat,
            DetectionResult<Path> tomcatHome,
            DetectionResult<Path> antExecutable,
            DetectionResult<Integer> javaRelease,
            DetectionResult<Path> jdkHome,
            DetectionResult<ReloadStrategy> reloadStrategy,
            DetectionResult<String> tomcatManagerUrl,
            List<String> warnings
    ) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        this.projectRootResult = Objects.requireNonNull(projectRootResult, "projectRootResult");
        this.appName = Objects.requireNonNull(appName, "appName");
        this.contextPath = Objects.requireNonNull(contextPath, "contextPath");
        this.sourceRoots = Objects.requireNonNull(sourceRoots, "sourceRoots");
        this.webRoot = Objects.requireNonNull(webRoot, "webRoot");
        this.libraryRoots = Objects.requireNonNull(libraryRoots, "libraryRoots");
        this.servletNamespace = Objects.requireNonNull(servletNamespace, "servletNamespace");
        this.recommendedTomcat = Objects.requireNonNull(recommendedTomcat, "recommendedTomcat");
        this.tomcatHome = Objects.requireNonNull(tomcatHome, "tomcatHome");
        this.antExecutable = Objects.requireNonNull(antExecutable, "antExecutable");
        this.javaRelease = Objects.requireNonNull(javaRelease, "javaRelease");
        this.jdkHome = Objects.requireNonNull(jdkHome, "jdkHome");
        this.reloadStrategy = Objects.requireNonNull(reloadStrategy, "reloadStrategy");
        this.tomcatManagerUrl = Objects.requireNonNull(tomcatManagerUrl, "tomcatManagerUrl");
        this.warnings = List.copyOf(warnings);
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public DetectionResult<Path> projectRootResult() {
        return projectRootResult;
    }

    public DetectionResult<String> appName() {
        return appName;
    }

    public DetectionResult<String> contextPath() {
        return contextPath;
    }

    public DetectionResult<List<SourceRoot>> sourceRoots() {
        return sourceRoots;
    }

    public DetectionResult<WebRoot> webRoot() {
        return webRoot;
    }

    public DetectionResult<List<LibraryRoot>> libraryRoots() {
        return libraryRoots;
    }

    public DetectionResult<ServletNamespace> servletNamespace() {
        return servletNamespace;
    }

    public DetectionResult<String> recommendedTomcat() {
        return recommendedTomcat;
    }

    public DetectionResult<Path> tomcatHome() {
        return tomcatHome;
    }

    public DetectionResult<Path> antExecutable() {
        return antExecutable;
    }

    public DetectionResult<Integer> javaRelease() {
        return javaRelease;
    }

    public DetectionResult<Path> jdkHome() {
        return jdkHome;
    }

    public DetectionResult<ReloadStrategy> reloadStrategy() {
        return reloadStrategy;
    }

    public DetectionResult<String> tomcatManagerUrl() {
        return tomcatManagerUrl;
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public boolean hasBlockingMissingValues() {
        return projectRootResult.status() == DetectionStatus.USER_REQUIRED
                || javaRelease.status() == DetectionStatus.USER_REQUIRED
                || jdkHome.status() == DetectionStatus.USER_REQUIRED
                || tomcatHome.status() == DetectionStatus.USER_REQUIRED
                || sourceRoots.status() == DetectionStatus.USER_REQUIRED
                || webRoot.status() == DetectionStatus.USER_REQUIRED
                || libraryRoots.status() == DetectionStatus.USER_REQUIRED;
    }

    public static List<String> collectWarnings(DetectionResult<?>... results) {
        List<String> warnings = new ArrayList<>();
        for (DetectionResult<?> result : results) {
            warnings.addAll(result.warnings());
        }
        return warnings;
    }
}