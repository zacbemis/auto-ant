package com.gei.autoant.run;

import com.gei.autoant.detect.AntDetector;
import com.gei.autoant.util.AntCommand;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AntRunner {
    private final PrintStream out;
    private final ProcessRunner processRunner;

    public AntRunner(PrintStream out, PrintStream err) {
        this(out, err, new ProcessRunner(out, err));
    }

    AntRunner(PrintStream out, PrintStream err, ProcessRunner processRunner) {
        this.out = out;
        this.processRunner = processRunner;
    }

    public CommandResult runTarget(Path projectRoot, String target) throws IOException, InterruptedException {
        return runTargets(projectRoot, List.of(target));
    }

    public CommandResult runTarget(Path projectRoot, Path buildFile, String target) throws IOException, InterruptedException {
        return runTargets(projectRoot, buildFile, List.of(target));
    }

    public CommandResult runTargets(Path projectRoot, List<String> targets) throws IOException, InterruptedException {
        return runTargets(projectRoot, null, targets);
    }

    public CommandResult runTargets(Path projectRoot, Path buildFile, List<String> targets) throws IOException, InterruptedException {
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("At least one Ant target is required.");
        }
        for (String target : targets) {
            validateTarget(target);
        }

        List<String> command = buildCommand(buildFile, targets);
        out.println("Running: " + String.join(" ", command));
        return processRunner.run(projectRoot, command);
    }

    List<String> buildCommand(List<String> targets) {
        return buildCommand(null, targets);
    }

    List<String> buildCommand(Path buildFile, List<String> targets) {
        String executable = new AntDetector().detect().value()
                .map(Path::toString)
                .orElse(AntDetector.executableName());

        List<String> command = new ArrayList<>();
        if (isWindowsBatch(executable)) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(executable);
        command.add("-logger");
        command.add(AntCommand.DEFAULT_LOGGER_CLASS);
        if (buildFile != null) {
            command.add("-f");
            command.add(buildFile.toAbsolutePath().normalize().toString());
        }
        command.addAll(targets);
        return command;
    }

    private void validateTarget(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Ant target must not be blank.");
        }
        if (target.contains(" ") || target.contains("\t") || target.contains(";") || target.contains("&") || target.contains("|")) {
            throw new IllegalArgumentException("Unsafe Ant target name: " + target);
        }
    }

    private boolean isWindowsBatch(String executable) {
        String lower = executable.toLowerCase(Locale.ROOT);
        return lower.endsWith(".bat") || lower.endsWith(".cmd");
    }
}