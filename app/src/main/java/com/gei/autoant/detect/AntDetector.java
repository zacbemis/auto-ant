package com.gei.autoant.detect;

import com.gei.autoant.model.DetectionResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AntDetector {
    public DetectionResult<Path> detect() {
        String antHome = System.getenv("ANT_HOME");
        if (antHome != null && !antHome.isBlank()) {
            Path executable = Path.of(antHome, "bin", executableName()).toAbsolutePath().normalize();
            if (Files.isRegularFile(executable)) {
                return DetectionResult.confident(executable, "--ant");
            }
        }

        for (Path candidate : pathCandidates()) {
            if (Files.isRegularFile(candidate)) {
                return DetectionResult.confident(candidate, "--ant");
            }
        }

        return DetectionResult.notDetected("--ant");
    }

    private List<Path> pathCandidates() {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return List.of();
        }
        List<Path> candidates = new ArrayList<>();
        for (String part : path.split(java.io.File.pathSeparator)) {
            if (!part.isBlank()) {
                candidates.add(Path.of(part, executableName()).toAbsolutePath().normalize());
                if (isWindows()) {
                    candidates.add(Path.of(part, "ant.cmd").toAbsolutePath().normalize());
                    candidates.add(Path.of(part, "ant.exe").toAbsolutePath().normalize());
                }
            }
        }
        return candidates;
    }

    public static String executableName() {
        return isWindows() ? "ant.bat" : "ant";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}