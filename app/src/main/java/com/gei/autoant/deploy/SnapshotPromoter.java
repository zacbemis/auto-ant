package com.gei.autoant.deploy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public class SnapshotPromoter {
    private static final String JOURNAL_SUFFIX = ".auto-ant-transaction.properties";
    private static final String JOURNAL_VERSION = "1";
    private final FaultInjector faultInjector;

    public SnapshotPromoter() {
        this(point -> { });
    }

    SnapshotPromoter(FaultInjector faultInjector) {
        this.faultInjector = faultInjector;
    }

    public PreparedSnapshot stage(Path source, Path live) throws IOException {
        Path safeLive = validateLive(live);
        Path parent = safeLive.getParent();
        Path safeSource = source.toAbsolutePath().normalize();
        requireRealDirectory(safeSource, "Built snapshot directory");
        String transactionId = UUID.randomUUID().toString();
        Path stage = controlledSibling(parent, safeLive.getFileName(), "stage", transactionId);
        Path backup = controlledSibling(parent, safeLive.getFileName(), "backup", transactionId);
        Files.createDirectory(stage);
        try {
            FileStore stageStore = Files.getFileStore(stage);
            FileStore liveParentStore = Files.getFileStore(parent);
            if (!stageStore.equals(liveParentStore)) {
                throw new IOException("Staging directory is not on the deployment volume.");
            }
            copyTreeNoLinks(safeSource, stage);
            return new PreparedSnapshot(stage, safeLive, backup, journalPath(safeLive), transactionId, treeDigest(stage));
        } catch (IOException ex) {
            deleteControlledTree(stage, parent, safeLive.getFileName().toString(), "stage", transactionId);
            throw ex;
        }
    }

    public PromotionResult promote(PreparedSnapshot prepared) throws IOException {
        List<StepOutcome> steps = new ArrayList<>();
        try {
            validatePrepared(prepared);
        } catch (IOException ex) {
            throw promotionFailure("Snapshot promotion validation failed.", ex, prepared, false, "", steps,
                    new RollbackResult(TransactionState.ROLLBACK_UNCERTAIN, false, ex, List.copyOf(steps)));
        }
        boolean hadLive = Files.exists(prepared.live(), LinkOption.NOFOLLOW_LINKS);
        String oldDigest = hadLive ? treeDigest(prepared.live()) : "";
        try {
            persist(prepared, Phase.PREPARED, hadLive, oldDigest, steps, FaultPoint.AFTER_PREPARED_JOURNAL);
            if (hadLive) {
                rename(prepared.live(), prepared.backup(), steps, FaultPoint.AFTER_LIVE_TO_BACKUP);
                persist(prepared, Phase.LIVE_MOVED_TO_BACKUP, true, oldDigest, steps, FaultPoint.AFTER_LIVE_MOVED_JOURNAL);
            }
            rename(prepared.stage(), prepared.live(), steps, FaultPoint.AFTER_STAGE_TO_LIVE);
            persist(prepared, Phase.STAGE_MOVED_TO_LIVE, hadLive, oldDigest, steps, FaultPoint.AFTER_STAGE_MOVED_JOURNAL);
            TransactionOutcome outcome = new TransactionOutcome(TransactionState.PROMOTED, false, List.copyOf(steps));
            return new PromotionResult(prepared.live(), prepared.backup(), prepared.stage(), prepared.journal(), hadLive,
                    TransactionState.PROMOTED, prepared.transactionId(), oldDigest, prepared.stagedDigest(), outcome);
        } catch (IOException ex) {
            RollbackResult rollback = restorePrevious(prepared, hadLive, oldDigest, steps);
            throw promotionFailure("Snapshot promotion failed; restoration state is " + rollback.state() + ".",
                    ex, prepared, hadLive, oldDigest, steps, rollback);
        }
    }

    public RollbackResult rollback(PromotionResult promotion) {
        List<StepOutcome> steps = new ArrayList<>(promotion.outcome().steps());
        try {
            validatePromotion(promotion);
        } catch (IOException ex) {
            return new RollbackResult(TransactionState.ROLLBACK_UNCERTAIN, false, ex, List.copyOf(steps));
        }
        PreparedSnapshot prepared = new PreparedSnapshot(promotion.stage(), promotion.live(), promotion.backup(),
                promotion.journal(), promotion.transactionId(), promotion.stagedDigest());
        RollbackResult restored = restorePrevious(prepared, promotion.hadLive(), promotion.oldDigest(), steps);
        if (!restored.complete()) return restored;
        try {
            persist(prepared, Phase.ROLLED_BACK, promotion.hadLive(), promotion.oldDigest(), steps, FaultPoint.AFTER_ROLLED_BACK_JOURNAL);
            deleteControlledTree(prepared.stage(), prepared.live().getParent(), prepared.live().getFileName().toString(),
                    "stage", prepared.transactionId());
            Files.deleteIfExists(prepared.journal());
            return new RollbackResult(TransactionState.ROLLED_BACK, true, null, List.copyOf(steps));
        } catch (IOException ex) {
            // Restoration was already content-verified. Keep the journal and remaining artifacts for recovery.
            return new RollbackResult(TransactionState.ROLLED_BACK, true, ex, List.copyOf(steps));
        }
    }

    public void complete(PromotionResult promotion) throws IOException {
        validatePromotion(promotion);
        Path parent = promotion.live().getParent();
        deleteControlledTree(promotion.backup(), parent, promotion.live().getFileName().toString(), "backup", promotion.transactionId());
        Files.deleteIfExists(promotion.journal());
    }

    public void discard(PreparedSnapshot prepared) throws IOException {
        validatePrepared(prepared);
        deleteControlledTree(prepared.stage(), prepared.live().getParent(), prepared.live().getFileName().toString(),
                "stage", prepared.transactionId());
    }

    public Path createBuildOutput(Path live) throws IOException {
        Path safeLive = validateLive(live);
        String id = UUID.randomUUID().toString();
        Path output = controlledSibling(safeLive.getParent(), safeLive.getFileName(), "build", id);
        Files.createDirectory(output);
        if (!Files.getFileStore(output).equals(Files.getFileStore(safeLive.getParent()))) {
            deleteControlledTree(output, safeLive.getParent(), safeLive.getFileName().toString(), "build", id);
            throw new IOException("Controlled build output is not on the deployment volume.");
        }
        return output;
    }

    public void discardBuildOutput(Path output, Path live) throws IOException {
        Path safeLive = validateLive(live);
        String id = controlledId(output, safeLive.getFileName().toString(), "build");
        deleteControlledTree(output, safeLive.getParent(), safeLive.getFileName().toString(), "build", id);
    }

    public RecoveryResult recover(Path live) throws IOException {
        Path safeLive = validateLive(live);
        Path journal = journalPath(safeLive);
        if (Files.notExists(journal, LinkOption.NOFOLLOW_LINKS)) {
            return new RecoveryResult(TransactionState.NONE, "No pending promotion transaction.");
        }

        Journal transaction;
        try {
            transaction = trustedJournal(safeLive, journal);
        } catch (IOException | RuntimeException ex) {
            return new RecoveryResult(TransactionState.AMBIGUOUS,
                    "Transaction journal is corrupt, stale, or mismatched (" + safeMessage(ex) + "); no artifact was changed. "
                            + artifactMessage(safeLive, null, null, journal));
        }

        Path stage = transaction.stage();
        Path backup = transaction.backup();
        boolean liveExists = Files.exists(safeLive, LinkOption.NOFOLLOW_LINKS);
        boolean stageExists = Files.exists(stage, LinkOption.NOFOLLOW_LINKS);
        boolean backupExists = Files.exists(backup, LinkOption.NOFOLLOW_LINKS);
        try {
            validateArtifactType(safeLive, "live");
            validateArtifactType(stage, "stage");
            validateArtifactType(backup, "backup");
        } catch (IOException ex) {
            return new RecoveryResult(TransactionState.AMBIGUOUS,
                    "Promotion artifacts include a link or special file; no artifact was changed. " + artifactMessage(safeLive, stage, backup, journal));
        }

        if (transaction.phase() == Phase.STAGE_MOVED_TO_LIVE && liveExists && !stageExists
                && (!transaction.hadLive() || backupExists)
                && treeDigest(safeLive).equals(transaction.stagedDigest())) {
            return new RecoveryResult(TransactionState.PROMOTED,
                    "A content-verified promoted snapshot is present. Verify it before completing recovery. "
                            + artifactMessage(safeLive, stage, backup, journal));
        }
        if ((transaction.phase() == Phase.LIVE_MOVED_TO_BACKUP || transaction.phase() == Phase.PREPARED)
                && liveExists && !stageExists && backupExists
                && treeDigest(safeLive).equals(transaction.stagedDigest())) {
            return new RecoveryResult(TransactionState.PROMOTED,
                    "The filesystem is ahead of the journal and contains a content-verified promoted snapshot. "
                            + artifactMessage(safeLive, stage, backup, journal));
        }
        if ((transaction.phase() == Phase.LIVE_MOVED_TO_BACKUP || transaction.phase() == Phase.PREPARED)
                && !liveExists && stageExists && transaction.hadLive() && backupExists
                && treeDigest(backup).equals(transaction.oldDigest())) {
            moveRaw(backup, safeLive);
            if (!verifiedPrevious(safeLive, backup, true, transaction.oldDigest())) {
                return new RecoveryResult(TransactionState.AMBIGUOUS,
                        "Previous deployment restoration could not be verified; the journal was preserved. "
                                + artifactMessage(safeLive, stage, backup, journal));
            }
            Files.deleteIfExists(journal);
            return new RecoveryResult(TransactionState.ROLLED_BACK, "Recovered and content-verified the previous live deployment from the transaction backup.");
        }
        if (transaction.phase() == Phase.PREPARED && stageExists && !backupExists
                && ((transaction.hadLive() && liveExists && treeDigest(safeLive).equals(transaction.oldDigest()))
                || (!transaction.hadLive() && !liveExists))) {
            deleteControlledTree(stage, safeLive.getParent(), safeLive.getFileName().toString(), "stage", transaction.transactionId());
            Files.deleteIfExists(journal);
            return new RecoveryResult(TransactionState.ROLLED_BACK, "Discarded an unpromoted, transaction-validated snapshot.");
        }
        if (transaction.phase() == Phase.ROLLED_BACK && verifiedPrevious(safeLive, backup, transaction.hadLive(), transaction.oldDigest())) {
            if (stageExists) {
                deleteControlledTree(stage, safeLive.getParent(), safeLive.getFileName().toString(), "stage", transaction.transactionId());
            }
            Files.deleteIfExists(journal);
            return new RecoveryResult(TransactionState.ROLLED_BACK, "Completed cleanup of a content-verified rollback.");
        }
        return new RecoveryResult(TransactionState.AMBIGUOUS,
                "Promotion artifacts do not prove one safe transaction state; no artifact was changed. "
                        + artifactMessage(safeLive, stage, backup, journal));
    }

    private RollbackResult restorePrevious(PreparedSnapshot prepared, boolean hadLive, String oldDigest, List<StepOutcome> steps) {
        IOException failure = null;
        try {
            if (verifiedPrevious(prepared.live(), prepared.backup(), hadLive, oldDigest)) {
                return new RollbackResult(TransactionState.ROLLED_BACK, true, null, List.copyOf(steps));
            }
            if (Files.exists(prepared.live(), LinkOption.NOFOLLOW_LINKS)) {
                if (Files.exists(prepared.stage(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Both live and stage exist; refusing to overwrite either artifact during restoration.");
                }
                rename(prepared.live(), prepared.stage(), steps, FaultPoint.AFTER_RESTORE_LIVE_TO_STAGE);
            }
            if (hadLive) {
                if (Files.notExists(prepared.backup(), LinkOption.NOFOLLOW_LINKS)
                        || !treeDigest(prepared.backup()).equals(oldDigest)) {
                    throw new IOException("The transaction backup is missing or does not match the previous live digest.");
                }
                rename(prepared.backup(), prepared.live(), steps, FaultPoint.AFTER_RESTORE_BACKUP_TO_LIVE);
            }
            if (!verifiedPrevious(prepared.live(), prepared.backup(), hadLive, oldDigest)) {
                throw new IOException("Postconditions did not verify exact restoration of the previous deployment.");
            }
            try {
                persist(prepared, Phase.ROLLED_BACK, hadLive, oldDigest, steps, FaultPoint.AFTER_ROLLED_BACK_JOURNAL);
            } catch (IOException journalFailure) {
                // The filesystem is verified restored. Preserve the old/stale journal and report the persistence failure.
                failure = journalFailure;
            }
            return new RollbackResult(TransactionState.ROLLED_BACK, true, failure, List.copyOf(steps));
        } catch (IOException ex) {
            return new RollbackResult(TransactionState.ROLLBACK_UNCERTAIN, false, ex, List.copyOf(steps));
        }
    }

    private boolean verifiedPrevious(Path live, Path backup, boolean hadLive, String oldDigest) throws IOException {
        if (!hadLive) {
            return Files.notExists(live, LinkOption.NOFOLLOW_LINKS) && Files.notExists(backup, LinkOption.NOFOLLOW_LINKS);
        }
        return Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(live)
                && Files.notExists(backup, LinkOption.NOFOLLOW_LINKS) && !oldDigest.isBlank() && treeDigest(live).equals(oldDigest);
    }

    private PromotionException promotionFailure(String message, IOException cause, PreparedSnapshot prepared,
                                                boolean hadLive, String oldDigest, List<StepOutcome> steps,
                                                RollbackResult rollback) {
        if (rollback.failure() != null && rollback.failure() != cause) cause.addSuppressed(rollback.failure());
        TransactionOutcome outcome = new TransactionOutcome(rollback.state(), rollback.restorationVerified(), List.copyOf(steps));
        return new PromotionException(message, cause, rollback, prepared, hadLive, oldDigest, outcome);
    }

    private Path validateLive(Path live) throws IOException {
        Path normalized = live.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IOException("Deployment parent must be an existing directory: " + parent);
        }
        Path realParent = parent.toRealPath();
        Path result = realParent.resolve(normalized.getFileName()).normalize();
        if (!result.getParent().equals(realParent)) throw new IOException("Deployment path escapes its canonical parent: " + live);
        if (Files.exists(result, LinkOption.NOFOLLOW_LINKS)) {
            requireRealDirectory(result, "Deployment target");
            if (!result.toRealPath().equals(result)) throw new IOException("Deployment target aliases another path: " + live);
        }
        return result;
    }

    private void validatePrepared(PreparedSnapshot prepared) throws IOException {
        Path live = validateLive(prepared.live());
        if (!live.equals(prepared.live().toAbsolutePath().normalize())) throw new IOException("Prepared live path is not canonical.");
        Path parent = live.getParent();
        validateControlledSibling(prepared.stage(), parent, live.getFileName().toString(), "stage", prepared.transactionId());
        validateControlledSibling(prepared.backup(), parent, live.getFileName().toString(), "backup", prepared.transactionId());
        if (!prepared.journal().toAbsolutePath().normalize().equals(journalPath(live))) throw new IOException("Unexpected transaction journal path.");
        if (!UUID.fromString(prepared.transactionId()).toString().equals(prepared.transactionId())) throw new IOException("Invalid transaction ID.");
    }

    private void validatePromotion(PromotionResult promotion) throws IOException {
        validatePrepared(new PreparedSnapshot(promotion.stage(), promotion.live(), promotion.backup(), promotion.journal(),
                promotion.transactionId(), promotion.stagedDigest()));
    }

    private Path controlledSibling(Path parent, Path targetName, String kind, String transactionId) {
        return parent.resolve("." + targetName + ".auto-ant-" + kind + "-" + transactionId).normalize();
    }

    private String controlledId(Path path, String targetName, String kind) throws IOException {
        String prefix = "." + targetName + ".auto-ant-" + kind + "-";
        String name = path.toAbsolutePath().normalize().getFileName().toString();
        if (!name.startsWith(prefix)) throw new IOException("Refusing uncontrolled " + kind + " path: " + path);
        String id = name.substring(prefix.length());
        try { return UUID.fromString(id).toString(); }
        catch (IllegalArgumentException ex) { throw new IOException("Refusing uncontrolled " + kind + " path: " + path, ex); }
    }

    private void validateControlledSibling(Path path, Path parent, String targetName, String kind, String transactionId) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        Path expected = controlledSibling(parent, Path.of(targetName), kind, transactionId);
        if (!normalized.equals(expected)) throw new IOException("Refusing uncontrolled " + kind + " path: " + path);
        validateArtifactType(normalized, kind);
    }

    private void validateArtifactType(Path path, String description) throws IOException {
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return;
        BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink() || Files.isSymbolicLink(path)) {
            throw new IOException("Refusing linked or special " + description + " artifact: " + path);
        }
    }

    private void requireRealDirectory(Path path, String description) throws IOException {
        validateArtifactType(path, description);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException(description + " is not a real directory: " + path);
    }

    private void deleteControlledTree(Path path, Path parent, String targetName, String kind, String transactionId) throws IOException {
        validateControlledSibling(path, parent, targetName, kind, transactionId);
        deleteTree(path);
    }

    private Path journalPath(Path live) {
        return live.getParent().resolve("." + live.getFileName() + JOURNAL_SUFFIX).normalize();
    }

    private Journal trustedJournal(Path live, Path journal) throws IOException {
        BasicFileAttributes journalAttributes = Files.readAttributes(journal, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!journalAttributes.isRegularFile() || journalAttributes.isSymbolicLink() || Files.isSymbolicLink(journal)) {
            throw new IOException("journal is a link or special file");
        }
        Properties properties = load(journal);
        if (!JOURNAL_VERSION.equals(properties.getProperty("journal.version"))) throw new IOException("unsupported journal version");
        String id = required(properties, "transaction.id");
        if (!UUID.fromString(id).toString().equals(id)) throw new IOException("invalid transaction ID");
        Path recordedLive = Path.of(required(properties, "live")).toAbsolutePath().normalize();
        if (!recordedLive.equals(live)) throw new IOException("journal live target does not exactly match the requested canonical target");
        Path parent = live.getParent();
        Path stage = Path.of(required(properties, "stage")).toAbsolutePath().normalize();
        Path backup = Path.of(required(properties, "backup")).toAbsolutePath().normalize();
        validateControlledSibling(stage, parent, live.getFileName().toString(), "stage", id);
        validateControlledSibling(backup, parent, live.getFileName().toString(), "backup", id);
        String hadLiveValue = required(properties, "hadLive");
        if (!hadLiveValue.equals("true") && !hadLiveValue.equals("false")) throw new IOException("invalid hadLive value");
        boolean hadLive = Boolean.parseBoolean(hadLiveValue);
        String oldDigest = required(properties, "old.digest");
        String stagedDigest = required(properties, "staged.digest");
        if ((hadLive && !isDigest(oldDigest)) || (!hadLive && !oldDigest.isEmpty()) || !isDigest(stagedDigest)) {
            throw new IOException("invalid transaction digest");
        }
        Phase phase = Phase.valueOf(required(properties, "phase"));
        return new Journal(id, phase, stage, backup, hadLive, oldDigest, stagedDigest);
    }

    private String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null) throw new IOException("journal is missing " + key);
        return value;
    }

    private boolean isDigest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private void persist(PreparedSnapshot prepared, Phase phase, boolean hadLive, String oldDigest,
                         List<StepOutcome> steps, FaultPoint point) throws IOException {
        try {
            writeJournalRaw(prepared, phase, hadLive, oldDigest);
            faultInjector.after(point);
            steps.add(new StepOutcome(point, true, true, "persisted " + phase));
        } catch (IOException ex) {
            steps.add(new StepOutcome(point, false, Files.exists(prepared.journal(), LinkOption.NOFOLLOW_LINKS), safeMessage(ex)));
            throw ex;
        }
    }

    private void writeJournalRaw(PreparedSnapshot prepared, Phase phase, boolean hadLive, String oldDigest) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("journal.version", JOURNAL_VERSION);
        properties.setProperty("transaction.id", prepared.transactionId());
        properties.setProperty("phase", phase.name());
        properties.setProperty("live", prepared.live().toString());
        properties.setProperty("stage", prepared.stage().toString());
        properties.setProperty("backup", prepared.backup().toString());
        properties.setProperty("hadLive", Boolean.toString(hadLive));
        properties.setProperty("old.digest", oldDigest);
        properties.setProperty("staged.digest", prepared.stagedDigest());
        Path temporary = Files.createTempFile(prepared.live().getParent(), prepared.journal().getFileName().toString(), ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "auto-ant durable promotion transaction");
            }
            try {
                Files.move(temporary, prepared.journal(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                Files.move(temporary, prepared.journal(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
        return properties;
    }

    private void rename(Path source, Path target, List<StepOutcome> steps, FaultPoint point) throws IOException {
        try {
            moveRaw(source, target);
            faultInjector.after(point);
            steps.add(new StepOutcome(point, true, Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    && Files.notExists(source, LinkOption.NOFOLLOW_LINKS), "move completed"));
        } catch (IOException ex) {
            boolean observed = Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.notExists(source, LinkOption.NOFOLLOW_LINKS);
            steps.add(new StepOutcome(point, false, observed, safeMessage(ex)));
            throw ex;
        }
    }

    protected void move(Path source, Path target) throws IOException { moveRaw(source, target); }

    private void moveRaw(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (IOException ex) { Files.move(source, target); }
    }

    private void copyTreeNoLinks(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || Files.isSymbolicLink(dir)) throw new IOException("Snapshot contains a symbolic-link directory: " + dir);
                if (!dir.equals(source)) Files.createDirectory(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || attrs.isSymbolicLink() || Files.isSymbolicLink(file)) {
                    throw new IOException("Snapshot contains an unsupported link or special file: " + file);
                }
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String treeDigest(Path directory) throws IOException {
        requireRealDirectory(directory, "Transaction directory");
        MessageDigest digest = digest();
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || Files.isSymbolicLink(dir)) throw new IOException("Transaction directory contains a link: " + dir);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || attrs.isSymbolicLink() || Files.isSymbolicLink(file)) {
                    throw new IOException("Transaction directory contains a link or special file: " + file);
                }
                files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(path -> directory.relativize(path).toString().replace('\\', '/')));
        byte[] buffer = new byte[8192];
        for (Path file : files) {
            digest.update(directory.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
                while (input.read(buffer) >= 0) { }
            }
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    public static void deleteTree(Path path) throws IOException {
        if (path == null || Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(path)) { Files.delete(path); return; }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) throw exc;
                Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }

    private String artifactMessage(Path live, Path stage, Path backup, Path journal) {
        return "Preserve live=" + live + ", stage=" + (stage == null ? "<untrusted-journal-value>" : stage)
                + ", backup=" + (backup == null ? "<untrusted-journal-value>" : backup) + ", journal=" + journal + ".";
    }

    private String safeMessage(Throwable failure) {
        String value = failure.getMessage();
        return value == null || value.isBlank() ? failure.getClass().getSimpleName() : value.replace('\r', ' ').replace('\n', ' ');
    }

    private enum Phase { PREPARED, LIVE_MOVED_TO_BACKUP, STAGE_MOVED_TO_LIVE, ROLLED_BACK }
    enum FaultPoint {
        AFTER_PREPARED_JOURNAL,
        AFTER_LIVE_TO_BACKUP,
        AFTER_LIVE_MOVED_JOURNAL,
        AFTER_STAGE_TO_LIVE,
        AFTER_STAGE_MOVED_JOURNAL,
        AFTER_RESTORE_LIVE_TO_STAGE,
        AFTER_RESTORE_BACKUP_TO_LIVE,
        AFTER_ROLLED_BACK_JOURNAL
    }
    @FunctionalInterface interface FaultInjector { void after(FaultPoint point) throws IOException; }

    public enum TransactionState { NONE, PROMOTED, ROLLED_BACK, ROLLBACK_UNCERTAIN, AMBIGUOUS }
    public record PreparedSnapshot(Path stage, Path live, Path backup, Path journal, String transactionId, String stagedDigest) { }
    public record StepOutcome(FaultPoint point, boolean successful, boolean postconditionObserved, String detail) { }
    public record TransactionOutcome(TransactionState state, boolean restorationVerified, List<StepOutcome> steps) { }
    public record PromotionResult(Path live, Path backup, Path stage, Path journal, boolean hadLive,
                                  TransactionState state, String transactionId, String oldDigest,
                                  String stagedDigest, TransactionOutcome outcome) { }
    public record RollbackResult(TransactionState state, boolean restorationVerified, IOException failure, List<StepOutcome> steps) {
        public boolean complete() { return state == TransactionState.ROLLED_BACK && restorationVerified; }
    }
    public record RecoveryResult(TransactionState state, String message) {
        public boolean safeToProceed() { return state == TransactionState.NONE || state == TransactionState.ROLLED_BACK; }
    }
    private record Journal(String transactionId, Phase phase, Path stage, Path backup, boolean hadLive,
                           String oldDigest, String stagedDigest) { }

    public static final class PromotionException extends IOException {
        private final RollbackResult rollback;
        private final PreparedSnapshot prepared;
        private final boolean hadLive;
        private final String oldDigest;
        private final TransactionOutcome outcome;
        PromotionException(String message, Throwable cause, RollbackResult rollback, PreparedSnapshot prepared,
                           boolean hadLive, String oldDigest, TransactionOutcome outcome) {
            super(message, cause);
            this.rollback = rollback;
            this.prepared = prepared;
            this.hadLive = hadLive;
            this.oldDigest = oldDigest;
            this.outcome = outcome;
        }
        public RollbackResult rollback() { return rollback; }
        public PreparedSnapshot prepared() { return prepared; }
        public boolean hadLive() { return hadLive; }
        public String oldDigest() { return oldDigest; }
        public TransactionOutcome outcome() { return outcome; }
    }
}
