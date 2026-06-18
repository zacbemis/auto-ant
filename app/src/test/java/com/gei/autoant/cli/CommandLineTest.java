package com.gei.autoant.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLineTest {
    @Test
    void parsesLongOptionsWithValuesAndFlags() {
        CommandLine commandLine = CommandLine.parse(new String[]{
                "--app", "MyApp",
                "--src=src,generated-src",
                "--interactive",
                "deploy-exploded"
        });

        assertEquals("MyApp", commandLine.option("app").orElseThrow());
        assertEquals("src,generated-src", commandLine.option("src").orElseThrow());
        assertEquals("true", commandLine.option("interactive").orElseThrow());
        assertEquals(java.util.List.of("deploy-exploded"), commandLine.positionals());
    }

    @Test
    void normalizesOptionNames() {
        CommandLine commandLine = CommandLine.parse(new String[]{"--tomcat_manager_url", "http://localhost:8080/manager/text"});

        assertTrue(commandLine.hasOption("tomcat-manager-url"));
        assertEquals("http://localhost:8080/manager/text", commandLine.option("tomcat-manager-url").orElseThrow());
    }

    @Test
    void rejoinsPathLikeOptionsSplitByGradleArgs() {
        CommandLine commandLine = CommandLine.parse(new String[]{
                "--root",
                "C:\\dev\\git\\FOC-FEMS",
                "-",
                "Copy\\FEMSWeb",
                "--java",
                "8"
        });

        assertEquals("C:\\dev\\git\\FOC-FEMS - Copy\\FEMSWeb", commandLine.option("root").orElseThrow());
        assertEquals("8", commandLine.option("java").orElseThrow());
    }
}