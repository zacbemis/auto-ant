package com.gei.autoant.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentLockTest {
    @TempDir Path tempDir;

    @Test
    void contentionFailsFastAndReleaseAllowsNextOwner() throws Exception {
        ReconcileConfiguration configuration = configuration();
        try (DeploymentLock first = DeploymentLock.acquire(configuration, 0)) {
            assertThrows(DeploymentLock.LockUnavailableException.class, () -> DeploymentLock.acquire(configuration, 0));
        }
        try (DeploymentLock ignored = DeploymentLock.acquire(configuration, 0)) {
            // released successfully
        }
    }

    @Test
    void sameTargetDifferentContextUsesSameLockIdentity() throws Exception {
        ReconcileConfiguration firstConfiguration = configuration();
        Path other = tempDir.resolve("other-project");
        Files.createDirectories(other.resolve("web"));
        Files.writeString(other.resolve("auto-ant.properties"), "auto.ant.schema.version=2\ncontext.path=/other\ncontext.deploy.name=app\nbuild.web.dir=build/web\nweb.dir=web\nreload.strategy=none\n");
        Files.writeString(other.resolve("auto-ant.local.properties"), "catalina.base=" + portable(firstConfiguration.catalinaBase()) + "\n");
        ReconcileConfiguration secondConfiguration = ReconcileConfiguration.load(other);
        assertEquals(firstConfiguration.identity(), secondConfiguration.identity());
        try (DeploymentLock ignored = DeploymentLock.acquire(firstConfiguration, 0)) {
            assertThrows(DeploymentLock.LockUnavailableException.class, () -> DeploymentLock.acquire(secondConfiguration, 0));
        }
    }

    @Test
    void differentProjectsAndCatalinaBasesForOneExternalTargetUseOneOsLock() throws Exception {
        Path externalParent = tempDir.resolve("external");
        Files.createDirectories(externalParent);
        Path target = externalParent.resolve("shared-app");
        Path firstProject = tempDir.resolve("first-project");
        Path secondProject = tempDir.resolve("second-project");
        ReconcileConfiguration first = externalConfiguration(firstProject, tempDir.resolve("tomcat-one"), target, "/one");
        ReconcileConfiguration second = externalConfiguration(secondProject, tempDir.resolve("tomcat-two"), target, "/two");
        assertEquals(first.identity(), second.identity());

        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        Process process = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
                DeploymentLockProcess.class.getName(), firstProject.toString(), "10000").redirectErrorStream(true).start();
        try {
            assertEquals("LOCKED", new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream())).readLine());
            assertThrows(DeploymentLock.LockUnavailableException.class, () -> DeploymentLock.acquire(second, 0));
        } finally {
            process.destroyForcibly();
            assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    void crossProcessOwnerExcludesThisProcess() throws Exception {
        ReconcileConfiguration configuration = configuration();
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        Process process = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"),
                DeploymentLockProcess.class.getName(), configuration.projectRoot().toString(), "10000").redirectErrorStream(true).start();
        try {
            String line = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream())).readLine();
            assertEquals("LOCKED", line);
            assertThrows(DeploymentLock.LockUnavailableException.class, () -> DeploymentLock.acquire(configuration, 0));
        } finally {
            process.destroyForcibly();
            assertTrue(process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    void canonicalParentAliasUsesSameIdentityWhenLinksAreSupported() throws Exception {
        ReconcileConfiguration direct = configuration();
        Path alias = tempDir.resolve("tomcat-alias");
        try {
            Files.createSymbolicLink(alias, direct.catalinaBase());
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable: " + ex.getMessage());
        }
        Path other = tempDir.resolve("alias-project");
        Files.createDirectories(other.resolve("web"));
        Files.writeString(other.resolve("auto-ant.properties"), "auto.ant.schema.version=2\ncontext.path=/alias\ncontext.deploy.name=app\nbuild.web.dir=build/web\nweb.dir=web\nreload.strategy=none\n");
        Files.writeString(other.resolve("auto-ant.local.properties"), "catalina.base=" + portable(alias) + "\n");
        ReconcileConfiguration throughAlias = ReconcileConfiguration.load(other);
        assertEquals(direct.identity(), throughAlias.identity());
    }

    @Test
    void deploymentParentAliasResolvingInsideProjectIsRejectedWhenLinksAreSupported() throws Exception {
        Path project = tempDir.resolve("contained-project");
        Path realParent = project.resolve("real-deploy-parent");
        Path aliasParent = tempDir.resolve("deploy-parent-alias");
        Path catalina = tempDir.resolve("contained-tomcat");
        Files.createDirectories(realParent);
        Files.createDirectories(catalina.resolve("webapps"));
        Files.createDirectories(project.resolve("web"));
        try {
            Files.createSymbolicLink(aliasParent, realParent);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            org.junit.jupiter.api.Assumptions.abort("Symbolic links are unavailable: " + ex.getMessage());
        }
        Path aliasedTarget = aliasParent.resolve("app");
        Files.writeString(project.resolve("auto-ant.properties"), "auto.ant.schema.version=2\ncontext.path=/app\ncontext.deploy.name=unused\nbuild.web.dir=build/web\nweb.dir=web\nreload.strategy=none\n");
        Files.writeString(project.resolve("auto-ant.local.properties"), "catalina.base=" + portable(catalina)
                + "\ndeploy.dir=" + portable(aliasedTarget) + "\ncontext.descriptor.docBase=" + portable(aliasedTarget) + "\n");
        assertThrows(IllegalArgumentException.class, () -> ReconcileConfiguration.load(project));
    }

    private ReconcileConfiguration configuration() throws IOException {
        Path project = tempDir.resolve("main-project");
        Path catalina = tempDir.resolve("tomcat");
        Files.createDirectories(catalina.resolve("webapps"));
        Files.createDirectories(catalina.resolve("temp"));
        Files.createDirectories(project.resolve("web"));
        Files.writeString(project.resolve("auto-ant.properties"), "auto.ant.schema.version=2\ncontext.path=/app\ncontext.deploy.name=app\nbuild.web.dir=build/web\nweb.dir=web\nreload.strategy=none\n");
        Files.writeString(project.resolve("auto-ant.local.properties"), "catalina.base=" + portable(catalina) + "\n");
        return ReconcileConfiguration.load(project);
    }

    private ReconcileConfiguration externalConfiguration(Path project, Path catalina, Path target, String context) throws IOException {
        Files.createDirectories(catalina.resolve("webapps"));
        Files.createDirectories(project.resolve("web"));
        Files.writeString(project.resolve("auto-ant.properties"), "auto.ant.schema.version=2\ncontext.path=" + context
                + "\ncontext.deploy.name=unused\nbuild.web.dir=build/web\nweb.dir=web\nreload.strategy=none\n");
        Files.writeString(project.resolve("auto-ant.local.properties"), "catalina.base=" + portable(catalina)
                + "\ndeploy.dir=" + portable(target) + "\ncontext.descriptor.docBase=" + portable(target) + "\n");
        return ReconcileConfiguration.load(project);
    }

    private String portable(Path path) { return path.toAbsolutePath().normalize().toString().replace('\\', '/'); }
}
