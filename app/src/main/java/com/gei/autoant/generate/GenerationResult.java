package com.gei.autoant.generate;

import java.nio.file.Path;
import java.util.List;

public record GenerationResult(Path buildFile, List<GeneratedFile> files) {
    public GenerationResult {
        buildFile = buildFile.toAbsolutePath().normalize();
        files = List.copyOf(files);
    }
}