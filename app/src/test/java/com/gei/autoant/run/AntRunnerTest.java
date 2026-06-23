package com.gei.autoant.run;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void usesJavaLauncherWhenAntLauncherJarIsAvailable() throws IOException {
        Path antHome = createAntHome(true);
        Path antBat = antHome.resolve("bin/ant.bat");
        Path buildFile = tempDir.resolve("auto-ant.build.xml");

        List<String> command = runner().buildCommand(antBat.toString(), buildFile, List.of("deploy-exploded"));

        assertFalse(command.contains("cmd.exe"));
        assertFalse(command.contains("/c"));
        assertFalse(command.contains(antBat.toString()));
        assertTrue(command.get(0).endsWith("java.exe") || command.get(0).endsWith("java"));
        assertTrue(command.contains("-Dant.home=" + antHome.toAbsolutePath().normalize()));
        assertTrue(command.contains("-classpath"));
        assertTrue(command.contains(antHome.resolve("lib/ant-launcher.jar").toAbsolutePath().normalize().toString()));
        assertTrue(command.contains("org.apache.tools.ant.launch.Launcher"));
        assertTrue(command.contains("-f"));
        assertTrue(command.contains(buildFile.toAbsolutePath().normalize().toString()));
        assertEquals("deploy-exploded", command.get(command.size() - 1));
    }

    @Test
    void fallsBackToBatchFileWhenAntLauncherJarIsMissing() throws IOException {
        Path antHome = createAntHome(false);
        Path antBat = antHome.resolve("bin/ant.bat");

        List<String> command = runner().buildCommand(antBat.toString(), null, List.of("clean-build"));

        assertEquals(List.of("cmd.exe", "/c", antBat.toString(), "-logger", "org.apache.tools.ant.DefaultLogger", "clean-build"), command);
    }

    private Path createAntHome(boolean includeLauncher) throws IOException {
        Path antHome = tempDir.resolve("apache-ant");
        Files.createDirectories(antHome.resolve("bin"));
        Files.createDirectories(antHome.resolve("lib"));
        Files.writeString(antHome.resolve("bin/ant.bat"), "@echo off\n");
        if (includeLauncher) {
            Files.writeString(antHome.resolve("lib/ant-launcher.jar"), "placeholder\n");
        }
        return antHome;
    }

    private AntRunner runner() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        return new AntRunner(new PrintStream(out), new PrintStream(err), null);
    }
}