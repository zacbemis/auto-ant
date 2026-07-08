package com.gei.autoant.generate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

final class PropertiesMerger {
    GeneratedFile mergeMissing(Path target, String desiredContent, Path projectRoot) throws IOException {
        String relativePath = projectRoot.relativize(target).toString().replace('\\', '/');
        if (Files.notExists(target)) {
            Files.createDirectories(target.getParent() == null ? projectRoot : target.getParent());
            Files.writeString(target, desiredContent, StandardCharsets.UTF_8);
            return new GeneratedFile(target, WriteStatus.CREATED, "Created " + relativePath + ".");
        }

        String existingContent = Files.readString(target, StandardCharsets.UTF_8);
        Map<String, String> existing = activeProperties(existingContent);
        Map<String, String> desired = activeProperties(desiredContent);
        Map<String, String> missing = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            if (!existing.containsKey(entry.getKey())) {
                missing.put(entry.getKey(), entry.getValue());
            }
        }

        if (missing.isEmpty()) {
            return new GeneratedFile(target, WriteStatus.UNCHANGED, relativePath + " is up to date.");
        }

        StringBuilder updated = new StringBuilder(existingContent);
        if (!existingContent.endsWith("\n") && !existingContent.endsWith("\r")) {
            updated.append(System.lineSeparator());
        }
        updated.append(System.lineSeparator()).append("# auto-ant update: added missing keys").append(System.lineSeparator());
        for (Map.Entry<String, String> entry : missing.entrySet()) {
            updated.append(entry.getKey()).append('=').append(entry.getValue()).append(System.lineSeparator());
        }
        Files.writeString(target, updated.toString(), StandardCharsets.UTF_8);
        return new GeneratedFile(target, WriteStatus.UPDATED,
                "Updated " + relativePath + ": added " + missing.size() + " missing key" + (missing.size() == 1 ? "" : "s") + ".");
    }

    private Map<String, String> activeProperties(String content) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                continue;
            }
            int separator = separatorIndex(line);
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.isEmpty()) {
                properties.put(key, value);
            }
        }
        return properties;
    }

    private int separatorIndex(String line) {
        int equals = line.indexOf('=');
        int colon = line.indexOf(':');
        if (equals < 0) {
            return colon;
        }
        if (colon < 0) {
            return equals;
        }
        return Math.min(equals, colon);
    }
}