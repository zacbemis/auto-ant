package com.gei.autoant.generate;

import java.util.List;

public record GenerationResult(List<GeneratedFile> files) {
    public GenerationResult {
        files = List.copyOf(files);
    }
}