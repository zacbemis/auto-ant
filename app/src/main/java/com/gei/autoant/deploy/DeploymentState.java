package com.gei.autoant.deploy;

import com.gei.autoant.util.PathUtils;
import com.gei.autoant.util.PropertiesUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class DeploymentState {
    private final Path stateDirectory;
    private final Path manifest;
    private final Path stale;

    public DeploymentState(ReconcileConfiguration configuration) throws IOException {
        String id = shortHash(configuration.identity());
        stateDirectory = configuration.projectRoot().resolve(".auto-ant").resolve("state");
        manifest = stateDirectory.resolve(id + ".manifest.properties");
        stale = stateDirectory.resolve(id + ".stale.properties");
    }

    public String fingerprint(ReconcileConfiguration configuration) throws IOException {
        MessageDigest digest = digest();
        Path root = configuration.projectRoot().toRealPath();
        Set<Path> inputs = new LinkedHashSet<>();
        Set<Path> inputRoots = new LinkedHashSet<>();
        Properties shared = PropertiesUtils.loadIfExists(root.resolve("auto-ant.properties"));
        addRoot(inputRoots, root, configuration.webDirectory());
        addConfiguredRoots(inputRoots, root, shared.getProperty("src.dirs", ""));
        addConfiguredRoots(inputRoots, root, shared.getProperty("lib.dirs", ""));
        inputs.addAll(inputRoots);
        inputs.add(root.resolve("auto-ant.properties"));
        inputs.add(root.resolve("auto-ant.local.properties"));
        inputs.add(root.resolve("auto-ant.build.xml"));
        if (Files.exists(root.resolve("auto-ant.user.xml"), LinkOption.NOFOLLOW_LINKS)) inputs.add(root.resolve("auto-ant.user.xml"));

        Set<Path> excludes = new LinkedHashSet<>();
        excludes.add(root.resolve(".git").toAbsolutePath().normalize());
        excludes.add(root.resolve(".auto-ant").toAbsolutePath().normalize());
        excludes.add(configuration.deployDirectory().toAbsolutePath().normalize());
        addConfiguredExclude(excludes, root, shared, "build.dir");
        addConfiguredExclude(excludes, root, shared, "build.web.dir");
        addConfiguredExclude(excludes, root, shared, "classes.dir");
        addConfiguredExclude(excludes, root, shared, "dist.dir");

        for (Path inputRoot : inputRoots) {
            Path normalized = inputRoot.toAbsolutePath().normalize();
            validateInputRoot(normalized);
            if (excludes.stream().anyMatch(exclude -> normalized.startsWith(exclude) || exclude.startsWith(normalized))) {
                throw new IOException("Configured build input overlaps generated/deployment output: " + normalized);
            }
        }

        List<FileInput> files = new ArrayList<>();
        for (Path input : inputs) {
            Path normalized = input.toAbsolutePath().normalize();
            if (excluded(normalized, excludes)) continue;
            if (Files.notExists(normalized, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Configured build input does not exist: " + normalized);
            }
            if (Files.isSymbolicLink(normalized)) throw new IOException("Configured build input root is a symbolic link: " + normalized);
            if (Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                files.add(new FileInput(label(normalized), normalized));
            } else if (Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
                collect(normalized, excludes, files);
            } else {
                throw new IOException("Configured build input is a link or special file: " + normalized);
            }
        }
        files.sort(Comparator.comparing(FileInput::label));
        byte[] buffer = new byte[8192];
        for (FileInput file : files) {
            digest.update(file.label().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = new DigestInputStream(Files.newInputStream(file.path()), digest)) {
                while (input.read(buffer) >= 0) { }
            }
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public String treeDigest(Path directory) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) return "";
        MessageDigest digest = digest();
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new IOException("Deployment contains an unsupported link or special file: " + file);
                files.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')));
        byte[] buffer = new byte[8192];
        for (Path file : files) {
            digest.update(root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (InputStream input = new DigestInputStream(Files.newInputStream(file), digest)) {
                while (input.read(buffer) >= 0) { }
            }
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public boolean isCurrent(String fingerprint, Path live) throws IOException {
        if (!Files.isDirectory(live, LinkOption.NOFOLLOW_LINKS) || Files.notExists(manifest)) return false;
        Properties properties = load(manifest);
        String expectedLiveDigest = properties.getProperty("live.digest", "");
        return fingerprint.equals(properties.getProperty("fingerprint"))
                && !expectedLiveDigest.isBlank() && expectedLiveDigest.equals(treeDigest(live));
    }

    public boolean isStale() { return Files.exists(stale); }

    public String staleReason() {
        try { return load(stale).getProperty("reason", "unknown"); }
        catch (IOException ex) { return "unreadable stale state"; }
    }

    public void markStale(String reason) throws IOException { markState(reason, false); }
    public void markCritical(String reason) throws IOException { markState(reason, true); }

    private void markState(String reason, boolean critical) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("stale", "true");
        properties.setProperty("critical", Boolean.toString(critical));
        properties.setProperty("at", Instant.now().toString());
        properties.setProperty("reason", sanitize(reason));
        atomicStore(stale, properties, "auto-ant deployment stale/critical state");
    }

    public void markSuccess(String fingerprint, ReconcileConfiguration configuration) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("fingerprint", fingerprint);
        properties.setProperty("live.digest", treeDigest(configuration.deployDirectory()));
        properties.setProperty("completed.at", Instant.now().toString());
        properties.setProperty("context.path", configuration.contextPath());
        properties.setProperty("deploy.dir", configuration.deployDirectory().toString());
        atomicStore(manifest, properties, "auto-ant last verified successful reconcile");
        Files.deleteIfExists(stale);
    }

    public Path manifestPath() { return manifest; }

    private void addConfiguredRoots(Set<Path> roots, Path projectRoot, String values) {
        if (!values.isBlank()) PathUtils.parsePathList(projectRoot, values).forEach(path -> addRoot(roots, projectRoot, path));
    }

    private void addRoot(Set<Path> roots, Path projectRoot, Path path) {
        roots.add(path.isAbsolute() ? path.toAbsolutePath().normalize() : projectRoot.resolve(path).toAbsolutePath().normalize());
    }

    private void validateInputRoot(Path input) throws IOException {
        if (Files.notExists(input, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Configured build input does not exist: " + input);
        }
        if (Files.isSymbolicLink(input)) {
            throw new IOException("Configured build input root is a symbolic link: " + input);
        }
        if (!Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Configured build input root is not a directory: " + input);
        }
    }

    private void addConfiguredExclude(Set<Path> excludes, Path root, Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value != null && !value.isBlank()) excludes.add(PathUtils.resolve(root, value).toAbsolutePath().normalize());
    }

    private void collect(Path root, Set<Path> excludes, List<FileInput> files) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (Files.isSymbolicLink(dir)) throw new IOException("Build input contains a symbolic-link directory: " + dir);
                return !dir.equals(root) && excluded(dir.toAbsolutePath().normalize(), excludes) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isSymbolicLink() || Files.isSymbolicLink(file)) throw new IOException("Build input contains a symbolic-link file: " + file);
                if (!attrs.isRegularFile()) throw new IOException("Build input contains an unsupported special file: " + file);
                if (!excluded(file.toAbsolutePath().normalize(), excludes)) files.add(new FileInput(label(file), file));
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean excluded(Path path, Set<Path> excludes) { return excludes.stream().anyMatch(path::startsWith); }
    private String label(Path path) { return path.toAbsolutePath().normalize().toString().replace('\\', '/'); }

    private void atomicStore(Path target, Properties properties, String comment) throws IOException {
        Files.createDirectories(stateDirectory);
        Path temporary = Files.createTempFile(stateDirectory, target.getFileName().toString(), ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) { properties.store(output, comment); }
        try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException ex) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
    }

    private Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) { properties.load(input); }
        return properties;
    }

    private String sanitize(String value) {
        String result = value == null ? "unknown" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return result.length() > 1000 ? result.substring(0, 1000) : result;
    }

    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }

    private static String shortHash(String value) {
        return HexFormat.of().formatHex(digest().digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
    }

    private record FileInput(String label, Path path) { }
}
