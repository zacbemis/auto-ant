package com.gei.autoant.run;

import com.gei.autoant.detect.AntDetector;
import com.gei.autoant.util.AntCommand;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
        return buildCommand(executable, buildFile, targets);
    }

    List<String> buildCommand(String executable, Path buildFile, List<String> targets) {
        List<String> command = antLauncherCommand(executable).orElseGet(() -> antExecutableCommand(executable));
        command.add("-logger");
        command.add(AntCommand.DEFAULT_LOGGER_CLASS);
        if (buildFile != null) {
            command.add("-f");
            command.add(buildFile.toAbsolutePath().normalize().toString());
        }
        command.addAll(targets);
        return command;
    }

    private List<String> antExecutableCommand(String executable) {
        List<String> command = new ArrayList<>();
        if (isWindowsBatch(executable)) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(executable);
        return command;
    }

    private Optional<List<String>> antLauncherCommand(String executable) {
        return antHome(executable).map(antHome -> {
            Path launcher = antHome.resolve("lib").resolve("ant-launcher.jar").toAbsolutePath().normalize();
            List<String> command = new ArrayList<>();
            command.add(javaExecutable());
            command.add("-Dant.home=" + antHome.toAbsolutePath().normalize());
            command.add("-classpath");
            command.add(launcher.toString());
            command.add("org.apache.tools.ant.launch.Launcher");
            return command;
        });
    }

    private Optional<Path> antHome(String executable) {
        Optional<Path> antHome = antHomeFromExecutable(executable);
        if (antHome.isPresent()) {
            return antHome;
        }

        String antHomeEnvironment = System.getenv("ANT_HOME");
        if (antHomeEnvironment == null || antHomeEnvironment.isBlank()) {
            return Optional.empty();
        }
        try {
            return antHomeWithLauncher(Path.of(antHomeEnvironment));
        } catch (InvalidPathException ex) {
            return Optional.empty();
        }
    }

    private Optional<Path> antHomeFromExecutable(String executable) {
        try {
            Path executablePath = Path.of(executable);
            if (!executablePath.isAbsolute() && executablePath.getParent() == null) {
                return Optional.empty();
            }

            Path binDirectory = executablePath.toAbsolutePath().normalize().getParent();
            if (binDirectory == null || binDirectory.getFileName() == null || !"bin".equalsIgnoreCase(binDirectory.getFileName().toString())) {
                return Optional.empty();
            }

            Path antHome = binDirectory.getParent();
            if (antHome == null) {
                return Optional.empty();
            }
            return antHomeWithLauncher(antHome);
        } catch (InvalidPathException ex) {
            return Optional.empty();
        }
    }

    private Optional<Path> antHomeWithLauncher(Path antHome) {
        Path normalized = antHome.toAbsolutePath().normalize();
        Path launcher = normalized.resolve("lib").resolve("ant-launcher.jar");
        return Files.isRegularFile(launcher) ? Optional.of(normalized) : Optional.empty();
    }

    private String javaExecutable() {
        String executableName = isWindows() ? "java.exe" : "java";
        Path executable = Path.of(System.getProperty("java.home", ""), "bin", executableName);
        if (Files.isRegularFile(executable)) {
            return executable.toAbsolutePath().normalize().toString();
        }
        return executableName;
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

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}