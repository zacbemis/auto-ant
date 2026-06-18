package com.gei.autoant.watch;

import com.gei.autoant.cli.CommandLine;
import com.gei.autoant.util.PathUtils;
import com.gei.autoant.util.PropertiesUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

public record WatchConfiguration(Path projectRoot, List<Path> watchRoots, Duration debounceDelay) {
    private static final long DEFAULT_DEBOUNCE_MS = 750;

    public WatchConfiguration {
        projectRoot = projectRoot.toAbsolutePath().normalize();
        watchRoots = List.copyOf(new LinkedHashSet<>(watchRoots.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList()));
        if (watchRoots.isEmpty()) {
            throw new IllegalArgumentException("No watch roots are configured. Check src.dirs and web.dir in auto-ant.properties.");
        }
        if (debounceDelay.isNegative()) {
            throw new IllegalArgumentException("watch.debounce.ms must not be negative.");
        }
    }

    public static WatchConfiguration load(Path defaultProjectRoot, CommandLine commandLine) throws IOException {
        Path projectRoot = commandLine.option("root")
                .map(value -> PathUtils.resolve(defaultProjectRoot, value))
                .orElse(defaultProjectRoot)
                .toAbsolutePath()
                .normalize();

        Path propertiesPath = projectRoot.resolve("auto-ant.properties");
        if (!Files.exists(propertiesPath)) {
            throw new IllegalArgumentException("auto-ant.properties not found in "
                    + PathUtils.toPortableString(projectRoot)
                    + ". Run auto-ant init first.");
        }

        Properties properties = PropertiesUtils.loadIfExists(propertiesPath);
        List<Path> roots = new ArrayList<>();
        String sourceDirs = properties.getProperty("src.dirs", "");
        if (!sourceDirs.isBlank()) {
            roots.addAll(PathUtils.parsePathList(projectRoot, sourceDirs));
        }
        String webDir = properties.getProperty("web.dir", "");
        if (!webDir.isBlank()) {
            roots.add(PathUtils.resolve(projectRoot, webDir));
        }

        return new WatchConfiguration(projectRoot, roots, debounceDelay(properties, commandLine));
    }

    private static Duration debounceDelay(Properties properties, CommandLine commandLine) {
        String value = commandLine.option("debounce-ms")
                .orElseGet(() -> properties.getProperty("watch.debounce.ms", Long.toString(DEFAULT_DEBOUNCE_MS)));
        try {
            return Duration.ofMillis(Long.parseLong(value.trim()));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("watch.debounce.ms must be a whole number of milliseconds: " + value);
        }
    }
}