package com.gei.autoant.util;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PathUtils {
    private PathUtils() {
    }

    public static Path resolve(Path root, String value) {
        Path path = Path.of(value.trim());
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return root.resolve(path).normalize();
    }

    public static List<Path> parsePathList(Path root, String value) {
        List<Path> paths = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paths.add(resolve(root, trimmed));
            }
        }
        return paths;
    }

    public static String display(Path root, Path path) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (normalizedPath.startsWith(normalizedRoot)) {
            Path relative = normalizedRoot.relativize(normalizedPath);
            if (relative.toString().isEmpty()) {
                return ".";
            }
            return toPortableString(relative);
        }
        return toPortableString(normalizedPath);
    }

    public static String toPortableString(Path path) {
        return path.toString().replace('\\', '/');
    }
}