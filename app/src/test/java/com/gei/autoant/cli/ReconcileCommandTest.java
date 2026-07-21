package com.gei.autoant.cli;

import com.gei.autoant.prompt.PromptService;
import com.gei.autoant.run.CommandResult;
import com.gei.autoant.tomcat.TomcatManagerClient;
import com.gei.autoant.tomcat.TomcatManagerResponse;
import com.gei.autoant.deploy.SnapshotPromoter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconcileCommandTest {
    @TempDir Path tempDir;
    private Path projectRoot;
    private Path catalinaRoot;

    @Test
    void refusesBeforeBuildOrLiveMutationWithoutSafeServerPolicy() throws Exception {
        setup();
        Path live = catalinaRoot.resolve("webapps/app");
        Files.createDirectories(live);
        Files.writeString(live.resolve("old.txt"), "old");
        AtomicInteger builds = new AtomicInteger();
        Harness harness = new Harness(builds);

        assertEquals(1, harness.command.run(new String[]{}));
        assertEquals(0, builds.get());
        assertTrue(Files.exists(live.resolve("old.txt")));
        assertTrue(harness.err.toString().contains("refused when server state cannot be established safely"));
    }

    @Test
    void buildFailureLeavesLiveUntouchedAndMarksStale() throws Exception {
        setup();
        Path live = catalinaRoot.resolve("webapps/app");
        Files.createDirectories(live);
        Files.writeString(live.resolve("old.txt"), "old");
        Harness harness = new Harness(new AtomicInteger(), 7);

        assertEquals(7, harness.command.run(new String[]{"--confirm-stopped"}));
        assertTrue(Files.exists(live.resolve("old.txt")));
        assertTrue(Files.walk(projectRoot.resolve(".auto-ant/state")).anyMatch(path -> path.getFileName().toString().contains("stale")));
    }

    @Test
    void successfulStoppedServerPromotionWritesManifest() throws Exception {
        setup();
        Path live = catalinaRoot.resolve("webapps/app");
        Files.createDirectories(live);
        Files.writeString(live.resolve("old.txt"), "old");
        Harness harness = new Harness(new AtomicInteger());

        assertEquals(0, harness.command.run(new String[]{"--confirm-stopped"}));
        assertFalse(Files.exists(live.resolve("old.txt")));
        assertTrue(Files.exists(live.resolve("index.html")));
        assertTrue(Files.walk(projectRoot.resolve(".auto-ant/state")).anyMatch(path -> path.getFileName().toString().contains("manifest")));
    }

    @Test
    void mismatchedDeploymentIdentityIsRejectedBeforeBuild() throws Exception {
        setup();
        Path other = tempDir.resolve("other-parent/other");
        Files.createDirectories(other.getParent());
        Files.writeString(projectRoot.resolve("auto-ant.local.properties"), "catalina.base=" + portable(catalinaRoot) + "\ndeploy.dir=" + portable(other) + "\n");
        AtomicInteger builds = new AtomicInteger();
        Harness harness = new Harness(builds);
        assertEquals(2, harness.command.run(new String[]{"--confirm-stopped"}));
        assertEquals(0, builds.get());
    }

    @Test
    void interruptionDuringReadinessRollsBackBeforeReturningInterrupted() throws Exception {
        setup();
        Files.writeString(projectRoot.resolve("auto-ant.properties"), "auto.ant.schema.version=2\ncontext.path=/app\ncontext.deploy.name=app\nbuild.web.dir=build/web\nweb.dir=web\nreload.strategy=manager\n");
        Files.writeString(projectRoot.resolve("auto-ant.local.properties"), "catalina.base=" + portable(catalinaRoot) + "\ntomcat.manager.url=http://manager.invalid/text\n");
        Path live = catalinaRoot.resolve("webapps/app");
        Files.createDirectories(live);
        Files.writeString(live.resolve("old.txt"), "old");
        InterruptingManager manager = new InterruptingManager();
        Harness harness = new Harness(new AtomicInteger(), 0, manager);

        try {
            assertEquals(130, harness.command.run(new String[]{}));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals("old", Files.readString(live.resolve("old.txt")));
        assertFalse(Files.exists(live.resolve("index.html")));
        assertEquals(2, manager.starts.get());
    }

    private void setup() throws Exception {
        projectRoot = tempDir.resolve("project");
        catalinaRoot = tempDir.resolve("tomcat");
        Files.createDirectories(catalinaRoot.resolve("webapps"));
        Files.createDirectories(projectRoot.resolve("web/WEB-INF"));
        Files.writeString(projectRoot.resolve("web/index.html"), "new");
        Files.writeString(projectRoot.resolve("web/WEB-INF/web.xml"), "<web-app/>");
        Files.writeString(projectRoot.resolve("auto-ant.build.xml"), "<project/>");
        Files.writeString(projectRoot.resolve("auto-ant.properties"), "auto.ant.schema.version=2\ncontext.path=/app\ncontext.deploy.name=app\nbuild.web.dir=build/web\nweb.dir=web\nreload.strategy=none\n");
        Files.writeString(projectRoot.resolve("auto-ant.local.properties"), "catalina.base=" + portable(catalinaRoot) + "\n");
    }

    private String portable(Path path) { return path.toAbsolutePath().normalize().toString().replace('\\', '/'); }

    private final class Harness {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final ReconcileCommand command;
        Harness(AtomicInteger builds) { this(builds, 0); }
        Harness(AtomicInteger builds, int result) { this(builds, result, new TomcatManagerClient()); }
        Harness(AtomicInteger builds, int result, TomcatManagerClient manager) {
            PromptService prompt = (label, detected) -> Optional.empty();
            CommandContext context = new CommandContext(projectRoot, new PrintStream(out), new PrintStream(err), prompt);
            command = new ReconcileCommand(context, (root, build, target, properties) -> {
                builds.incrementAndGet();
                if (result == 0) {
                    Path snapshot = Path.of(properties.get("reconcile.output.dir"));
                    Files.createDirectories(snapshot.resolve("WEB-INF"));
                    Files.writeString(snapshot.resolve("index.html"), "new");
                }
                return new CommandResult(result);
            }, manager, new SnapshotPromoter());
        }
    }

    private static final class InterruptingManager extends TomcatManagerClient {
        private final AtomicInteger lists = new AtomicInteger();
        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger waits = new AtomicInteger();

        @Override public TomcatManagerResponse list(String managerUrl, String user, String password) {
            int call = lists.incrementAndGet();
            String state = call == 1 || call == 3 ? "running" : "stopped";
            return new TomcatManagerResponse(200, "OK - Listed applications\n/app:" + state + ":0:/app");
        }

        @Override public TomcatManagerResponse stop(String managerUrl, String contextPath, String user, String password) {
            return new TomcatManagerResponse(200, "OK - Stopped application");
        }

        @Override public TomcatManagerResponse start(String managerUrl, String contextPath, String user, String password) {
            starts.incrementAndGet();
            return new TomcatManagerResponse(200, "OK - Started application");
        }

        @Override public boolean waitUntilRunning(String managerUrl, String contextPath, String user, String password,
                                                  int timeoutSeconds, int pollMillis) throws InterruptedException {
            if (waits.incrementAndGet() == 1) throw new InterruptedException("injected readiness interruption");
            return true;
        }
    }
}
