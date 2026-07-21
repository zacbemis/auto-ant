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
        assertTrue(tasksJson.contains("\"label\": \"auto-ant: reconcile web changes\""));
        assertTrue(tasksJson.contains("auto-ant.build.xml"));

        String settingsJson = Files.readString(tempDir.resolve(".vscode/settings.json"));
        assertTrue(settingsJson.contains("AUTO-ANT MANAGED SETTINGS - EDIT WITH CARE"));
        assertTrue(settingsJson.contains("\"editor.tabSize\": 2"));
        assertTrue(settingsJson.contains("\"filewatcher.commands\""));
    }

    @Test
    void updateCanApplyExplicitPropertyOverrides() throws IOException {
        createSimpleProject();
        Path oldTomcatHome = tempDir.resolve("old-tomcat");
        Path newTomcatHome = tempDir.resolve("apache-tomcat-9.0.120");
        Files.writeString(tempDir.resolve("auto-ant.properties"), "app.name=OldApp\ncontext.path=/old\njava.release=8\n");
        Files.writeString(tempDir.resolve("auto-ant.local.properties"), "tomcat.home=" + portable(oldTomcatHome) + "\ncatalina.base=" + portable(oldTomcatHome) + "\ndeploy.dir=" + portable(oldTomcatHome) + "/webapps/old\ntomcat.manager.password=secret\n");

        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir)
                .appName("NewApp")
                .contextPath("/new")
                .javaRelease(17)
                .tomcatHome(newTomcatHome)
                .build());

        new UpdateGenerator(tempDir).update(model,
                java.util.List.of("app.name", "context.path", "context.deploy.name", "context.descriptor.file.name", "java.release"),
                java.util.List.of("tomcat.home", "catalina.base", "deploy.dir", "context.descriptor.dir"));

        String sharedProperties = Files.readString(tempDir.resolve("auto-ant.properties"));
        assertTrue(sharedProperties.contains("app.name=NewApp"));
        assertTrue(sharedProperties.contains("context.path=/new"));
        assertTrue(sharedProperties.contains("context.deploy.name=new"));
        assertTrue(sharedProperties.contains("context.descriptor.file.name=new.xml"));
        assertTrue(sharedProperties.contains("java.release=17"));
        assertTrue(sharedProperties.contains("build.dir=build"));
        assertTrue(sharedProperties.contains("auto.ant.schema.version=2"));

        String localProperties = Files.readString(tempDir.resolve("auto-ant.local.properties"));
        assertTrue(localProperties.contains("tomcat.home=" + portable(newTomcatHome)));
        assertTrue(localProperties.contains("catalina.base=" + portable(newTomcatHome)));
        assertTrue(localProperties.contains("deploy.dir=" + portable(newTomcatHome) + "/webapps/new"));
        assertTrue(localProperties.contains("context.descriptor.dir=" + portable(newTomcatHome) + "/conf/Catalina/localhost"));
        assertTrue(localProperties.contains("tomcat.manager.password=secret"));
    }

    @Test
    void migratesSchemaOneWithBackupAndValidation() throws IOException {
        createSimpleProject();
        Path tomcat = tempDir.resolve("tomcat");
        Files.writeString(tempDir.resolve("auto-ant.properties"), "auto.ant.schema.version=1\napp.name=Legacy\ncontext.path=/legacy\n");
        Files.writeString(tempDir.resolve("auto-ant.local.properties"), "tomcat.home=" + portable(tomcat) + "\n");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).appName("Legacy").contextPath("/legacy").tomcatHome(tomcat).build());
        new UpdateGenerator(tempDir).update(model);
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("auto.ant.schema.version=2"));
        assertTrue(Files.list(tempDir).anyMatch(path -> path.getFileName().toString().startsWith("auto-ant.properties.auto-ant-backup-")));
        assertTrue(Files.list(tempDir).noneMatch(path -> path.getFileName().toString().endsWith(".migration.tmp")));
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
