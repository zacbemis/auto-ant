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

        assertEquals(7, result.files().size());
        assertEquals(tempDir.resolve("auto-ant.build.xml").toAbsolutePath().normalize(), result.buildFile());
        assertFalse(Files.exists(tempDir.resolve("build.xml")));
        assertTrue(Files.exists(tempDir.resolve("auto-ant.build.xml")));
        assertTrue(Files.exists(tempDir.resolve("auto-ant.user.xml")));
        assertTrue(Files.exists(tempDir.resolve("auto-ant.properties")));
        assertTrue(Files.exists(tempDir.resolve("auto-ant.local.properties")));
        assertTrue(Files.exists(tempDir.resolve(".vscode/tasks.json")));
        assertTrue(Files.exists(tempDir.resolve(".vscode/settings.json")));
        String buildXml = Files.readString(tempDir.resolve("auto-ant.build.xml"));
        assertTrue(buildXml.contains("AUTO-ANT GENERATED FILE - DO NOT EDIT DIRECTLY"));
        assertTrue(buildXml.contains("<import file=\"auto-ant.user.xml\" optional=\"true\"/>"));
        assertTrue(buildXml.contains("target name=\"clean-build\""));
        assertTrue(buildXml.contains("target name=\"deploy-exploded\""));
        assertTrue(buildXml.contains("depends=\"copy-web,compile,copy-libs\""));
        assertTrue(buildXml.contains("reconcile.output.dir"));
        assertTrue(buildXml.contains("target name=\"branch-refresh\""));
        assertTrue(buildXml.contains("target name=\"reconcile-snapshot\""));
        assertTrue(buildXml.contains("Direct live deployment is disabled"));
        assertTrue(buildXml.contains("property name=\"deploy.dir\""));
        assertTrue(buildXml.contains("target name=\"write-context-descriptor\""));
        assertTrue(buildXml.contains("${catalina.base}/conf/Catalina/localhost"));
        assertTrue(buildXml.contains("&lt;Context reloadable=&quot;true&quot;/&gt;"));
        assertTrue(buildXml.contains("&lt;Context docBase=&quot;${context.descriptor.docBase}&quot; reloadable=&quot;true&quot;/&gt;"));
        assertTrue(buildXml.contains("<delete dir=\"${classes.dir}\"/>"));
        assertTrue(buildXml.contains("${tomcat.home}/lib"));
        assertTrue(buildXml.contains("${catalina.base}/lib"));
        assertTrue(buildXml.contains("target name=\"sync-web-inf\""));
        assertTrue(buildXml.contains("Direct live sync is disabled"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("app.name=MyApp"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("AUTO-ANT PROJECT CONFIGURATION"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("context.deploy.name=MyApp"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("context.descriptor.file.name=MyApp.xml"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("java.release=25"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.properties")).contains("auto.ant.schema.version=2"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.local.properties")).contains("deploy.dir="));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.local.properties")).contains("AUTO-ANT LOCAL USER CONFIGURATION"));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.local.properties")).contains("jdk.home="));
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
        String buildXml = Files.readString(tempDir.resolve("auto-ant.build.xml"));

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
    void generatesVsCodeFileWatcherSettings() throws IOException {
        createSimpleProject();
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).appName("MyApp").javaRelease(25).build());

        GenerationResult result = new InitGenerator(tempDir).generate(model);

        assertEquals(7, result.files().size());
        assertTrue(Files.exists(tempDir.resolve(".vscode/settings.json")));
        String settingsJson = Files.readString(tempDir.resolve(".vscode/settings.json"));
        assertTrue(settingsJson.contains("AUTO-ANT MANAGED SETTINGS - EDIT WITH CARE"));
        assertTrue(settingsJson.contains("\"java.project.referencedLibraries\""));
        assertTrue(settingsJson.contains("\"filewatcher.commands\""));
        assertTrue(settingsJson.contains("\"event\": \"onFileChange\""));
        assertTrue(settingsJson.contains("auto-ant reconcile --root"));
        assertTrue(settingsJson.contains("\\\\.java$"));
        assertTrue(settingsJson.contains("jsp|jspf|tag|tagx|tld"));
        assertTrue(settingsJson.contains("html|htm|css"));
        String tasksJson = Files.readString(tempDir.resolve(".vscode/tasks.json"));
        assertTrue(tasksJson.contains("AUTO-ANT MANAGED TASKS - DO NOT EDIT AUTO-ANT TASKS DIRECTLY"));
        assertTrue(tasksJson.contains("Add custom tasks with labels that do not start with \"auto-ant:\""));
    }

    @Test
    void existingUserBuildFileIsNeverOverwritten() throws IOException {
        createSimpleProject();
        Files.writeString(tempDir.resolve("auto-ant.user.xml"), "<project name=\"mine\"/>\n");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        new InitGenerator(tempDir).generate(model);

        assertEquals("<project name=\"mine\"/>\n", Files.readString(tempDir.resolve("auto-ant.user.xml")));
    }

    @Test
    void existingNetBeansBuildUsesStableAutoAntOverlayBuildFile() throws IOException {
        createSimpleProject();
        Files.writeString(tempDir.resolve("build.xml"), "existing build\n");
        Files.createDirectories(tempDir.resolve(".vscode"));
        Files.writeString(tempDir.resolve(".vscode/tasks.json"), "existing tasks\n");
        Files.writeString(tempDir.resolve(".vscode/settings.json"), "existing settings\n");

        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        GenerationResult result = new InitGenerator(tempDir).generate(model);

        assertEquals(tempDir.resolve("auto-ant.build.xml").toAbsolutePath().normalize(), result.buildFile());
        assertEquals("existing build\n", Files.readString(tempDir.resolve("build.xml")));
        assertEquals("existing tasks\n", Files.readString(tempDir.resolve(".vscode/tasks.json")));
        assertEquals("existing settings\n", Files.readString(tempDir.resolve(".vscode/settings.json")));
        assertTrue(Files.exists(tempDir.resolve("auto-ant.build.xml")));
        assertTrue(Files.readString(tempDir.resolve("auto-ant.build.xml")).contains("target name=\"deploy-exploded\""));
        assertTrue(Files.exists(tempDir.resolve(".vscode/tasks.auto-ant-new.json")));
        assertTrue(Files.exists(tempDir.resolve(".vscode/settings.auto-ant-new.json")));
        assertTrue(Files.readString(tempDir.resolve(".vscode/tasks.auto-ant-new.json")).contains("auto-ant.build.xml"));
        assertTrue(Files.readString(tempDir.resolve(".vscode/settings.auto-ant-new.json")).contains("auto-ant reconcile"));
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
