package com.gei.autoant.vscode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeCliVsCodeExtensionCheckerTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsExtensionFromVsCodeExtensionDirectoryWhenCodeCliIsUnavailable() throws IOException {
        Files.createDirectories(tempDir.resolve("appulate.filewatcher-1.2.3"));
        CodeCliVsCodeExtensionChecker checker = new CodeCliVsCodeExtensionChecker(List.of("missing-code-command"), List.of(tempDir));

        assertTrue(checker.isInstalled("appulate.filewatcher"));
    }

    @Test
    void returnsFalseWhenExtensionIsNotListedOrPresentOnDisk() {
        CodeCliVsCodeExtensionChecker checker = new CodeCliVsCodeExtensionChecker(List.of("missing-code-command"), List.of(tempDir));

        assertFalse(checker.isInstalled("appulate.filewatcher"));
    }
}