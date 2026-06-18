package com.gei.autoant.generate;

import com.gei.autoant.detect.ProjectDetector;
import com.gei.autoant.prompt.NonInteractiveOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitGeneratorTest {
    @TempDir
    Path tempDir;

    @Test
    void generatesCoreFilesAndGitignoreEntries() throws IOException {
        createSimpleProject();
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).appName("MyApp").javaRelease(25).build());

        GenerationResult result = new InitGenerator(tempDir).generate(model);

        assertEquals(5, result.files().size());
        assertTrue(Files.exists(tempDir.resolve("build.xml")));
        assertTrue(Files.exists(tempDir.resolve("auto-ant.properties")));
        assertTrue(Files.exists(tempDir.resolve("auto-ant.local.properties")));
        assertTrue(Files.exists(tempDir.resolve(".vscode/tasks.json")));
        assertTrue(Files.notExists(tempDir.resolve(".vscode/settings.json")));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("target name=\"clean-build\""));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("target name=\"deploy-exploded\""));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("property name=\"deploy.dir\""));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("target name=\"write-context-descriptor\""));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("${catalina.base}/conf/Catalina/localhost"));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("&lt;Context reloadable=&quot;true&quot;/&gt;"));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("&lt;Context docBase=&quot;${context.descriptor.docBase}&quot; reloadable=&quot;true&quot;/&gt;"));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("${deploy.dir}/WEB-INF/classes"));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("${tomcat.home}/lib"));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("${catalina.base}/lib"));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("exclude name=\"WEB-INF/**\""));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("target name=\"sync-web-inf\""));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("include name=\"**/*.jsp\""));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("${deploy.dir}/WEB-INF"));
        assertTrue(Files.readString(tempDir.resolve("build.xml")).contains("Set deploy.dir to the running exploded app folder"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("app.name=MyApp"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("context.deploy.name=MyApp"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("context.descriptor.file.name=MyApp.xml"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("java.release=25"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.local.properties")).contains("deploy.dir="));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.local.properties")).contains("context.descriptor.dir="));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.local.properties")).contains("# context.descriptor.docBase="));
        assertTrue(Files.readString(tempDir.resolve(".gitignore")).contains("auto-ant.local.properties"));
    }

    @Test
    void userContextPathControlsDeployFolderAndWarName() throws IOException {
        createSimpleProject();
        Path tomcatHome = tempDir.resolve("apache-tomcat");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir)
                .appName("FEMSWeb")
                .contextPath("/fems")
                .tomcatHome(tomcatHome)
                .javaRelease(25)
                .build());

        new InitGenerator(tempDir).generate(model);

        String sharedProperties = Files.readString(tempDir.resolve("auto-ant.properties"));
        String localProperties = Files.readString(tempDir.resolve("auto-ant.local.properties"));
        String buildXml = Files.readString(tempDir.resolve("build.xml"));

        assertTrue(sharedProperties.contains("app.name=FEMSWeb"));
        assertTrue(sharedProperties.contains("context.path=/fems"));
        assertTrue(sharedProperties.contains("context.deploy.name=fems"));
        assertTrue(sharedProperties.contains("context.descriptor.file.name=fems.xml"));
        assertTrue(sharedProperties.contains("war.name=fems.war"));
        assertTrue(localProperties.contains("deploy.dir=" + portable(tomcatHome.toAbsolutePath().normalize()) + "/webapps/fems"));
        assertTrue(localProperties.contains("context.descriptor.dir=" + portable(tomcatHome.toAbsolutePath().normalize()) + "/conf/Catalina/localhost"));
        assertTrue(buildXml.contains("${catalina.base}/webapps/${context.deploy.name}"));
        assertFalse(buildXml.contains("docBase=&quot;${deploy.dir}&quot;"));
    }

    @Test
    void generatedContextDescriptorFileNameFollowsTomcatPathConventions() throws IOException {
        createSimpleProject();
        var rootModel = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir)
                .appName("RootApp")
                .contextPath("/")
                .javaRelease(25)
                .build());
        var nestedModel = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir)
                .appName("NestedApp")
                .contextPath("/foo/bar")
                .javaRelease(25)
                .build());

        String rootProperties = new PropertiesWriter().writeShared(rootModel);
        String nestedProperties = new PropertiesWriter().writeShared(nestedModel);

        assertTrue(rootProperties.contains("context.deploy.name=ROOT"));
        assertTrue(rootProperties.contains("context.descriptor.file.name=ROOT.xml"));
        assertTrue(nestedProperties.contains("context.deploy.name=foo#bar"));
        assertTrue(nestedProperties.contains("context.descriptor.file.name=foo#bar.xml"));
    }

    @Test
    void generatesVsCodeFileWatcherSettingsWhenEnabled() throws IOException {
        createSimpleProject();
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).appName("MyApp").javaRelease(25).build());

        GenerationResult result = new InitGenerator(tempDir).generate(model, true);

        assertEquals(6, result.files().size());
        assertTrue(Files.exists(tempDir.resolve(".vscode/settings.json")));
        String settingsJson = Files.readString(tempDir.resolve(".vscode/settings.json"));
        assertTrue(settingsJson.contains("\"filewatcher.commands\""));
        assertTrue(settingsJson.contains("\"event\": \"onFileChange\""));
        assertTrue(settingsJson.contains("-f "));
        assertTrue(settingsJson.contains("sync-web\""));
        assertTrue(settingsJson.contains("sync-web-inf\""));
        assertFalse(settingsJson.contains("compile-hot && auto-ant reload"));
        assertTrue(settingsJson.contains("jsp|jspf|html"));
    }

    @Test
    void doesNotOverwriteExistingFiles() throws IOException {
        createSimpleProject();
        Files.writeString(tempDir.resolve("build.xml"), "existing build\n");
        Files.createDirectories(tempDir.resolve(".vscode"));
        Files.writeString(tempDir.resolve(".vscode/tasks.json"), "existing tasks\n");
        Files.writeString(tempDir.resolve(".vscode/settings.json"), "existing settings\n");

        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        new InitGenerator(tempDir).generate(model, true);

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

    private String portable(Path path) {
        return path.toString().replace('\\', '/');
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