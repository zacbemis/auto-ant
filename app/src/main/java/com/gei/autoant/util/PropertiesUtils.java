package com.gei.autoant.util;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class PropertiesUtils {
    private PropertiesUtils() {
    }

    public static Properties loadIfExists(Path path) throws IOException {
        Properties properties = new Properties();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    public static void store(Path path, Properties properties, String comments) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            properties.store(writer, comments);
        }
    }
}