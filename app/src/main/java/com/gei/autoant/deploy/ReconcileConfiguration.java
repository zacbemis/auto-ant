package com.gei.autoant.deploy;

import com.gei.autoant.model.ReloadStrategy;
import com.gei.autoant.util.PathUtils;
import com.gei.autoant.util.PropertiesUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public record ReconcileConfiguration(
        Path projectRoot,
        Path catalinaBase,
        Path deployDirectory,
        Path buildWebDirectory,
        Path webDirectory,
        String contextPath,
        String contextDeployName,
        ReloadStrategy reloadStrategy,
        String managerUrl,
        String managerUser,
        String managerPassword,
        int readinessTimeoutSeconds,
        int readinessPollMillis
) {
    public static final String SCHEMA_VERSION = "2";

    public static ReconcileConfiguration load(Path root) throws IOException {
        Path projectRoot = canonicalExistingDirectory(root.toAbsolutePath().normalize(), ".", "project root");
        Path sharedFile = projectRoot.resolve("auto-ant.properties");
        Path localFile = projectRoot.resolve("auto-ant.local.properties");
        if (Files.notExists(sharedFile) || Files.notExists(localFile)) {
            throw new IllegalArgumentException("auto-ant.properties and auto-ant.local.properties are required. Run auto-ant update.");
        }
        Properties shared = PropertiesUtils.loadIfExists(sharedFile);
        Properties local = PropertiesUtils.loadIfExists(localFile);
        String schema = required(shared, "auto.ant.schema.version", "auto-ant.properties");
        if (!SCHEMA_VERSION.equals(schema)) {
            throw new IllegalArgumentException("Configuration schema " + schema + " is not supported; run auto-ant update to migrate to schema " + SCHEMA_VERSION + ".");
        }

        String contextPath = required(shared, "context.path", "auto-ant.properties");
        if (!contextPath.startsWith("/") || contextPath.contains("?") || contextPath.contains("#")) {
            throw new IllegalArgumentException("context.path must be an absolute Tomcat context path such as /MyApp.");
        }
        String deployName = required(shared, "context.deploy.name", "auto-ant.properties");
        if (deployName.contains("/") || deployName.contains("\\") || deployName.equals(".") || deployName.equals("..")) {
            throw new IllegalArgumentException("context.deploy.name must be a single safe Tomcat appBase name.");
        }

        Path catalinaBase = canonicalExistingDirectory(projectRoot,
                optional(local, "catalina.base").or(() -> optional(local, "tomcat.home"))
                        .orElseThrow(() -> new IllegalArgumentException("Set catalina.base or tomcat.home in auto-ant.local.properties.")),
                "catalina.base");
        Path webapps = canonicalExistingDirectory(catalinaBase, "webapps", "Tomcat webapps");
        if (!Files.isDirectory(webapps)) {
            throw new IllegalArgumentException("Tomcat webapps directory does not exist: " + PathUtils.toPortableString(webapps));
        }
        Path expectedDeploy = canonicalTarget(webapps.resolve(deployName), "expected deployment");
        Path deploy = canonicalTarget(optional(local, "deploy.dir")
                .map(value -> PathUtils.resolve(projectRoot, value).toAbsolutePath().normalize())
                .orElse(expectedDeploy), "deploy.dir");
        validateTargetIdentity(projectRoot, local, deploy, expectedDeploy);

        Path buildWeb = canonicalFuturePath(requiredPath(projectRoot, shared, "build.web.dir", "auto-ant.properties"), "build.web.dir");
        Path web = canonicalConfiguredInput(requiredPath(projectRoot, shared, "web.dir", "auto-ant.properties"), "web.dir");
        Path controlledState = canonicalTarget(projectRoot.resolve(".auto-ant"), ".auto-ant state root");
        if (overlaps(deploy, buildWeb) || overlaps(deploy, projectRoot) || deploy.equals(catalinaBase) || catalinaBase.startsWith(deploy)
                || overlaps(deploy, controlledState)
                || (deploy.startsWith(catalinaBase) && !deploy.getParent().equals(webapps))) {
            throw new IllegalArgumentException("Unsafe deployment identity: deploy.dir overlaps a build, project, or Catalina root.");
        }

        ReloadStrategy strategy = optional(shared, "reload.strategy").map(ReloadStrategy::parse).orElse(ReloadStrategy.NONE);
        int timeout = positiveInt(shared, "reconcile.readiness.timeout.seconds", 60);
        int poll = positiveInt(shared, "reconcile.readiness.poll.millis", 500);
        return new ReconcileConfiguration(projectRoot, catalinaBase, deploy, buildWeb, web, contextPath, deployName,
                strategy, optional(local, "tomcat.manager.url").orElse(""),
                optional(local, "tomcat.manager.user").orElse(""), optional(local, "tomcat.manager.password").orElse(""),
                timeout, poll);
    }

    public boolean managerLifecycleConfigured() {
        return reloadStrategy == ReloadStrategy.MANAGER && !managerUrl.isBlank();
    }

    public String identity() throws IOException {
        Path parent = deployDirectory.getParent();
        if (parent == null) {
            throw new IOException("Deployment target has no parent directory.");
        }
        String targetName = deployDirectory.getFileName().toString();
        if (isWindows()) {
            targetName = targetName.toLowerCase(java.util.Locale.ROOT);
        }
        return parent.toRealPath() + "\n" + targetName;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    private static void validateTargetIdentity(Path projectRoot, Properties local, Path deploy, Path expectedDeploy) {
        if (deploy.equals(expectedDeploy)) {
            return;
        }
        Path docBase = optional(local, "context.descriptor.docBase")
                .map(value -> {
                    try { return canonicalTarget(PathUtils.resolve(projectRoot, value).toAbsolutePath().normalize(), "context.descriptor.docBase"); }
                    catch (IOException ex) { throw new IllegalArgumentException(ex.getMessage(), ex); }
                })
                .orElse(null);
        if (docBase == null || !docBase.equals(deploy)) {
            throw new IllegalArgumentException("deploy.dir does not match catalina.base/webapps/context.deploy.name. For an external deployment, set context.descriptor.docBase to the same canonical path.");
        }
    }

    private static Path canonicalExistingDirectory(Path root, String value, String key) throws IOException {
        Path path = PathUtils.resolve(root, value).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Configured " + key + " directory does not exist: " + PathUtils.toPortableString(path));
        }
        return path.toRealPath();
    }

    private static Path canonicalTarget(Path path, String key) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new IllegalArgumentException("Configured " + key + " parent does not exist: " + PathUtils.toPortableString(parent == null ? normalized : parent));
        }
        Path canonical = parent.toRealPath().resolve(normalized.getFileName()).normalize();
        if (Files.exists(canonical, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(canonical)) throw new IllegalArgumentException("Configured " + key + " must not be a linked target: " + canonical);
            canonical = canonical.toRealPath();
        }
        return canonical;
    }

    private static Path canonicalConfiguredInput(Path path, String key) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw new IllegalArgumentException("Configured " + key + " must be an existing non-linked directory: " + PathUtils.toPortableString(normalized));
        }
        return normalized.toRealPath();
    }

    private static Path canonicalFuturePath(Path path, String key) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        Path existing = normalized;
        while (existing != null && Files.notExists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (existing == null || !Files.isDirectory(existing, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(existing)) {
            throw new IllegalArgumentException("Configured " + key + " has no safe existing parent: " + PathUtils.toPortableString(normalized));
        }
        Path canonicalExisting = existing.toRealPath();
        Path suffix = existing.relativize(normalized);
        Path reconstructed = canonicalExisting.resolve(suffix).normalize();
        if (!reconstructed.startsWith(canonicalExisting)) {
            throw new IllegalArgumentException("Configured " + key + " escapes its canonical existing parent: " + PathUtils.toPortableString(normalized));
        }
        return reconstructed;
    }

    private static boolean overlaps(Path first, Path second) {
        return first.startsWith(second) || second.startsWith(first);
    }

    private static Path requiredPath(Path root, Properties properties, String key, String file) {
        return PathUtils.resolve(root, required(properties, key, file)).toAbsolutePath().normalize();
    }

    private static String required(Properties properties, String key, String file) {
        return optional(properties, key).orElseThrow(() -> new IllegalArgumentException("Set " + key + " in " + file + "."));
    }

    private static Optional<String> optional(Properties properties, String key) {
        return Optional.ofNullable(properties.getProperty(key)).map(String::trim).filter(value -> !value.isBlank());
    }

    private static int positiveInt(Properties properties, String key, int defaultValue) {
        String value = optional(properties, key).orElse(Integer.toString(defaultValue));
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be a positive integer.");
        }
    }
}
