package com.gei.autoant.cli;

import com.gei.autoant.prompt.PromptService;
import com.gei.autoant.tomcat.TomcatManagerClient;
import com.gei.autoant.tomcat.TomcatManagerResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void noneStrategyPrintsManualReloadMessage() throws IOException {
        writeShared("none");
        Harness harness = new Harness(tempDir);

        int exitCode = harness.command().run(new String[]{});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("Reload strategy is none"));
        assertTrue(harness.stdout().contains("/MyApp"));
    }

    @Test
    void touchWebXmlStrategyUpdatesDeployedWebXmlTimestamp() throws IOException {
        writeShared("touch-webxml");
        Path catalinaBase = tempDir.resolve("tomcat");
        writeLocal("catalina.base=" + catalinaBase.toString().replace('\\', '/') + "\n");
        Path deployedWebXml = catalinaBase.resolve("webapps/MyApp/WEB-INF/web.xml");
        Files.createDirectories(deployedWebXml.getParent());
        Files.writeString(deployedWebXml, "<web-app/>\n");
        FileTime oldTime = FileTime.from(Instant.parse("2020-01-01T00:00:00Z"));
        Files.setLastModifiedTime(deployedWebXml, oldTime);

        Harness harness = new Harness(tempDir);
        int exitCode = harness.command().run(new String[]{});

        assertEquals(0, exitCode);
        assertTrue(Files.getLastModifiedTime(deployedWebXml).toMillis() > oldTime.toMillis());
        assertTrue(harness.stdout().contains("Touched"));
    }

    @Test
    void managerStrategyCallsTomcatManagerReloadEndpoint() throws IOException {
        writeShared("manager");
        writeLocal("tomcat.manager.url=http://localhost:8080/manager/text\n"
                + "tomcat.manager.user=dev\n"
                + "tomcat.manager.password=secret\n");
        FakeTomcatManagerClient managerClient = new FakeTomcatManagerClient(new TomcatManagerResponse(200, "OK - Reloaded"));

        Harness harness = new Harness(tempDir, managerClient);
        int exitCode = harness.command().run(new String[]{});

        assertEquals(0, exitCode);
        assertEquals("http://localhost:8080/manager/text", managerClient.managerUrl);
        assertEquals("/MyApp", managerClient.contextPath);
        assertEquals("dev", managerClient.user);
        assertEquals("secret", managerClient.password);
        assertTrue(harness.stdout().contains("Reloaded Tomcat context /MyApp"));
        assertTrue(harness.stdout().contains("OK - Reloaded"));
    }

    private void writeShared(String strategy) throws IOException {
        Files.writeString(tempDir.resolve("auto-ant.properties"), "app.name=MyApp\n"
                + "context.path=/MyApp\n"
                + "reload.strategy=" + strategy + "\n");
    }

    private void writeLocal(String content) throws IOException {
        Files.writeString(tempDir.resolve("auto-ant.local.properties"), content);
    }

    private static final class Harness {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private final ReloadCommand command;

        private Harness(Path projectRoot) {
            this(projectRoot, new TomcatManagerClient());
        }

        private Harness(Path projectRoot, TomcatManagerClient managerClient) {
            PromptService promptService = (label, detectedValue) -> Optional.empty();
            CommandContext context = new CommandContext(projectRoot, new PrintStream(out), new PrintStream(err), promptService);
            this.command = new ReloadCommand(context, managerClient);
        }

        private ReloadCommand command() {
            return command;
        }

        private String stdout() {
            return out.toString();
        }
    }

    private static final class FakeTomcatManagerClient extends TomcatManagerClient {
        private final TomcatManagerResponse response;
        private String managerUrl;
        private String contextPath;
        private String user;
        private String password;

        private FakeTomcatManagerClient(TomcatManagerResponse response) {
            this.response = response;
        }

        @Override
        public TomcatManagerResponse reload(String managerUrl, String contextPath, String user, String password) {
            this.managerUrl = managerUrl;
            this.contextPath = contextPath;
            this.user = user;
            this.password = password;
            return response;
        }
    }
}