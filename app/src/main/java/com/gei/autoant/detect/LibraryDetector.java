package com.gei.autoant.detect;

import com.gei.autoant.model.DetectionResult;
import com.gei.autoant.model.LibraryRoot;
import com.gei.autoant.model.WebRoot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LibraryDetector {
    private static final List<String> CANDIDATES = List.of(
            "lib",
            "WEB-INF/lib",
            "web/WEB-INF/lib",
            "WebContent/WEB-INF/lib",
            "src/main/webapp/WEB-INF/lib"
    );

    public DetectionResult<List<LibraryRoot>> detect(Path projectRoot, DetectionResult<WebRoot> webRoot) {
        Set<Path> candidates = new LinkedHashSet<>();
        for (String candidate : CANDIDATES) {
            candidates.add(projectRoot.resolve(candidate).normalize());
        }
        webRoot.value().ifPresent(root -> candidates.add(root.webInfPath().resolve("lib").normalize()));

        List<LibraryRoot> roots = new ArrayList<>();
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                roots.add(new LibraryRoot(candidate));
            }
        }

        if (roots.isEmpty()) {
            return DetectionResult.notDetected("--lib");
        }
        return DetectionResult.confident(List.copyOf(roots), "--lib");
    }
}