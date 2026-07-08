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

class UpdateGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void updateRefreshesGeneratedFilesAndPreservesUserChanges() throws IOException {
        createSimpleProject();
        Path tomcatHome = tempDir.resolve("apache-tomcat");
        Path jdkHome = tempDir.resolve("jdk-21");
        Files.writeString(tempDir.resolve("auto-ant.build.xml"), "<project name=\"old\"/>\n");
        Files.writeString(tempDir.resolve("auto-ant.user.xml"), "<project name=\"custom\"/>\n");
        Files.writeString(tempDir.resolve("auto-ant.properties"), "app.name=CustomApp\ncontext.path=/custom\njava.release=8\n");
        Files.writeString(tempDir.resolve("auto-ant.local.properties"), "tomcat.home=" + portable(tomcatHome) + "\njdk.home=" + portable(jdkHome) + "\ntomcat.manager.password=secret\n");
        Files.createDirectories(tempDir.resolve(".vscode"));
        Files.writeString(tempDir.resolve(".vscode/tasks.json"), "{\n"
                + "  \"version\": \"2.0.0\",\n"
                + "  \"inputs\": [\n"
                + "    {\"id\": \"customInput\", \"type\": \"promptString\"}\n"
                + "  ],\n"
                + "  \"tasks\": [\n"
                + "    {\n"
                + "      \"label\": \"custom task\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": \"echo custom\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: sync web\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": \"old sync\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n");
        Files.writeString(tempDir.resolve(".vscode/settings.json"), "{\n"
                + "  \"editor.tabSize\": 2,\n"
                + "  \"filewatcher.commands\": []\n"
                + "}\n");

        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir)
                .appName("CustomApp")
                .contextPath("/custom")
                .javaRelease(8)
                .tomcatHome(tomcatHome)
                .jdkHome(jdkHome)
                .build());

        GenerationResult result = new UpdateGenerator(tempDir).update(model);

        assertEquals(7, result.files().size());
        assertTrue(Files.readString(tempDir.resolve("auto-ant.build.xml")).contains("AUTO-ANT GENERATED FILE - DO NOT EDIT DIRECTLY"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.build.xml")).contains("<import file=\"auto-ant.user.xml\" optional=\"true\"/>"));
        assertTrue(Files.list(tempDir).anyMatch(path -> path.getFileName().toString().startsWith("auto-ant.build.xml.auto-ant-backup-")));
        assertEquals("<project name=\"custom\"/>\n", Files.readString(tempDir.resolve("auto-ant.user.xml")));

        String sharedProperties = Files.readString(tempDir.resolve("auto-ant.properties"));
        assertTrue(sharedProperties.contains("app.name=CustomApp"));
        assertTrue(sharedProperties.contains("java.release=8"));
        assertTrue(sharedProperties.contains("# auto-ant update: added missing keys"));
        assertTrue(sharedProperties.contains("build.dir=build"));

        String localProperties = Files.readString(tempDir.resolve("auto-ant.local.properties"));
        assertTrue(localProperties.contains("tomcat.home=" + portable(tomcatHome)));
        assertTrue(localProperties.contains("jdk.home=" + portable(jdkHome)));
        assertTrue(localProperties.contains("tomcat.manager.password=secret"));
        assertTrue(localProperties.contains("# auto-ant update: added missing keys"));
        assertTrue(localProperties.contains("catalina.base=" + portable(tomcatHome)));

        String tasksJson = Files.readString(tempDir.resolve(".vscode/tasks.json"));
        assertTrue(tasksJson.contains("AUTO-ANT MANAGED TASKS - DO NOT EDIT AUTO-ANT TASKS DIRECTLY"));
        assertTrue(tasksJson.contains("\"inputs\""));
        assertTrue(tasksJson.contains("customInput"));
        assertTrue(tasksJson.contains("\"label\": \"custom task\""));
        assertTrue(tasksJson.contains("\"label\": \"auto-ant: sync web\""));
        assertTrue(tasksJson.contains("auto-ant.build.xml"));

        String settingsJson = Files.readString(tempDir.resolve(".vscode/settings.json"));
        assertTrue(settingsJson.contains("AUTO-ANT MANAGED SETTINGS - EDIT WITH CARE"));
        assertTrue(settingsJson.contains("\"editor.tabSize\": 2"));
        assertTrue(settingsJson.contains("\"filewatcher.commands\""));
    }

    private void createSimpleProject() throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve("web/WEB-INF"));
        Files.writeString(tempDir.resolve("web/WEB-INF/web.xml"), "<web-app/>\n");
    }

    private String portable(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }
}