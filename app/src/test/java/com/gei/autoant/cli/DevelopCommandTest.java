package com.gei.autoant.cli;

import com.gei.autoant.deploy.DevelopmentOperation;
import com.gei.autoant.deploy.IncrementalDeploymentUpdater;
import com.gei.autoant.deploy.SnapshotPromoter;
import com.gei.autoant.prompt.PromptService;
import com.gei.autoant.run.CommandResult;
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

class DevelopCommandTest {
    @TempDir Path tempDir;

    @Test
    void validatesRequiredAndKnownKinds() throws Exception {
        Harness harness = setup("none");
        assertEquals(2, harness.command.run(new String[]{}));
        assertEquals(2, harness.command.run(new String[]{"--kind", "everything"}));
        assertEquals(0, harness.builds.get());
    }

    @Test
    void frontendBuildsControlledSnapshotUpdatesLiveAndDoesNotReload() throws Exception {
        Harness harness = setup("touch-webxml");
        Path webXml = harness.live.resolve("WEB-INF/web.xml");
        long before = Files.getLastModifiedTime(webXml).toMillis();

        assertEquals(0, harness.command.run(new String[]{"--kind", "frontend"}));

        assertEquals(1, harness.builds.get());
        assertEquals("new", Files.readString(harness.live.resolve("index.html")));
        assertFalse(Files.exists(harness.live.resolve("old.css")));
        assertEquals(before, Files.getLastModifiedTime(webXml).toMillis());
        assertTrue(harness.out.toString().contains("Developed frontend"));
        assertFalse(harness.out.toString().contains("Reconciled full snapshot"));
    }

    @Test
    void classesMutateThenUseConfiguredTouchReload() throws Exception {
        Harness harness = setup("touch-webxml");
        Path webXml = harness.live.resolve("WEB-INF/web.xml");
        Files.setLastModifiedTime(webXml, java.nio.file.attribute.FileTime.fromMillis(1));

        assertEquals(0, harness.command.run(new String[]{"--kind", "classes"}));

        assertTrue(Files.exists(harness.live.resolve("WEB-INF/classes/New.class")));
        assertFalse(Files.exists(harness.live.resolve("WEB-INF/classes/Old.class")));
        assertTrue(Files.getLastModifiedTime(webXml).toMillis() > 1);
        assertTrue(harness.out.toString().contains("Touched"));
    }

    @Test
    void buildFailureDoesNotMutateLiveOrReload() throws Exception {
        Harness harness = setup("touch-webxml", 7);
        String old = Files.readString(harness.live.resolve("index.html"));
        long before = Files.getLastModifiedTime(harness.live.resolve("WEB-INF/web.xml")).toMillis();

        assertEquals(7, harness.command.run(new String[]{"--kind", "classes"}));

        assertEquals(old, Files.readString(harness.live.resolve("index.html")));
        assertEquals(before, Files.getLastModifiedTime(harness.live.resolve("WEB-INF/web.xml")).toMillis());
    }

    private Harness setup(String reload) throws Exception { return setup(reload, 0); }

    private Harness setup(String reload, int buildExit) throws Exception {
        Path project = tempDir.resolve("project");
        Path tomcat = tempDir.resolve("tomcat");
        Path live = tomcat.resolve("webapps/app");
        Files.createDirectories(project.resolve("web/WEB-INF"));
        Files.createDirectories(live.resolve("WEB-INF/classes"));
        Files.writeString(project.resolve("auto-ant.build.xml"), "<project/>");
        Files.writeString(project.resolve("auto-ant.properties"), "auto.ant.schema.version=2\ncontext.path=/app\ncontext.deploy.name=app\nbuild.web.dir=build/web\nweb.dir=web\nreload.strategy=" + reload + "\n");
        Files.writeString(project.resolve("auto-ant.local.properties"), "catalina.base=" + portable(tomcat) + "\n");
        Files.writeString(live.resolve("index.html"), "old");
        Files.writeString(live.resolve("old.css"), "old");
        Files.writeString(live.resolve("WEB-INF/web.xml"), "<old/>");
        Files.writeString(live.resolve("WEB-INF/classes/Old.class"), "old");
        AtomicInteger builds = new AtomicInteger();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PromptService prompt = (label, detected) -> Optional.empty();
        CommandContext context = new CommandContext(project, new PrintStream(out), new PrintStream(err), prompt);
        DevelopmentOperation operation = new DevelopmentOperation(new SnapshotPromoter(), new IncrementalDeploymentUpdater());
        DevelopCommand command = new DevelopCommand(context, operation, (root, build, target, properties) -> {
            builds.incrementAndGet();
            if (buildExit == 0) {
                Path snapshot = Path.of(properties.get("reconcile.output.dir"));
                Files.createDirectories(snapshot.resolve("WEB-INF/classes"));
                Files.writeString(snapshot.resolve("index.html"), "new");
                Files.writeString(snapshot.resolve("WEB-INF/web.xml"), "<new/>");
                Files.writeString(snapshot.resolve("WEB-INF/classes/New.class"), "new");
            }
            return new CommandResult(buildExit);
        });
        return new Harness(command, live, builds, out, err);
    }

    private String portable(Path path) { return path.toAbsolutePath().normalize().toString().replace('\\', '/'); }
    private record Harness(DevelopCommand command, Path live, AtomicInteger builds,
                           ByteArrayOutputStream out, ByteArrayOutputStream err) { }
}
