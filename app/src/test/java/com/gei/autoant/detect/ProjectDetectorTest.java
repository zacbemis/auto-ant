package com.gei.autoant.detect;

import com.gei.autoant.model.DetectionStatus;
import com.gei.autoant.model.ServletNamespace;
import com.gei.autoant.prompt.NonInteractiveOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDetectorTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsSimpleSrcWebLayout() throws IOException {
        Files.createDirectories(tempDir.resolve("src/com/example"));
        Files.writeString(tempDir.resolve("src/com/example/MyServlet.java"), "import javax.servlet.http.HttpServlet; class MyServlet extends HttpServlet {}\n");
        Files.createDirectories(tempDir.resolve("web/WEB-INF"));
        Files.writeString(tempDir.resolve("web/WEB-INF/web.xml"), "<web-app/>\n");
        Files.createDirectories(tempDir.resolve("web/WEB-INF/lib"));

        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        assertEquals(tempDir.getFileName().toString(), model.appName().value().orElseThrow());
        assertEquals("/" + tempDir.getFileName(), model.contextPath().value().orElseThrow());
        assertEquals(tempDir.resolve("src").normalize(), model.sourceRoots().value().orElseThrow().get(0).path());
        assertEquals(tempDir.resolve("web").normalize(), model.webRoot().value().orElseThrow().path());
        assertEquals(tempDir.resolve("web/WEB-INF").normalize(), model.webRoot().value().orElseThrow().webInfPath());
        assertEquals(tempDir.resolve("web/WEB-INF/lib").normalize(), model.libraryRoots().value().orElseThrow().get(0).path());
        assertEquals(ServletNamespace.JAVAX, model.servletNamespace().value().orElseThrow());
        assertEquals("Tomcat 9.x", model.recommendedTomcat().value().orElseThrow());
    }

    @Test
    void detectsMavenStyleWebRoot() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.createDirectories(tempDir.resolve("src/main/webapp/WEB-INF"));
        Files.writeString(tempDir.resolve("src/main/webapp/WEB-INF/web.xml"), "<web-app/>\n");

        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        assertEquals(tempDir.resolve("src/main/java").normalize(), model.sourceRoots().value().orElseThrow().get(0).path());
        assertEquals(tempDir.resolve("src/main/webapp").normalize(), model.webRoot().value().orElseThrow().path());
    }

    @Test
    void warnsWhenWebXmlIsMissing() throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve("web"));
        Files.writeString(tempDir.resolve("web/index.jsp"), "hello\n");

        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        assertEquals(DetectionStatus.WARNING, model.webRoot().status());
        assertTrue(model.webRoot().warnings().get(0).contains("WEB-INF/web.xml"));
    }

    @Test
    void overridesWinOverDetection() throws IOException {
        Files.createDirectories(tempDir.resolve("src"));
        Files.createDirectories(tempDir.resolve("WebContent/WEB-INF"));
        Files.createDirectories(tempDir.resolve("custom/java"));
        Files.createDirectories(tempDir.resolve("custom/web/WEB-INF"));

        NonInteractiveOptions options = NonInteractiveOptions.builder(tempDir)
                .appName("MyApp")
                .contextPath("custom")
                .sourceRoots(java.util.List.of(tempDir.resolve("custom/java")))
                .webRoot(tempDir.resolve("custom/web"))
                .javaRelease(25)
                .build();

        var model = new ProjectDetector().detect(tempDir, options);

        assertEquals("MyApp", model.appName().value().orElseThrow());
        assertEquals("/custom", model.contextPath().value().orElseThrow());
        assertEquals(tempDir.resolve("custom/java").normalize(), model.sourceRoots().value().orElseThrow().get(0).path());
        assertEquals(tempDir.resolve("custom/web").normalize(), model.webRoot().value().orElseThrow().path());
        assertEquals(25, model.javaRelease().value().orElseThrow());
        assertEquals(DetectionStatus.OVERRIDDEN, model.webRoot().status());
    }

    @Test
    void detectsJakartaServletNamespace() throws IOException {
        Files.createDirectories(tempDir.resolve("src/com/example"));
        Files.writeString(tempDir.resolve("src/com/example/MyServlet.java"), "import jakarta.servlet.http.HttpServlet; class MyServlet extends HttpServlet {}\n");

        var model = new ProjectDetector().detect(tempDir, NonInteractiveOptions.builder(tempDir).build());

        assertEquals(ServletNamespace.JAKARTA, model.servletNamespace().value().orElseThrow());
        assertEquals("Tomcat 10/11", model.recommendedTomcat().value().orElseThrow());
    }
}