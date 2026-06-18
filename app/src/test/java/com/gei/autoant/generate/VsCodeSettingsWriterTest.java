package com.gei.autoant.generate;

import com.gei.autoant.detect.ProjectDetector;
import com.gei.autoant.prompt.NonInteractiveOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VsCodeSettingsWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesFileWatcherCommandForFrontendSaves() throws IOException {
        createProject("web");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        String settings = new VsCodeSettingsWriter().write(model);

        assertTrue(settings.contains("\"filewatcher.isSyncRunEvents\": true"));
        assertTrue(settings.contains("\"filewatcher.commands\""));
        assertTrue(settings.contains("\"event\": \"onFileChange\""));
        assertTrue(settings.contains("\"isAsync\": false"));
        assertTrue(settings.contains("\"cmd\": \"ant sync-web\""));
        assertTrue(settings.contains("jsp|jspf|html|htm|css|js|ts"));
        assertTrue(settings.contains("web"));
    }

    @Test
    void preservesDetectedWebDirectoryCaseInGeneratedRegex() throws IOException {
        createProject("WebContent");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).webRoot(tempDir.resolve("WebContent")).build());

        String settings = new VsCodeSettingsWriter().write(model);

        assertTrue(settings.contains("WebContent"));
    }

    @Test
    void escapesRegexCharactersInWebDirectoryNames() throws IOException {
        createProject("web.v1");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).webRoot(tempDir.resolve("web.v1")).build());

        String settings = new VsCodeSettingsWriter().write(model);

        assertTrue(settings.contains("web\\\\.v1"));
    }

    private void createProject(String webDirectory) throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve(webDirectory).resolve("WEB-INF"));
        Files.writeString(tempDir.resolve(webDirectory).resolve("WEB-INF/web.xml"), "<web-app/>\n");
    }
}