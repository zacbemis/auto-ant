package com.gei.autoant.generate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GitignoreWriter {
    private static final List<String> ENTRIES = List.of(
            "auto-ant.local.properties",
            ".auto-ant/",
            "build/",
            "dist/"
    );

    public GeneratedFile update(Path gitignore) throws IOException {
        boolean existedBefore = Files.exists(gitignore);
        List<String> lines = Files.exists(gitignore)
                ? Files.readAllLines(gitignore, StandardCharsets.UTF_8)
                : new ArrayList<>();

        List<String> missing = ENTRIES.stream().filter(entry -> !lines.contains(entry)).toList();
        if (missing.isEmpty()) {
            return new GeneratedFile(gitignore, WriteStatus.UNCHANGED, "Unchanged .gitignore; auto-ant entries already present.");
        }

        List<String> updated = new ArrayList<>(lines);
        if (!updated.isEmpty() && !updated.get(updated.size() - 1).isBlank()) {
            updated.add("");
        }
        updated.add("# auto-ant");
        updated.addAll(missing);

        Files.write(gitignore, updated, StandardCharsets.UTF_8);
        WriteStatus status = existedBefore ? WriteStatus.UPDATED : WriteStatus.CREATED;
        return new GeneratedFile(gitignore, status, (existedBefore ? "Updated" : "Created") + " .gitignore auto-ant entries.");
    }
}
