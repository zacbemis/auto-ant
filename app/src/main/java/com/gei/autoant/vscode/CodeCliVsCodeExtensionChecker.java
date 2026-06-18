package com.gei.autoant.vscode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Locale;

public final class CodeCliVsCodeExtensionChecker implements VsCodeExtensionChecker {
    @Override
    public boolean isInstalled(String extensionId) {
        String expected = extensionId.toLowerCase(Locale.ROOT);
        Process process;
        try {
            process = new ProcessBuilder("code", "--list-extensions")
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException ex) {
            return false;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (expected.equals(line.trim().toLowerCase(Locale.ROOT))) {
                    return process.waitFor() == 0;
                }
            }
            return process.waitFor() == 0 && false;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}