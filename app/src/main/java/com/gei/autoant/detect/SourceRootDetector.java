package com.gei.autoant.detect;

import com.gei.autoant.model.DetectionResult;
import com.gei.autoant.model.SourceRoot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SourceRootDetector {
    private static final List<String> CANDIDATES = List.of(
            "src/main/java",
            "src/java",
            "src",
            "source",
            "Source Packages",
            "JavaSource",
            "generated-src"
    );

    public DetectionResult<List<SourceRoot>> detect(Path projectRoot) {
        List<SourceRoot> roots = new ArrayList<>();
        for (String candidate : CANDIDATES) {
            Path path = projectRoot.resolve(candidate).normalize();
            if (Files.isDirectory(path)) {
                roots.add(new SourceRoot(path));
            }
        }

        roots = removeParentRootsWhenSpecificChildExists(roots);

        if (roots.isEmpty()) {
            return DetectionResult.notDetected("--src", List.of("No Java source root candidate was found."));
        }
        if (roots.size() == 1) {
            return DetectionResult.confident(List.copyOf(roots), "--src");
        }
        return DetectionResult.warning(
                List.copyOf(roots),
                "--src",
                "Multiple Java source roots were detected. Confirm src.dirs before generating build files."
        );
    }

    private List<SourceRoot> removeParentRootsWhenSpecificChildExists(List<SourceRoot> roots) {
        return roots.stream()
                .filter(candidate -> roots.stream().noneMatch(other -> isMoreSpecificChild(candidate.path(), other.path())))
                .toList();
    }

    private boolean isMoreSpecificChild(Path candidate, Path other) {
        return !candidate.equals(other)
                && other.startsWith(candidate)
                && candidate.getFileName() != null
                && candidate.getFileName().toString().equalsIgnoreCase("src");
    }
}