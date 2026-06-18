package com.gei.autoant.watch;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class FileClassifier {
    private static final Set<String> FRONTEND_EXTENSIONS = Set.of(
            "jsp",
            "jspf",
            "html",
            "htm",
            "css",
            "js",
            "ts",
            "png",
            "jpg",
            "jpeg",
            "gif",
            "svg",
            "webp",
            "ico",
            "woff",
            "woff2"
    );

    public ChangeKind classify(Path path) {
        if (path == null || path.getFileName() == null) {
            return ChangeKind.IGNORED;
        }

        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        String extension = extension(fileName);

        if ("web.xml".equals(fileName) || "context.xml".equals(fileName)) {
            return ChangeKind.CONFIG;
        }
        if ("properties".equals(extension) || "xml".equals(extension) || "jar".equals(extension)) {
            return ChangeKind.CONFIG;
        }
        if ("java".equals(extension)) {
            return ChangeKind.BACKEND;
        }
        if (FRONTEND_EXTENSIONS.contains(extension)) {
            return ChangeKind.FRONTEND;
        }
        return ChangeKind.IGNORED;
    }

    private String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1);
    }
}