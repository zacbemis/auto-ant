package com.gei.autoant.detect;

import com.gei.autoant.model.DetectionResult;
import com.gei.autoant.model.ServletNamespace;
import com.gei.autoant.model.SourceRoot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class ServletNamespaceDetector {
    public DetectionResult<ServletNamespace> detect(List<SourceRoot> sourceRoots) {
        AtomicBoolean javaxFound = new AtomicBoolean(false);
        AtomicBoolean jakartaFound = new AtomicBoolean(false);

        for (SourceRoot sourceRoot : sourceRoots) {
            scanRoot(sourceRoot.path(), javaxFound, jakartaFound);
        }

        if (javaxFound.get() && jakartaFound.get()) {
            return DetectionResult.warning(
                    ServletNamespace.BOTH,
                    null,
                    "Both javax.servlet and jakarta.servlet imports were found. Confirm the intended Tomcat major version."
            );
        }
        if (javaxFound.get()) {
            return DetectionResult.confident(ServletNamespace.JAVAX, null);
        }
        if (jakartaFound.get()) {
            return DetectionResult.confident(ServletNamespace.JAKARTA, null);
        }
        return DetectionResult.notDetected(null, List.of("No javax.servlet or jakarta.servlet references were found."));
    }

    private void scanRoot(Path root, AtomicBoolean javaxFound, AtomicBoolean jakartaFound) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java"))
                    .limit(10_000)
                    .forEach(path -> scanFile(path, javaxFound, jakartaFound));
        } catch (IOException ignored) {
            // Detection should be best-effort and non-destructive.
        }
    }

    private void scanFile(Path path, AtomicBoolean javaxFound, AtomicBoolean jakartaFound) {
        if (javaxFound.get() && jakartaFound.get()) {
            return;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            if (text.contains("javax.servlet")) {
                javaxFound.set(true);
            }
            if (text.contains("jakarta.servlet")) {
                jakartaFound.set(true);
            }
        } catch (IOException ignored) {
            // Ignore unreadable files during detection.
        }
    }
}