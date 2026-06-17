package com.gei.autoant.detect;

import com.gei.autoant.model.DetectionResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TomcatDetector {
    private static final List<String> ENV_NAMES = List.of("CATALINA_HOME", "CATALINA_BASE", "TOMCAT_HOME");

    public DetectionResult<Path> detect() {
        Map<String, String> env = System.getenv();
        for (String envName : ENV_NAMES) {
            String value = env.get(envName);
            if (value != null && !value.isBlank()) {
                Path path = Path.of(value).toAbsolutePath().normalize();
                if (looksLikeTomcat(path)) {
                    return DetectionResult.confident(path, "--tomcat");
                }
                return DetectionResult.warning(path, "--tomcat", envName + " is set, but the path does not look like a Tomcat home.");
            }
        }

        for (Path candidate : commonCandidates()) {
            if (looksLikeTomcat(candidate)) {
                return DetectionResult.confident(candidate, "--tomcat");
            }
        }

        return DetectionResult.notDetected("--tomcat");
    }

    private boolean looksLikeTomcat(Path path) {
        return Files.isDirectory(path)
                && Files.isDirectory(path.resolve("webapps"))
                && (Files.isDirectory(path.resolve("bin")) || Files.isDirectory(path.resolve("conf")));
    }

    private List<Path> commonCandidates() {
        List<Path> candidates = new ArrayList<>();
        String userHome = System.getProperty("user.home", "");
        candidates.add(Path.of("C:/apache-tomcat-9.0.118"));
        candidates.add(Path.of("C:/apache-tomcat-9"));
        candidates.add(Path.of("C:/apache-tomcat-10"));
        candidates.add(Path.of("C:/tools/apache-tomcat-9"));
        candidates.add(Path.of("C:/tools/apache-tomcat-10"));
        if (!userHome.isBlank()) {
            candidates.add(Path.of(userHome, "apache-tomcat-9"));
            candidates.add(Path.of(userHome, "apache-tomcat-10"));
        }
        candidates.add(Path.of("/usr/local/tomcat"));
        candidates.add(Path.of("/opt/tomcat"));
        return candidates.stream().map(Path::toAbsolutePath).map(Path::normalize).toList();
    }
}