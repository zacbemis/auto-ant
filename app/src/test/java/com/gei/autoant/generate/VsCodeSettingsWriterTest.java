package com.gei.autoant.generate;

import com.gei.autoant.detect.ProjectDetector;
import com.gei.autoant.prompt.NonInteractiveOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VsCodeSettingsWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesFileWatcherCommandForFrontendSaves() throws IOException {
        createProject("web");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        String settings = new VsCodeSettingsWriter().write(model);

        assertTrue(settings.contains("\"java.project.referencedLibraries\""));
        assertTrue(settings.contains("web/WEB-INF/lib/**/*.jar"));
        assertTrue(settings.contains("\"filewatcher.isSyncRunEvents\": true"));
        assertTrue(settings.contains("\"filewatcher.commands\""));
        assertTrue(settings.contains("\"event\": \"onFileChange\""));
        assertTrue(settings.contains("\"isAsync\": false"));
        assertTrue(settings.contains("\"cmd\": \"ant -logger org.apache.tools.ant.DefaultLogger -f"));
        assertTrue(settings.contains("sync-web\""));
        assertTrue(settings.contains("html|htm|css|js|ts"));
        assertTrue(settings.contains("(?!(WEB-INF|META-INF)"));
        assertTrue(settings.contains("web"));
    }

    @Test
    void writesSeparateCommandForWebInfViewSaves() throws IOException {
        createProject("web");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        String settings = new VsCodeSettingsWriter().write(model);

        assertTrue(settings.contains("WEB-INF"));
        assertTrue(settings.contains("tag|tagx|tld"));
        assertTrue(settings.contains("sync-web-inf"));
    }

    @Test
    void writesConfigWatcherCommandAndBackendJavaWatcher() throws IOException {
        createProject("web");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        String settings = new VsCodeSettingsWriter().write(model);

        assertTrue(settings.contains("compile-hot && auto-ant reload --root"));
        assertTrue(settings.contains("\\\\.java$"));
        assertTrue(settings.contains("deploy-exploded && auto-ant reload --root"));
        assertTrue(settings.contains("WEB-INF"));
        assertTrue(settings.contains("properties|xml|jar"));
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

    @Test
    void writesTomcatLibrariesWhenTomcatHomeIsKnown() throws IOException {
        createProject("web");
        Path tomcatHome = tempDir.resolve("apache-tomcat");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir)
                .tomcatHome(tomcatHome)
                .build());

        String settings = new VsCodeSettingsWriter().write(model);

        assertTrue(settings.contains(portable(tomcatHome.resolve("lib")) + "/**/*.jar"));
    }

    @Test
    void writesJdkHomeSettingsWhenJdkHomeIsKnown() throws IOException {
        createProject("web");
        Path jdkHome = tempDir.resolve("jdk-17");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir)
                .javaRelease(17)
                .jdkHome(jdkHome)
                .build());

        String settings = new VsCodeSettingsWriter().write(model);

        assertTrue(settings.contains("\"java.jdt.ls.java.home\": \"" + portable(jdkHome.toAbsolutePath().normalize()) + "\""));
        assertTrue(settings.contains("\"java.configuration.runtimes\""));
        assertTrue(settings.contains("\"name\": \"JavaSE-17\""));
        assertTrue(settings.contains("\"JAVA_HOME\": \"" + portable(jdkHome.toAbsolutePath().normalize()) + "\""));
        assertTrue(settings.contains(portable(jdkHome.toAbsolutePath().normalize()) + "/bin;${env:PATH}"));
    }

    @Test
    void usesVsCodeJavaRuntimeNameForJavaEight() throws IOException {
        createProject("web");
        Path jdkHome = tempDir.resolve("jdk8");
        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir)
                .javaRelease(8)
                .jdkHome(jdkHome)
                .build());

        String settings = new VsCodeSettingsWriter().write(model);

        assertTrue(settings.contains("\"name\": \"JavaSE-1.8\""));
    }

    private void createProject(String webDirectory) throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve(webDirectory).resolve("WEB-INF/lib"));
        Files.writeString(tempDir.resolve(webDirectory).resolve("WEB-INF/web.xml"), "<web-app/>\n");
    }

    private String portable(Path path) {
        return path.toString().replace('\\', '/');
    }
}