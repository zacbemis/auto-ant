package com.gei.autoant.watch;

import com.gei.autoant.run.CommandResult;
import com.gei.autoant.util.PathUtils;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public final class ProjectWatcher {
    private static final String SYNC_WEB_TARGET = "sync-web";

    private final Path projectRoot;
    private final List<Path> watchRoots;
    private final Duration debounceDelay;
    private final TargetRunner targetRunner;
    private final PrintStream out;
    private final PrintStream err;
    private final FileClassifier fileClassifier;

    public ProjectWatcher(
            Path projectRoot,
            List<Path> watchRoots,
            Duration debounceDelay,
            TargetRunner targetRunner,
            PrintStream out,
            PrintStream err
    ) {
        this(projectRoot, watchRoots, debounceDelay, targetRunner, out, err, new FileClassifier());
    }

    ProjectWatcher(
            Path projectRoot,
            List<Path> watchRoots,
            Duration debounceDelay,
            TargetRunner targetRunner,
            PrintStream out,
            PrintStream err,
            FileClassifier fileClassifier
    ) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
        this.watchRoots = List.copyOf(Objects.requireNonNull(watchRoots, "watchRoots"));
        this.debounceDelay = Objects.requireNonNull(debounceDelay, "debounceDelay");
        this.targetRunner = Objects.requireNonNull(targetRunner, "targetRunner");
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
        this.fileClassifier = Objects.requireNonNull(fileClassifier, "fileClassifier");
    }

    public void watch() throws IOException, InterruptedException {
        List<Path> existingRoots = watchRoots.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isDirectory)
                .distinct()
                .toList();
        if (existingRoots.isEmpty()) {
            throw new IllegalArgumentException("None of the configured watch roots exist. Check src.dirs and web.dir in auto-ant.properties.");
        }

        try (WatchService watchService = FileSystems.getDefault().newWatchService();
             Debouncer debouncer = new Debouncer(debounceDelay, this::handleBatchSafely)) {
            RecursiveWatchRegistrar registrar = new RecursiveWatchRegistrar(watchService);
            for (Path root : existingRoots) {
                registrar.registerAll(root);
            }
            printHeader(existingRoots);

            while (!Thread.currentThread().isInterrupted()) {
                WatchKey key = watchService.take();
                Path directory = registrar.directoryFor(key).orElse(null);
                if (directory != null) {
                    collectEvents(key, directory, registrar, debouncer);
                }
                if (!key.reset()) {
                    // The directory was deleted or is otherwise no longer watchable. Other registered
                    // directories can still produce events, so keep the watcher alive.
                }
            }
        }
    }

    void handleBatch(ChangeBatch batch) throws IOException, InterruptedException {
        if (batch.isEmpty()) {
            return;
        }

        out.println();
        out.println("Changed:");
        for (Path path : batch.paths()) {
            out.println("  " + PathUtils.display(projectRoot, path));
        }

        ChangeKind kind = batch.classify(fileClassifier);
        if (kind == ChangeKind.FRONTEND) {
            out.println();
            out.println("Detected frontend change.");
            runTarget(SYNC_WEB_TARGET);
        } else if (kind == ChangeKind.BACKEND) {
            out.println();
            out.println("Detected backend change. compile-hot/reload support is planned for the backend watcher milestone.");
        } else if (kind == ChangeKind.CONFIG) {
            out.println();
            out.println("Detected config/library change. deploy/reload support is planned for the backend watcher milestone.");
        } else {
            out.println();
            out.println("No actionable changes detected.");
        }
    }

    private void collectEvents(
            WatchKey key,
            Path directory,
            RecursiveWatchRegistrar registrar,
            Debouncer debouncer
    ) throws IOException {
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == OVERFLOW) {
                continue;
            }

            Path changed = directory.resolve(castContext(event)).toAbsolutePath().normalize();
            if (event.kind() == ENTRY_CREATE && Files.isDirectory(changed)) {
                registrar.registerAll(changed);
            }
            debouncer.submit(changed);
        }
    }

    private void handleBatchSafely(ChangeBatch batch) {
        try {
            handleBatch(batch);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            err.println("watch: interrupted");
        } catch (IOException ex) {
            err.println("watch: failed to run Ant target: " + ex.getMessage());
        }
    }

    private void runTarget(String target) throws IOException, InterruptedException {
        out.println("Running: ant " + target);
        CommandResult result = targetRunner.run(target);
        if (result.exitCode() == 0) {
            out.println("Done.");
        } else {
            err.println("watch: ant " + target + " failed with exit code " + result.exitCode());
        }
    }

    private void printHeader(List<Path> existingRoots) {
        out.println("auto-ant watch");
        out.println();
        out.println("Watching:");
        for (Path root : existingRoots) {
            out.println("  " + PathUtils.display(projectRoot, root));
        }
        out.println();
        out.println("Debounce: " + debounceDelay.toMillis() + "ms");
    }

    @SuppressWarnings("unchecked")
    private Path castContext(WatchEvent<?> event) {
        return ((WatchEvent<Path>) event).context();
    }
}