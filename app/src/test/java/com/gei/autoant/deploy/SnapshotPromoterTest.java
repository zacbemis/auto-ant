package com.gei.autoant.deploy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnapshotPromoterTest {
    @TempDir Path tempDir;

    @Test
    void promotionIsFullSnapshotSoDeletedAndRenamedFilesDisappear() throws Exception {
        Path source = tempDir.resolve("build");
        Path live = tempDir.resolve("live");
        Files.createDirectories(source.resolve("WEB-INF"));
        Files.createDirectories(live);
        Files.writeString(source.resolve("renamed.txt"), "new");
        Files.writeString(live.resolve("old.txt"), "stale");

        SnapshotPromoter promoter = new SnapshotPromoter();
        SnapshotPromoter.PromotionResult promotion = promoter.promote(promoter.stage(source, live));
        promoter.complete(promotion);

        assertFalse(Files.exists(live.resolve("old.txt")));
        assertEquals("new", Files.readString(live.resolve("renamed.txt")));
        assertTrue(Files.isDirectory(live.resolve("WEB-INF")));
    }

    @Test
    void rollbackRestoresPreviousLiveSnapshot() throws Exception {
        Path source = tempDir.resolve("build");
        Path live = tempDir.resolve("live");
        Files.createDirectories(source);
        Files.createDirectories(live);
        Files.writeString(source.resolve("new.txt"), "new");
        Files.writeString(live.resolve("old.txt"), "old");

        SnapshotPromoter promoter = new SnapshotPromoter();
        SnapshotPromoter.PromotionResult promotion = promoter.promote(promoter.stage(source, live));
        assertTrue(promoter.rollback(promotion).complete());

        assertTrue(Files.exists(live.resolve("old.txt")));
        assertFalse(Files.exists(live.resolve("new.txt")));
    }

    @Test
    void recoveryRestoresUnambiguouslyMovedLive() throws Exception {
        Path source = tempDir.resolve("build");
        Path live = tempDir.resolve("live");
        Files.createDirectories(source);
        Files.createDirectories(live);
        Files.writeString(source.resolve("new.txt"), "new");
        Files.writeString(live.resolve("old.txt"), "old");
        SnapshotPromoter promoter = new SnapshotPromoter();
        SnapshotPromoter.PreparedSnapshot prepared = promoter.stage(source, live);
        java.util.Properties journal = journal(prepared, "LIVE_MOVED_TO_BACKUP", true, digest(prepared.live()), prepared.stagedDigest());
        Files.move(prepared.live(), prepared.backup());
        try (var output = Files.newOutputStream(prepared.journal())) { journal.store(output, "test crash"); }
        SnapshotPromoter.RecoveryResult recovery = promoter.recover(live);
        assertTrue(recovery.safeToProceed());
        assertEquals("old", Files.readString(live.resolve("old.txt")));
    }

    @Test
    void faultsAfterEveryMutationOrJournalWriteReturnStructuredVerifiedOrUncertainOutcomes() throws Exception {
        for (SnapshotPromoter.FaultPoint point : SnapshotPromoter.FaultPoint.values()) {
            Path caseRoot = tempDir.resolve(point.name());
            Path source = caseRoot.resolve("build");
            Path live = caseRoot.resolve("live");
            Files.createDirectories(source);
            Files.createDirectories(live);
            Files.writeString(source.resolve("new.txt"), "new");
            Files.writeString(live.resolve("old.txt"), "old");
            SnapshotPromoter promoter = new SnapshotPromoter(actual -> {
                boolean restorationPoint = point == SnapshotPromoter.FaultPoint.AFTER_RESTORE_LIVE_TO_STAGE
                        || point == SnapshotPromoter.FaultPoint.AFTER_RESTORE_BACKUP_TO_LIVE
                        || point == SnapshotPromoter.FaultPoint.AFTER_ROLLED_BACK_JOURNAL;
                if (actual == point || (restorationPoint && actual == SnapshotPromoter.FaultPoint.AFTER_STAGE_MOVED_JOURNAL)) {
                    throw new java.io.IOException("injected " + point);
                }
            });
            SnapshotPromoter.PreparedSnapshot prepared = promoter.stage(source, live);
            SnapshotPromoter.PromotionException failure = assertThrows(SnapshotPromoter.PromotionException.class,
                    () -> promoter.promote(prepared), point.name());
            assertFalse(failure.outcome().steps().isEmpty(), point.name());
            if (failure.rollback().complete()) {
                assertEquals("old", Files.readString(live.resolve("old.txt")), point.name());
                assertFalse(Files.exists(prepared.backup()), point.name());
            } else {
                assertTrue(Files.exists(prepared.journal()), point.name());
                assertTrue(Files.exists(prepared.live()) || Files.exists(prepared.stage()) || Files.exists(prepared.backup()), point.name());
            }
        }
    }

    @Test
    void corruptOrNeighborTargetJournalNeverMutatesSibling() throws Exception {
        Path source = tempDir.resolve("build-corrupt");
        Path live = tempDir.resolve("app");
        Path neighbor = tempDir.resolve("neighbor");
        Files.createDirectories(source);
        Files.createDirectories(live);
        Files.createDirectories(neighbor);
        Files.writeString(source.resolve("new.txt"), "new");
        Files.writeString(live.resolve("old.txt"), "old");
        Files.writeString(neighbor.resolve("keep.txt"), "keep");
        SnapshotPromoter promoter = new SnapshotPromoter();
        SnapshotPromoter.PreparedSnapshot prepared = promoter.stage(source, live);
        Properties corrupt = journal(prepared, "LIVE_MOVED_TO_BACKUP", true, digest(live), prepared.stagedDigest());
        corrupt.setProperty("backup", neighbor.toString());
        try (var output = Files.newOutputStream(prepared.journal())) { corrupt.store(output, "corrupt"); }

        SnapshotPromoter.RecoveryResult result = promoter.recover(live);
        assertEquals(SnapshotPromoter.TransactionState.AMBIGUOUS, result.state());
        assertEquals("keep", Files.readString(neighbor.resolve("keep.txt")));
        assertEquals("old", Files.readString(live.resolve("old.txt")));
        assertTrue(Files.exists(prepared.stage()));
        assertTrue(Files.exists(prepared.journal()));
    }

    private Properties journal(SnapshotPromoter.PreparedSnapshot prepared, String phase, boolean hadLive,
                               String oldDigest, String stagedDigest) {
        Properties journal = new Properties();
        journal.setProperty("journal.version", "1");
        journal.setProperty("transaction.id", prepared.transactionId());
        journal.setProperty("phase", phase);
        journal.setProperty("live", prepared.live().toString());
        journal.setProperty("stage", prepared.stage().toString());
        journal.setProperty("backup", prepared.backup().toString());
        journal.setProperty("hadLive", Boolean.toString(hadLive));
        journal.setProperty("old.digest", oldDigest);
        journal.setProperty("staged.digest", stagedDigest);
        return journal;
    }

    private String digest(Path root) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        for (Path file : Files.walk(root).filter(Files::isRegularFile).sorted().toList()) {
            digest.update(root.relativize(file).toString().replace('\\', '/').getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
}
