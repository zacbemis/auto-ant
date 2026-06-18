package com.gei.autoant.vscode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class CodeCliVsCodeExtensionChecker implements VsCodeExtensionChecker {
    private final List<String> codeCommands;
    private final List<Path> extensionDirectories;

    public CodeCliVsCodeExtensionChecker() {
        this(List.of("code", "code.cmd", "code-insiders", "code-insiders.cmd"), defaultExtensionDirectories());
    }

    CodeCliVsCodeExtensionChecker(List<String> codeCommands, List<Path> extensionDirectories) {
        this.codeCommands = List.copyOf(codeCommands);
        this.extensionDirectories = List.copyOf(extensionDirectories);
    }

    @Override
    public boolean isInstalled(String extensionId) {
        String expected = extensionId.toLowerCase(Locale.ROOT);
        for (String codeCommand : codeCommands) {
            if (isListedByCodeCli(codeCommand, expected)) {
                return true;
            }
        }
        return isInstalledInExtensionDirectory(expected);
    }

    private boolean isListedByCodeCli(String codeCommand, String expected) {
        Process process;
        try {
            process = new ProcessBuilder(codeCommand, "--list-extensions")
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException ex) {
            return false;
        }

        boolean found = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (expected.equals(line.trim().toLowerCase(Locale.ROOT))) {
                    found = true;
                }
            }
            process.waitFor();
            return found;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isInstalledInExtensionDirectory(String expected) {
        for (Path extensionDirectory : extensionDirectories) {
            if (!Files.isDirectory(extensionDirectory)) {
                continue;
            }
            try (Stream<Path> children = Files.list(extensionDirectory)) {
                if (children.map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
                        .anyMatch(name -> name.equals(expected) || name.startsWith(expected + "-"))) {
                    return true;
                }
            } catch (IOException ex) {
                // Try the next VS Code extension location.
            }
        }
        return false;
    }

    private static List<Path> defaultExtensionDirectories() {
        String userHome = System.getProperty("user.home", "");
        if (userHome.isBlank()) {
            return List.of();
        }
        Path home = Path.of(userHome);
        return List.of(
                home.resolve(".vscode").resolve("extensions"),
                home.resolve(".vscode-insiders").resolve("extensions"),
                home.resolve(".cursor").resolve("extensions")
        );
    }
}