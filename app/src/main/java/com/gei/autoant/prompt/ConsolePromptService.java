package com.gei.autoant.prompt;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Optional;
import java.util.Scanner;

public final class ConsolePromptService implements PromptService {
    private final Scanner scanner;
    private final PrintStream out;

    public ConsolePromptService(InputStream in, PrintStream out) {
        this.scanner = new Scanner(in);
        this.out = out;
    }

    @Override
    public Optional<String> promptOverride(String label, String detectedValue) {
        if (detectedValue == null || detectedValue.isBlank()) {
            out.print(label + " not detected. Enter value, or leave blank: ");
            return readLine();
        }

        out.println("Detected " + label + ": " + detectedValue);
        out.print("Use this? [Y/n] ");
        Optional<String> answer = readLine();
        if (answer.isEmpty() || answer.get().isBlank() || answer.get().trim().equalsIgnoreCase("y") || answer.get().trim().equalsIgnoreCase("yes")) {
            return Optional.empty();
        }
        out.print("Enter " + label + ": ");
        return readLine();
    }

    private Optional<String> readLine() {
        if (!scanner.hasNextLine()) {
            return Optional.empty();
        }
        return Optional.of(scanner.nextLine().trim());
    }
}