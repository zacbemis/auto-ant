package com.gei.autoant.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncrementalDeploymentUpdaterTest {
    @TempDir Path tempDir;
    private final IncrementalDeploymentUpdater updater = new IncrementalDeploymentUpdater();

    @Test
    void frontendCopiesAndDeletesOnlyPublicFrontendAssets() throws Exception {
        Path desired = tree("desired");
        Path live = tree("live");
        write(desired, "index.html", "new");
        write(desired, "WEB-INF/app.js", "view");
        write(live, "index.html", "old");
        write(live, "stale.css", "stale");
        write(live, "WEB-INF/app.js", "preserve");
        write(live, "notes.txt", "preserve");

        var result = updater.update(desired, live, IncrementalDeploymentUpdater.Kind.FRONTEND);

        assertEquals(1, result.copied());
        assertEquals(1, result.deleted());
        assertEquals("new", Files.readString(live.resolve("index.html")));
        assertFalse(Files.exists(live.resolve("stale.css")));
        assertEquals("preserve", Files.readString(live.resolve("WEB-INF/app.js")));
        assertEquals("preserve", Files.readString(live.resolve("notes.txt")));
    }

    @Test
    void viewsOwnsJspAnywhereAndFrontendUnderWebInfButNotClassesOrLib() throws Exception {
        Path desired = tree("desired");
        Path live = tree("live");
        write(desired, "WEB-INF/views/page.jsp", "new");
        write(desired, "WEB-INF/assets/site.css", "new-css");
        write(desired, "WEB-INF/classes/embedded.jsp", "class");
        write(desired, "WEB-INF/lib/embedded.jsp", "lib");
        write(live, "old.jsp", "stale");
        write(live, "WEB-INF/classes/embedded.jsp", "keep-class");
        write(live, "WEB-INF/lib/embedded.jsp", "keep-lib");

        updater.update(desired, live, IncrementalDeploymentUpdater.Kind.VIEWS);

        assertEquals("new", Files.readString(live.resolve("WEB-INF/views/page.jsp")));
        assertEquals("new-css", Files.readString(live.resolve("WEB-INF/assets/site.css")));
        assertFalse(Files.exists(live.resolve("old.jsp")));
        assertEquals("keep-class", Files.readString(live.resolve("WEB-INF/classes/embedded.jsp")));
        assertEquals("keep-lib", Files.readString(live.resolve("WEB-INF/lib/embedded.jsp")));
    }

    @Test
    void classesIsExactMirrorAndConfigCleansStaleJarsWithoutTouchingViews() throws Exception {
        Path desired = tree("desired");
        Path live = tree("live");
        write(desired, "WEB-INF/classes/New.class", "new");
        write(desired, "WEB-INF/lib/new.jar", "new-jar");
        write(desired, "WEB-INF/web.xml", "new-xml");
        write(live, "WEB-INF/classes/Old.class", "old");
        write(live, "WEB-INF/lib/old.jar", "old-jar");
        write(live, "WEB-INF/page.jsp", "keep");

        updater.update(desired, live, IncrementalDeploymentUpdater.Kind.CLASSES);
        updater.update(desired, live, IncrementalDeploymentUpdater.Kind.CONFIG);

        assertTrue(Files.exists(live.resolve("WEB-INF/classes/New.class")));
        assertFalse(Files.exists(live.resolve("WEB-INF/classes/Old.class")));
        assertTrue(Files.exists(live.resolve("WEB-INF/lib/new.jar")));
        assertFalse(Files.exists(live.resolve("WEB-INF/lib/old.jar")));
        assertEquals("new-xml", Files.readString(live.resolve("WEB-INF/web.xml")));
        assertEquals("keep", Files.readString(live.resolve("WEB-INF/page.jsp")));
    }

    @Test
    void linkedDestinationIsRejectedBeforeOtherFilesMutate() throws Exception {
        Path desired = tree("desired");
        Path live = tree("live");
        write(desired, "a.css", "new-a");
        write(desired, "linked/b.css", "new-b");
        write(live, "a.css", "old-a");
        Path external = tree("external");
        try {
            Files.createSymbolicLink(live.resolve("linked"), external);
        } catch (UnsupportedOperationException | java.io.IOException ex) {
            return;
        }

        assertThrows(java.io.IOException.class,
                () -> updater.update(desired, live, IncrementalDeploymentUpdater.Kind.FRONTEND));
        assertEquals("old-a", Files.readString(live.resolve("a.css")));
    }

    private Path tree(String name) throws Exception { return Files.createDirectory(tempDir.resolve(name)); }
    private void write(Path root, String relative, String value) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, value);
    }
}
