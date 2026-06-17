package com.gei.autoant.generate;

import java.nio.file.Path;

public record GeneratedFile(Path path, WriteStatus status, String message) {
}