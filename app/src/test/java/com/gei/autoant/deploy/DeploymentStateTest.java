package com.gei.autoant.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeploymentStateTest {
    @TempDir Path tempDir;
    private Path project;

    @Test void currentRequiresSourceAndLiveIntegrity() throws Exception {
        ReconcileConfiguration configuration = configuration();
        Path live = configuration.deployDirectory();
        Files.createDirectories(live);
        Files.writeString(live.resolve("app.txt"), "one");
        DeploymentState state = new DeploymentState(configuration);
        String fingerprint = state.fingerprint(configuration);
        state.markSuccess(fingerprint, configuration);
        assertTrue(state.isCurrent(fingerprint, live));
        Files.writeString(live.resolve("app.txt"), "tampered");
        assertFalse(state.isCurrent(fingerprint, live));
    }

    @Test void fingerprintsExternalInputsAndDoesNotExcludeDirectoriesMerelyNamedBuild() throws Exception {
        ReconcileConfiguration configuration = configuration();
        DeploymentState state = new DeploymentState(configuration);
        String before = state.fingerprint(configuration);
        Files.writeString(project.resolve("web/build/kept.txt"), "changed");
        String after = state.fingerprint(configuration);
        assertNotEquals(before, after);
    }

    @Test void fingerprintsLegitimateExternalSourceAndLibraryRoots() throws Exception {
        Path externalSource = tempDir.resolve("external-source");
        Path externalLibrary = tempDir.resolve("external-library");
        Files.createDirectories(externalSource);
        Files.createDirectories(externalLibrary);
        Files.writeString(externalSource.resolve("External.java"), "class External {}");
        Files.writeString(externalLibrary.resolve("external.jar"), "jar-one");
        ReconcileConfiguration configuration = configuration("src.dirs=" + portable(externalSource) + "\nlib.dirs=" + portable(externalLibrary) + "\n");
        DeploymentState state = new DeploymentState(configuration);
        String before = state.fingerprint(configuration);
        Files.writeString(externalLibrary.resolve("external.jar"), "jar-two");
        assertNotEquals(before, state.fingerprint(configuration));
    }

    @Test void linkedRootAndNestedLinkFailClosedWhenSupported() throws Exception {
        Path external = tempDir.resolve("real-external");
        Files.createDirectories(external);
        Files.writeString(external.resolve("file.txt"), "external");
        Path linkedRoot = tempDir.resolve("linked-root");
        try {
            Files.createSymbolicLink(linkedRoot, external);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException ex) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable: " + ex.getMessage());
        }
        ReconcileConfiguration rootConfiguration = configuration("src.dirs=" + portable(linkedRoot) + "\n");
        assertThrows(java.io.IOException.class, () -> new DeploymentState(rootConfiguration).fingerprint(rootConfiguration));

        ReconcileConfiguration nestedConfiguration = configuration();
        Files.createSymbolicLink(project.resolve("web/nested-link"), external);
        assertThrows(java.io.IOException.class, () -> new DeploymentState(nestedConfiguration).fingerprint(nestedConfiguration));
    }

    @Test void configuredInputInsideGeneratedOutputIsRejectedInsteadOfOmitted() throws Exception {
        Path generatedInput = tempDir.resolve("project/out/generated-source");
        Files.createDirectories(generatedInput);
        Files.writeString(generatedInput.resolve("Generated.java"), "class Generated {}");
        ReconcileConfiguration configuration = configuration("src.dirs=out/generated-source\n");

        java.io.IOException failure = assertThrows(java.io.IOException.class,
                () -> new DeploymentState(configuration).fingerprint(configuration));
        assertTrue(failure.getMessage().contains("overlaps generated/deployment output"));
    }

    private ReconcileConfiguration configuration() throws Exception {
        return configuration("");
    }

    private ReconcileConfiguration configuration(String extraShared) throws Exception {
        project = tempDir.resolve("project");
        Path catalina = tempDir.resolve("tomcat");
        Files.createDirectories(catalina.resolve("webapps"));
        Files.createDirectories(project.resolve("web/build"));
        Files.writeString(project.resolve("web/build/kept.txt"), "initial");
        Files.writeString(project.resolve("auto-ant.build.xml"), "<project/>");
        Files.writeString(project.resolve("auto-ant.properties"), "auto.ant.schema.version=2\ncontext.path=/app\ncontext.deploy.name=app\nbuild.dir=out\nbuild.web.dir=out/web\nclasses.dir=out/web/WEB-INF/classes\ndist.dir=out-dist\nweb.dir=web\nreload.strategy=none\n" + extraShared);
        Files.writeString(project.resolve("auto-ant.local.properties"), "catalina.base=" + catalina.toString().replace('\\', '/') + "\n");
        return ReconcileConfiguration.load(project);
    }

    private String portable(Path path) { return path.toAbsolutePath().normalize().toString().replace('\\', '/'); }
}
