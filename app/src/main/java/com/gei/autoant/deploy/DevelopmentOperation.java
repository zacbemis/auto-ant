package com.gei.autoant.deploy;

import com.gei.autoant.generate.InitGenerator;
import com.gei.autoant.run.CommandResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;

public final class DevelopmentOperation {
    private final SnapshotPromoter promoter;
    private final IncrementalDeploymentUpdater updater;

    public DevelopmentOperation() {
        this(new SnapshotPromoter(), new IncrementalDeploymentUpdater());
    }

    public DevelopmentOperation(SnapshotPromoter promoter, IncrementalDeploymentUpdater updater) {
        this.promoter = promoter;
        this.updater = updater;
    }

    public Result run(ReconcileConfiguration configuration, IncrementalDeploymentUpdater.Kind kind,
                      AntTargetRunner antRunner) throws IOException, InterruptedException {
        SnapshotPromoter.RecoveryResult recovery = promoter.recover(configuration.deployDirectory());
        if (!recovery.safeToProceed()) throw new IOException("Pending promotion recovery prevents incremental development: " + recovery.message());
        Path live = configuration.deployDirectory();
        if (!Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(live)) {
            throw new IOException("An existing non-linked live deployment is required; run a safe full reconcile while Tomcat is stopped first: " + live);
        }
        Path buildFile = configuration.projectRoot().resolve(InitGenerator.AUTO_ANT_BUILD_FILE);
        if (!Files.isRegularFile(buildFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(buildFile)) {
            throw new IOException(InitGenerator.AUTO_ANT_BUILD_FILE + " not found or unsafe. Run auto-ant update.");
        }

        Path snapshot = promoter.createBuildOutput(live);
        try {
            CommandResult build = antRunner.run(configuration.projectRoot(), buildFile, "reconcile-snapshot", Map.of(
                    "reconcile.output.dir", snapshot.toString(),
                    "build.dir", snapshot.toString(),
                    "build.web.dir", snapshot.toString(),
                    "classes.dir", snapshot.resolve("WEB-INF/classes").toString(),
                    "dist.dir", snapshot.resolve(".dist-disabled").toString()));
            if (build.exitCode() != 0) return new Result(build.exitCode(), null, false);
            IncrementalDeploymentUpdater.UpdateResult update = updater.update(snapshot, live, kind);
            return new Result(0, update, true);
        } finally {
            promoter.discardBuildOutput(snapshot, live);
        }
    }

    @FunctionalInterface
    public interface AntTargetRunner {
        CommandResult run(Path root, Path buildFile, String target, Map<String, String> properties) throws IOException, InterruptedException;
    }

    public record Result(int exitCode, IncrementalDeploymentUpdater.UpdateResult update, boolean buildSuccessful) { }
}
