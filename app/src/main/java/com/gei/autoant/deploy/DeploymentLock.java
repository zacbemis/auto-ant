package com.gei.autoant.deploy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

public final class DeploymentLock implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private final Path path;

    private DeploymentLock(FileChannel channel, FileLock lock, Path path) {
        this.channel = channel;
        this.lock = lock;
        this.path = path;
    }

    public static DeploymentLock acquire(ReconcileConfiguration configuration, long timeoutMillis) throws IOException, InterruptedException {
        Path target = configuration.deployDirectory();
        Path canonicalParent = target.getParent().toRealPath();
        String targetName = target.getFileName().toString();
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            targetName = targetName.toLowerCase(java.util.Locale.ROOT);
        }
        String identity = canonicalParent + "\n" + targetName;
        Path lockDirectory = canonicalParent.resolve(".auto-ant-locks");
        if (Files.exists(lockDirectory, java.nio.file.LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(lockDirectory)) {
            throw new IOException("Deployment lock namespace must not be a symbolic link: " + lockDirectory);
        }
        Files.createDirectories(lockDirectory);
        if (!lockDirectory.toRealPath().equals(lockDirectory.toAbsolutePath().normalize())) {
            throw new IOException("Deployment lock namespace aliases another path: " + lockDirectory);
        }
        Path path = lockDirectory.resolve(hash(identity) + ".lock");
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (true) {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    DeploymentLock result = new DeploymentLock(channel, lock, path);
                    result.writeOwner(configuration);
                    return result;
                }
            } catch (OverlappingFileLockException ignored) {
                // Another thread in this JVM owns the same OS lock.
            }
            if (timeoutMillis == 0 || System.nanoTime() >= deadline) {
                String owner = readOwner(path);
                channel.close();
                throw new LockUnavailableException("Deployment is locked by another auto-ant process" + (owner.isBlank() ? "." : ": " + owner));
            }
            Thread.sleep(Math.min(200, Math.max(1, timeoutMillis)));
        }
    }

    public Path path() {
        return path;
    }

    private void writeOwner(ReconcileConfiguration configuration) throws IOException {
        String owner = "pid=" + ProcessHandle.current().pid() + ", started=" + Instant.now()
                + ", project=" + configuration.projectRoot() + ", context=" + configuration.contextPath();
        byte[] bytes = owner.getBytes(StandardCharsets.UTF_8);
        channel.truncate(0);
        channel.position(0);
        channel.write(ByteBuffer.wrap(bytes));
        channel.force(true);
    }

    private static String readOwner(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            return "";
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            channel.close();
        }
    }

    public static final class LockUnavailableException extends IOException {
        public LockUnavailableException(String message) {
            super(message);
        }
    }
}
