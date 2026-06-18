package com.gei.autoant.generate;

import com.gei.autoant.detect.ProjectDetector;
import com.gei.autoant.prompt.NonInteractiveOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesCoreFilesAndGitignoreEntries() throws IOException {
        createSimpleProject();
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).appName("MyApp").javaRelease(25).build());

        GenerationResult result = new InitGenerator(tempDir).generate(model);

        assertEquals(6, result.files().size());
        assertTrue(Files.exists(tempDir.resolve("build.xml")));
        assertTrue(Files.exists(tempDir.resolve("auto-ant.properties")));
        assertTrue(Files.exists(tempDir.resolve("auto-ant.local.properties")));
        assertTrue(Files.exists(tempDir.resolve(".vscode/tasks.json")));
        assertTrue(Files.exists(tempDir.resolve(".vscode/settings.json")));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("target name=\"clean-build\""));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("target name=\"deploy-exploded\""));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("app.name=MyApp"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("java.release=25"));
        String settingsJson = Files.readString(tempDir.resolve(".vscode/settings.json"));
        assertTrue(settingsJson.contains("\"filewatcher.commands\""));
        assertTrue(settingsJson.contains("\"event\": \"onFileChange\""));
        assertTrue(settingsJson.contains("\"cmd\": \"ant sync-web\""));
        assertTrue(settingsJson.contains("jsp|jspf|html"));
        assertTrue(Files.readString(tempDir.resolve(".gitignore")).contains("auto-ant.local.properties"));
    }

    @Test
    void doesNotOverwriteExistingFiles() throws IOException {
        createSimpleProject();
        Files.writeString(tempDir.resolve("build.xml"), "existing build\n");
        Files.createDirectories(tempDir.resolve(".vscode"));
        Files.writeString(tempDir.resolve(".vscode/tasks.json"), "existing tasks\n");
        Files.writeString(tempDir.resolve(".vscode/settings.json"), "existing settings\n");

        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        new InitGenerator(tempDir).generate(model);

        assertEquals("existing build\n", Files.readString(tempDir.resolve("build.xml")));
        assertEquals("existing tasks\n", Files.readString(tempDir.resolve(".vscode/tasks.json")));
        assertEquals("existing settings\n", Files.readString(tempDir.resolve(".vscode/settings.json")));
        assertTrue(Files.exists(tempDir.resolve("build.xml.auto-ant-new")));
        assertTrue(Files.exists(tempDir.resolve(".vscode/tasks.auto-ant-new.json")));
        assertTrue(Files.exists(tempDir.resolve(".vscode/settings.auto-ant-new.json")));
    }

    @Test
    void gitignoreUpdatesAreIdempotent() throws IOException {
        createSimpleProject();
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        new InitGenerator(tempDir).generate(model);
        new InitGenerator(tempDir).generate(model);

        String gitignore = Files.readString(tempDir.resolve(".gitignore"));
        assertEquals(1, countOccurrences(gitignore, "auto-ant.local.properties"));
        assertEquals(1, countOccurrences(gitignore, "build/"));
        assertEquals(1, countOccurrences(gitignore, "dist/"));
    }

    private void createSimpleProject() throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve("web/WEB-INF"));
        Files.writeString(tempDir.resolve("web/WEB-INF/web.xml"), "<web-app/>\n");
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}