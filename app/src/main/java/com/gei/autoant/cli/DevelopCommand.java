package com.gei.autoant.cli;

import com.gei.autoant.deploy.DeploymentLock;
import com.gei.autoant.deploy.DeploymentState;
import com.gei.autoant.deploy.DevelopmentOperation;
import com.gei.autoant.deploy.IncrementalDeploymentUpdater;
import com.gei.autoant.deploy.ReconcileConfiguration;
import com.gei.autoant.run.AntRunner;
import com.gei.autoant.util.PathUtils;

import java.io.IOException;
import java.nio.file.Path;

public final class DevelopCommand {
    private final CommandContext context;
    private final DevelopmentOperation operation;
    private final DevelopmentOperation.AntTargetRunner antRunner;

    public DevelopCommand(CommandContext context) {
        this(context, new DevelopmentOperation(),
                (root, build, target, properties) -> new AntRunner(context.out(), context.err()).runTarget(root, build, target, properties));
    }

    DevelopCommand(CommandContext context, DevelopmentOperation operation, DevelopmentOperation.AntTargetRunner antRunner) {
        this.context = context;
        this.operation = operation;
        this.antRunner = antRunner;
    }

    public int run(String[] args) {
        CommandLine line = CommandLine.parse(args);
        if (line.hasAnyOption("help", "h")) { printHelp(); return 0; }
        if (line.option("kind").isEmpty()) {
            context.err().println("develop: --kind is required (frontend, views, classes, or config).");
            return 2;
        }
        try {
            IncrementalDeploymentUpdater.Kind kind = IncrementalDeploymentUpdater.Kind.parse(line.option("kind").orElseThrow());
            long waitMillis = parseWait(line) * 1000L;
            Path root = line.option("root").map(value -> PathUtils.resolve(context.projectRoot(), value))
                    .orElse(context.projectRoot()).toAbsolutePath().normalize();
            ReconcileConfiguration configuration = ReconcileConfiguration.load(root);
            DeploymentState state = new DeploymentState(configuration);
            try (DeploymentLock ignored = DeploymentLock.acquire(configuration, waitMillis)) {
                DevelopmentOperation.Result result;
                try {
                    result = operation.run(configuration, kind, antRunner);
                } catch (IOException | RuntimeException ex) {
                    state.markStale("Incremental " + kind.name().toLowerCase() + " update failed: " + ex.getMessage());
                    throw ex;
                }
                if (!result.buildSuccessful()) {
                    context.err().println("develop: desired snapshot build failed; live deployment was not changed.");
                    return result.exitCode();
                }
                var update = result.update();
                context.out().println("Developed " + kind.name().toLowerCase() + ": copied " + update.copied()
                        + ", deleted " + update.deleted() + ".");
                if ((kind == IncrementalDeploymentUpdater.Kind.CLASSES || kind == IncrementalDeploymentUpdater.Kind.CONFIG)
                        && update.mutated()) {
                    int reload = new ReloadCommand(context).run(new String[]{"--root", root.toString()});
                    if (reload != 0) {
                        state.markStale("Incremental " + kind.name().toLowerCase() + " mutation succeeded but reload failed.");
                        return reload;
                    }
                }
                return 0;
            }
        } catch (NumberFormatException ex) {
            context.err().println("develop: --lock-wait-seconds must be a non-negative integer.");
            return 2;
        } catch (IllegalArgumentException ex) {
            context.err().println("develop: " + ex.getMessage());
            return 2;
        } catch (DeploymentLock.LockUnavailableException ex) {
            context.err().println("develop: " + ex.getMessage());
            return 3;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            context.err().println("develop: interrupted");
            return 130;
        } catch (IOException ex) {
            context.err().println("develop: " + ex.getMessage());
            return 1;
        }
    }

    private long parseWait(CommandLine line) {
        long seconds = line.option("lock-wait-seconds").map(Long::parseLong).orElse(30L);
        if (seconds < 0) throw new NumberFormatException();
        return seconds;
    }

    private void printHelp() {
        context.out().println("Usage: auto-ant develop --kind <frontend|views|classes|config> [options]");
        context.out().println();
        context.out().println("Rebuilds a trusted snapshot and incrementally updates only the selected owned category.");
        context.out().println("It does not stop Tomcat or perform a full deployment promotion.");
        context.out().println();
        context.out().println("Options:");
        context.out().println("  --kind <name>              Required owned category.");
        context.out().println("  --root <path>              Project root. Defaults to current directory.");
        context.out().println("  --lock-wait-seconds <n>    Bounded lock wait; 0 is fail-fast (default 30).");
    }
}
