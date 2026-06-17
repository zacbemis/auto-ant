package com.gei.autoant.model;

import java.nio.file.Path;
import java.util.Optional;

public record ToolConfig(Optional<Path> antExecutable, Optional<Path> tomcatHome, Optional<Integer> javaRelease) {
}