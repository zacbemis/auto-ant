package com.gei.autoant.detect;

import com.gei.autoant.model.DetectionResult;

public final class JavaDetector {
    public DetectionResult<Integer> detect() {
        String version = System.getProperty("java.specification.version", "");
        if (version.isBlank()) {
            return DetectionResult.notDetected("--java");
        }
        try {
            int release;
            if (version.startsWith("1.")) {
                release = Integer.parseInt(version.substring(2));
            } else {
                int dot = version.indexOf('.');
                release = Integer.parseInt(dot >= 0 ? version.substring(0, dot) : version);
            }
            return DetectionResult.confident(release, "--java");
        } catch (NumberFormatException ex) {
            return DetectionResult.notDetected("--java", java.util.List.of("Could not parse Java version: " + version));
        }
    }
}