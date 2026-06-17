package com.gei.autoant.model;

import java.nio.file.Path;

public record WebRoot(Path path, Path webInfPath, boolean webXmlPresent, int score) {
}