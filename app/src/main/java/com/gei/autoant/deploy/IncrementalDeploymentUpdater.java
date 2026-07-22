package com.gei.autoant.deploy;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Applies one explicitly-owned part of a rebuilt deployment snapshot to an existing live tree. */
public final class IncrementalDeploymentUpdater {
    private static final Set<String> FRONTEND = Set.of(
            "html", "htm", "css", "js", "ts", "png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "woff", "woff2");
    private static final Set<String> VIEWS = Set.of("jsp", "jspf", "tag", "tagx", "tld");
    private static final Set<String> CONFIG = Set.of("properties", "xml", "jar");

    public enum Kind {
        FRONTEND, VIEWS, CLASSES, CONFIG;

        public static Kind parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("--kind must be one of frontend, views, classes, or config.");
            }
        }
    }

    public UpdateResult update(Path desired, Path live, Kind kind) throws IOException {
        Path safeDesired = requireRealRoot(desired, "Desired snapshot");
        Path safeLive = requireRealRoot(live, "Live deployment");
        Map<String, Path> desiredFiles = collect(safeDesired, kind, true);
        Map<String, Path> liveFiles = collect(safeLive, kind, false);

        List<String> copies = desiredFiles.keySet().stream().sorted()
                .filter(relative -> changed(desiredFiles.get(relative), liveFiles.get(relative))).toList();
        List<String> deletes = liveFiles.keySet().stream().filter(relative -> !desiredFiles.containsKey(relative))
                .sorted(Comparator.reverseOrder()).toList();

        // Validate every destination before the first mutation so a linked/colliding path cannot leave a partial update.
        for (String relative : copies) validateDestination(safeLive, relative);

        int copied = 0;
        int deleted = 0;
        for (String relative : copies) {
            copyAtomically(desiredFiles.get(relative), safeLive, relative);
            copied++;
        }
        for (String relative : deletes) {
            Path target = resolveOwned(safeLive, relative);
            requireRegularFile(target, "Owned live file");
            Files.delete(target);
            deleted++;
            removeEmptyParents(target.getParent(), safeLive, kind);
        }
        return new UpdateResult(copied, deleted);
    }

    private Map<String, Path> collect(Path root, Kind kind, boolean validateAll) throws IOException {
        Map<String, Path> files = new HashMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || Files.isSymbolicLink(dir)) {
                    throw new IOException("Deployment tree contains a symbolic-link directory: " + dir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String relative = portable(root.relativize(file));
                if (attrs.isSymbolicLink() || Files.isSymbolicLink(file)) {
                    if (validateAll || owns(relative, kind)) throw new IOException("Deployment tree contains a symbolic-link file: " + file);
                    return FileVisitResult.CONTINUE;
                }
                if (!attrs.isRegularFile()) {
                    if (validateAll || owns(relative, kind)) throw new IOException("Deployment tree contains a special file: " + file);
                    return FileVisitResult.CONTINUE;
                }
                if (owns(relative, kind)) files.put(relative, file);
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    public boolean owns(String relativePath, Kind kind) {
        String path = relativePath.replace('\\', '/');
        String lower = path.toLowerCase(Locale.ROOT);
        String extension = extension(lower);
        boolean inClasses = lower.startsWith("web-inf/classes/");
        boolean inLib = lower.startsWith("web-inf/lib/");
        return switch (kind) {
            case FRONTEND -> FRONTEND.contains(extension) && !lower.startsWith("web-inf/") && !lower.startsWith("meta-inf/");
            case VIEWS -> !inClasses && !inLib && (VIEWS.contains(extension)
                    || (lower.startsWith("web-inf/") && FRONTEND.contains(extension)));
            case CLASSES -> inClasses;
            case CONFIG -> !inClasses && !ownedByFrontendOrViews(lower)
                    && (lower.equals("web-inf/web.xml") || fileName(lower).equals("context.xml") || CONFIG.contains(extension));
        };
    }

    private boolean ownedByFrontendOrViews(String path) {
        return owns(path, Kind.FRONTEND) || owns(path, Kind.VIEWS);
    }

    private boolean changed(Path source, Path target) {
        if (target == null) return true;
        try {
            requireRegularFile(target, "Owned live file");
            return Files.size(source) != Files.size(target) || Files.mismatch(source, target) != -1;
        } catch (IOException ex) {
            return true;
        }
    }

    private void copyAtomically(Path source, Path live, String relative) throws IOException {
        requireRegularFile(source, "Desired file");
        Path target = resolveOwned(live, relative);
        Path parent = target.getParent();
        createSafeDirectories(live, parent);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Owned deployment target is not a regular file: " + target);
        }
        Path temporary = Files.createTempFile(parent, ".auto-ant-develop-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void validateDestination(Path live, String relative) throws IOException {
        Path target = resolveOwned(live, relative);
        Path current = live;
        Path parent = target.getParent();
        for (Path segment : live.relativize(parent)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))) {
                throw new IOException("Deployment path contains a link or non-directory: " + current);
            }
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException("Owned deployment target is linked or not a regular file: " + target);
        }
    }

    private void createSafeDirectories(Path root, Path directory) throws IOException {
        Path current = root;
        for (Path segment : root.relativize(directory)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Deployment path contains a link or non-directory: " + current);
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private void removeEmptyParents(Path directory, Path root, Kind kind) throws IOException {
        Path current = directory;
        while (current != null && !current.equals(root) && Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
            try (var entries = Files.list(current)) {
                if (entries.findAny().isPresent()) return;
            }
            String relative = portable(root.relativize(current)) + "/placeholder";
            if (kind != Kind.CLASSES && !owns(relative, kind)) return;
            Files.delete(current);
            current = current.getParent();
        }
    }

    private Path resolveOwned(Path root, String relative) throws IOException {
        Path target = root.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
        if (!target.startsWith(root) || target.equals(root)) throw new IOException("Owned path escapes deployment root: " + relative);
        return target;
    }

    private Path requireRealRoot(Path root, String description) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw new IOException(description + " must be an existing non-linked directory: " + normalized);
        }
        return normalized.toRealPath();
    }

    private void requireRegularFile(Path file, String description) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IOException(description + " is linked or not a regular file: " + file);
        }
    }

    private String extension(String path) {
        String name = fileName(path);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private String portable(Path path) { return path.toString().replace('\\', '/'); }

    public record UpdateResult(int copied, int deleted) {
        public boolean mutated() { return copied > 0 || deleted > 0; }
    }
}
