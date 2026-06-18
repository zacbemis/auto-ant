package com.gei.autoant.cli;

import com.gei.autoant.prompt.PromptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void helpListsAvailableTargetsFromBuildXml() throws IOException {
        writeAutoAntBuildXml();
        Harness harness = new Harness(tempDir);

        int exitCode = harness.command().run(new String[]{"--help"});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("Available Ant targets from auto-ant.build.xml:"));
        assertTrue(harness.stdout().contains("clean-build - Clean and build WAR."));
        assertTrue(harness.stdout().contains("deploy-exploded - Copy exploded app to Tomcat webapps."));
        assertTrue(harness.stdout().contains("internal-helper"));
    }

    @Test
    void missingTargetPrintsHelpAndAvailableTargets() throws IOException {
        writeAutoAntBuildXml();
        Harness harness = new Harness(tempDir);

        int exitCode = harness.command().run(new String[]{});

        assertEquals(2, exitCode);
        assertTrue(harness.stdout().contains("Usage: auto-ant run <ant-target> [more-targets]"));
        assertTrue(harness.stdout().contains("Available Ant targets from auto-ant.build.xml:"));
        assertTrue(harness.stdout().contains("sync-web - Copy frontend/web files."));
    }

    @Test
    void helpPrefersAutoAntBuildXmlWhenItExists() throws IOException {
        Files.writeString(tempDir.resolve("build.xml"), """
                <project name=\"NetBeans\">
                    <target name=\"netbeans-target\"/>
                </project>
                """);
        Files.writeString(tempDir.resolve("auto-ant.build.xml"), """
                <project name=\"AutoAnt\">
                    <target name=\"sync-web\" description=\"Copy frontend/web files.\"/>
                </project>
                """);
        Harness harness = new Harness(tempDir);

        int exitCode = harness.command().run(new String[]{"--help"});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("Available Ant targets from auto-ant.build.xml:"));
        assertTrue(harness.stdout().contains("sync-web - Copy frontend/web files."));
        assertFalse(harness.stdout().contains("netbeans-target"));
    }

    @Test
    void helpExplainsWhenBuildXmlIsMissing() {
        Harness harness = new Harness(tempDir);

        int exitCode = harness.command().run(new String[]{"--help"});

        assertEquals(0, exitCode);
        assertTrue(harness.stdout().contains("Available Ant targets: auto-ant.build.xml not found"));
        assertTrue(harness.stdout().contains("Run auto-ant init first"));
    }

    private void writeAutoAntBuildXml() throws IOException {
        Files.writeString(tempDir.resolve("auto-ant.build.xml"), """
                <?xml version=\"1.0\" encoding=\"UTF-8\"?>
                <project name=\"Example\" default=\"clean-build\" basedir=\".\">
                    <target name=\"clean-build\" description=\"Clean and build WAR.\"/>
                    <target name=\"deploy-exploded\" description=\"Copy exploded app to Tomcat webapps.\"/>
                    <target name=\"sync-web\" description=\"Copy frontend/web files.\"/>
                    <target name=\"internal-helper\"/>
                </project>
                """);
    }

    private static final class Harness {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final ByteArrayOutputStream err = new ByteArrayOutputStream();
        private final RunCommand command;

        private Harness(Path projectRoot) {
            PromptService promptService = (label, detectedValue) -> Optional.empty();
            CommandContext context = new CommandContext(projectRoot, new PrintStream(out), new PrintStream(err), promptService);
            this.command = new RunCommand(context);
        }

        private RunCommand command() {
            return command;
        }

        private String stdout() {
            return out.toString();
        }
    }
}