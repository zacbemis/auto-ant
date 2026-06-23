package com.gei.autoant.prompt;

import com.gei.autoant.cli.CommandLine;
import com.gei.autoant.model.ReloadStrategy;
import com.gei.autoant.util.PathUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NonInteractiveOptions {
    private final Path projectRoot;
    private final boolean interactive;
    private final Optional<String> appName;
    private final Optional<String> contextPath;
    private final Optional<Integer> javaRelease;
    private final Optional<Path> jdkHome;
    private final Optional<Path> tomcatHome;
    private final Optional<Path> antExecutable;
    private final List<Path> sourceRoots;
    private final Optional<Path> webRoot;
    private final Optional<Path> webInf;
    private final List<Path> libraryRoots;
    private final Optional<ReloadStrategy> reloadStrategy;
    private final Optional<String> tomcatManagerUrl;

    private NonInteractiveOptions(Builder builder) {
        this.projectRoot = builder.projectRoot;
        this.interactive = builder.interactive;
        this.appName = builder.appName;
        this.contextPath = builder.contextPath;
        this.javaRelease = builder.javaRelease;
        this.jdkHome = builder.jdkHome;
        this.tomcatHome = builder.tomcatHome;
        this.antExecutable = builder.antExecutable;
        this.sourceRoots = List.copyOf(builder.sourceRoots);
        this.webRoot = builder.webRoot;
        this.webInf = builder.webInf;
        this.libraryRoots = List.copyOf(builder.libraryRoots);
        this.reloadStrategy = builder.reloadStrategy;
        this.tomcatManagerUrl = builder.tomcatManagerUrl;
    }

    public static NonInteractiveOptions from(CommandLine commandLine, Path defaultRoot) {
        Path root = commandLine.option("root")
                .map(value -> PathUtils.resolve(defaultRoot, value))
                .orElse(defaultRoot.toAbsolutePath().normalize());

        Builder builder = builder(root);
        builder.interactive(commandLine.hasOption("interactive"));
        commandLine.option("app").ifPresent(builder::appName);
        commandLine.option("context").ifPresent(builder::contextPath);
        commandLine.option("context-path").ifPresent(builder::contextPath);
        commandLine.option("java").map(NonInteractiveOptions::parseInteger).ifPresent(builder::javaRelease);
        commandLine.option("jdk").map(value -> PathUtils.resolve(root, value)).ifPresent(builder::jdkHome);
        commandLine.option("jdk-home").map(value -> PathUtils.resolve(root, value)).ifPresent(builder::jdkHome);
        commandLine.option("tomcat").map(value -> PathUtils.resolve(root, value)).ifPresent(builder::tomcatHome);
        commandLine.option("ant").map(value -> PathUtils.resolve(root, value)).ifPresent(builder::antExecutable);
        commandLine.option("web").map(value -> PathUtils.resolve(root, value)).ifPresent(builder::webRoot);
        commandLine.option("webinf").map(value -> PathUtils.resolve(root, value)).ifPresent(builder::webInf);
        commandLine.option("web-inf").map(value -> PathUtils.resolve(root, value)).ifPresent(builder::webInf);
        commandLine.option("reload-strategy").map(ReloadStrategy::parse).ifPresent(builder::reloadStrategy);
        commandLine.option("tomcat-manager-url").ifPresent(builder::tomcatManagerUrl);
        commandLine.option("manager-url").ifPresent(builder::tomcatManagerUrl);

        List<Path> sourceRoots = new ArrayList<>();
        for (String value : commandLine.options("src")) {
            sourceRoots.addAll(PathUtils.parsePathList(root, value));
        }
        for (String value : commandLine.options("source")) {
            sourceRoots.addAll(PathUtils.parsePathList(root, value));
        }
        builder.sourceRoots(sourceRoots);

        List<Path> libraryRoots = new ArrayList<>();
        for (String value : commandLine.options("lib")) {
            libraryRoots.addAll(PathUtils.parsePathList(root, value));
        }
        for (String value : commandLine.options("libs")) {
            libraryRoots.addAll(PathUtils.parsePathList(root, value));
        }
        builder.libraryRoots(libraryRoots);

        return builder.build();
    }

    public static Builder builder(Path projectRoot) {
        return new Builder(projectRoot.toAbsolutePath().normalize());
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expected integer value, got: " + value);
        }
    }

    public Builder toBuilder() {
        return new Builder(projectRoot)
                .interactive(interactive)
                .appName(appName)
                .contextPath(contextPath)
                .javaRelease(javaRelease)
                .jdkHome(jdkHome)
                .tomcatHome(tomcatHome)
                .antExecutable(antExecutable)
                .sourceRoots(sourceRoots)
                .webRoot(webRoot)
                .webInf(webInf)
                .libraryRoots(libraryRoots)
                .reloadStrategy(reloadStrategy)
                .tomcatManagerUrl(tomcatManagerUrl);
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public boolean interactive() {
        return interactive;
    }

    public Optional<String> appName() {
        return appName;
    }

    public Optional<String> contextPath() {
        return contextPath;
    }

    public Optional<Integer> javaRelease() {
        return javaRelease;
    }

    public Optional<Path> jdkHome() {
        return jdkHome;
    }

    public Optional<Path> tomcatHome() {
        return tomcatHome;
    }

    public Optional<Path> antExecutable() {
        return antExecutable;
    }

    public List<Path> sourceRoots() {
        return sourceRoots;
    }

    public Optional<Path> webRoot() {
        return webRoot;
    }

    public Optional<Path> webInf() {
        return webInf;
    }

    public List<Path> libraryRoots() {
        return libraryRoots;
    }

    public Optional<ReloadStrategy> reloadStrategy() {
        return reloadStrategy;
    }

    public Optional<String> tomcatManagerUrl() {
        return tomcatManagerUrl;
    }

    public static final class Builder {
        private final Path projectRoot;
        private boolean interactive;
        private Optional<String> appName = Optional.empty();
        private Optional<String> contextPath = Optional.empty();
        private Optional<Integer> javaRelease = Optional.empty();
        private Optional<Path> jdkHome = Optional.empty();
        private Optional<Path> tomcatHome = Optional.empty();
        private Optional<Path> antExecutable = Optional.empty();
        private List<Path> sourceRoots = List.of();
        private Optional<Path> webRoot = Optional.empty();
        private Optional<Path> webInf = Optional.empty();
        private List<Path> libraryRoots = List.of();
        private Optional<ReloadStrategy> reloadStrategy = Optional.empty();
        private Optional<String> tomcatManagerUrl = Optional.empty();

        private Builder(Path projectRoot) {
            this.projectRoot = projectRoot;
        }

        public Builder interactive(boolean interactive) {
            this.interactive = interactive;
            return this;
        }

        public Builder appName(String appName) {
            this.appName = Optional.ofNullable(appName).filter(value -> !value.isBlank());
            return this;
        }

        public Builder appName(Optional<String> appName) {
            this.appName = appName;
            return this;
        }

        public Optional<String> appName() {
            return appName;
        }

        public Builder contextPath(String contextPath) {
            this.contextPath = Optional.ofNullable(contextPath).filter(value -> !value.isBlank()).map(NonInteractiveOptions::normalizeContextPath);
            return this;
        }

        public Builder contextPath(Optional<String> contextPath) {
            this.contextPath = contextPath.map(NonInteractiveOptions::normalizeContextPath);
            return this;
        }

        public Optional<String> contextPath() {
            return contextPath;
        }

        public Builder javaRelease(Integer javaRelease) {
            this.javaRelease = Optional.ofNullable(javaRelease);
            return this;
        }

        public Builder javaRelease(Optional<Integer> javaRelease) {
            this.javaRelease = javaRelease;
            return this;
        }

        public Optional<Integer> javaRelease() {
            return javaRelease;
        }

        public Builder jdkHome(Path jdkHome) {
            this.jdkHome = Optional.ofNullable(jdkHome).map(Path::normalize);
            return this;
        }

        public Builder jdkHome(Optional<Path> jdkHome) {
            this.jdkHome = jdkHome.map(Path::normalize);
            return this;
        }

        public Optional<Path> jdkHome() {
            return jdkHome;
        }

        public Builder tomcatHome(Path tomcatHome) {
            this.tomcatHome = Optional.ofNullable(tomcatHome).map(Path::normalize);
            return this;
        }

        public Builder tomcatHome(Optional<Path> tomcatHome) {
            this.tomcatHome = tomcatHome.map(Path::normalize);
            return this;
        }

        public Optional<Path> tomcatHome() {
            return tomcatHome;
        }

        public Builder antExecutable(Path antExecutable) {
            this.antExecutable = Optional.ofNullable(antExecutable).map(Path::normalize);
            return this;
        }

        public Builder antExecutable(Optional<Path> antExecutable) {
            this.antExecutable = antExecutable.map(Path::normalize);
            return this;
        }

        public Optional<Path> antExecutable() {
            return antExecutable;
        }

        public Builder sourceRoots(List<Path> sourceRoots) {
            this.sourceRoots = normalizePaths(sourceRoots);
            return this;
        }

        public List<Path> sourceRoots() {
            return sourceRoots;
        }

        public Builder webRoot(Path webRoot) {
            this.webRoot = Optional.ofNullable(webRoot).map(Path::normalize);
            return this;
        }

        public Builder webRoot(Optional<Path> webRoot) {
            this.webRoot = webRoot.map(Path::normalize);
            return this;
        }

        public Optional<Path> webRoot() {
            return webRoot;
        }

        public Builder webInf(Path webInf) {
            this.webInf = Optional.ofNullable(webInf).map(Path::normalize);
            return this;
        }

        public Builder webInf(Optional<Path> webInf) {
            this.webInf = webInf.map(Path::normalize);
            return this;
        }

        public Optional<Path> webInf() {
            return webInf;
        }

        public Builder libraryRoots(List<Path> libraryRoots) {
            this.libraryRoots = normalizePaths(libraryRoots);
            return this;
        }

        public List<Path> libraryRoots() {
            return libraryRoots;
        }

        public Builder reloadStrategy(ReloadStrategy reloadStrategy) {
            this.reloadStrategy = Optional.ofNullable(reloadStrategy);
            return this;
        }

        public Builder reloadStrategy(Optional<ReloadStrategy> reloadStrategy) {
            this.reloadStrategy = reloadStrategy;
            return this;
        }

        public Optional<ReloadStrategy> reloadStrategy() {
            return reloadStrategy;
        }

        public Builder tomcatManagerUrl(String tomcatManagerUrl) {
            this.tomcatManagerUrl = Optional.ofNullable(tomcatManagerUrl).filter(value -> !value.isBlank());
            return this;
        }

        public Builder tomcatManagerUrl(Optional<String> tomcatManagerUrl) {
            this.tomcatManagerUrl = tomcatManagerUrl;
            return this;
        }

        public Optional<String> tomcatManagerUrl() {
            return tomcatManagerUrl;
        }

        public NonInteractiveOptions build() {
            return new NonInteractiveOptions(this);
        }

        private static List<Path> normalizePaths(List<Path> paths) {
            return paths.stream().map(Path::normalize).distinct().toList();
        }
    }

    private static String normalizeContextPath(String contextPath) {
        String trimmed = contextPath.trim();
        if (trimmed.equals("/")) {
            return trimmed;
        }
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }
}