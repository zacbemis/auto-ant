package com.gei.autoant.watch;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public final class RecursiveWatchRegistrar {
    private final WatchService watchService;
    private final Map<WatchKey, Path> directories = new HashMap<>();

    public RecursiveWatchRegistrar(WatchService watchService) {
        this.watchService = Objects.requireNonNull(watchService, "watchService");
    }

    public void registerAll(Path root) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            return;
        }

        Files.walkFileTree(normalizedRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                register(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public Optional<Path> directoryFor(WatchKey key) {
        return Optional.ofNullable(directories.get(key));
    }

    public List<Path> registeredDirectories() {
        return List.copyOf(directories.values());
    }

    private void register(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (directories.containsValue(normalized)) {
            return;
        }
        WatchKey key = normalized.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
        directories.put(key, normalized);
    }
}