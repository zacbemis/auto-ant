package com.gei.autoant.run;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;

public final class ProcessRunner {
    private final PrintStream out;
    private final PrintStream err;

    public ProcessRunner(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public CommandResult run(Path workingDirectory, List<String> command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory.toFile());
        Process process = builder.start();

        Thread stdout = stream(process.getInputStream(), out);
        Thread stderr = stream(process.getErrorStream(), err);
        int exitCode = process.waitFor();
        stdout.join();
        stderr.join();
        return new CommandResult(exitCode);
    }

    private Thread stream(InputStream inputStream, PrintStream target) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    target.println(line);
                }
            } catch (IOException ex) {
                target.println("Failed to read process output: " + ex.getMessage());
            }
        });
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}