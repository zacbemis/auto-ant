package com.gei.autoant.cli;

import com.gei.autoant.model.ReloadStrategy;
import com.gei.autoant.tomcat.TomcatManagerClient;
import com.gei.autoant.tomcat.TomcatManagerResponse;
import com.gei.autoant.util.PathUtils;
import com.gei.autoant.util.PropertiesUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

public final class ReloadCommand {
    private final CommandContext context;
    private final TomcatManagerClient managerClient;

    public ReloadCommand(CommandContext context) {
        this(context, new TomcatManagerClient());
    }

    ReloadCommand(CommandContext context, TomcatManagerClient managerClient) {
        this.context = context;
        this.managerClient = managerClient;
    }

    public int run(String[] args) {
        CommandLine commandLine = CommandLine.parse(args);
        if (commandLine.hasAnyOption("help", "h")) {
            printHelp();
            return 0;
        }

        try {
            Path projectRoot = commandLine.option("root")
                    .map(value -> PathUtils.resolve(context.projectRoot(), value))
                    .orElse(context.projectRoot())
                    .toAbsolutePath()
                    .normalize();
            ReloadProperties properties = ReloadProperties.load(projectRoot);
            ReloadStrategy strategy = commandLine.option("strategy")
                    .or(() -> commandLine.option("reload-strategy"))
                    .map(ReloadStrategy::parse)
                    .orElse(properties.strategy());

            return switch (strategy) {
                case MANAGER -> reloadWithManager(properties);
                case TOUCH_WEBXML -> touchWebXml(properties);
                case NONE -> printManualReload(properties);
            };
        } catch (IllegalArgumentException ex) {
            context.err().println("reload: " + ex.getMessage());
            return 2;
        } catch (IOException ex) {
            context.err().println("reload: " + ex.getMessage());
            return 1;
        }
    }

    private int reloadWithManager(ReloadProperties properties) throws IOException {
        TomcatManagerResponse response = managerClient.reload(
                properties.requiredLocal("tomcat.manager.url"),
                properties.contextPath(),
                properties.local("tomcat.manager.user").orElse(""),
                properties.local("tomcat.manager.password").orElse("")
        );
        if (response.successful()) {
            context.out().println("Reloaded Tomcat context " + properties.contextPath() + " using Tomcat Manager.");
            if (!response.body().isBlank()) {
                context.out().println(response.body());
            }
            return 0;
        }

        context.err().println("Tomcat Manager reload failed with HTTP " + response.statusCode() + ".");
        if (!response.body().isBlank()) {
            context.err().println(response.body());
        }
        return 1;
    }

    private int touchWebXml(ReloadProperties properties) throws IOException {
        Path webXml = properties.deployedWebXml();
        if (!Files.exists(webXml)) {
            context.err().println("reload: deployed WEB-INF/web.xml not found: " + PathUtils.toPortableString(webXml));
            context.err().println("Run ant deploy-exploded first or use reload.strategy=manager/none.");
            return 1;
        }

        Files.setLastModifiedTime(webXml, FileTime.from(Instant.now()));
        context.out().println("Touched " + PathUtils.toPortableString(webXml) + " to trigger Tomcat reload.");
        return 0;
    }

    private int printManualReload(ReloadProperties properties) {
        context.out().println("Reload strategy is none. Reload or restart Tomcat manually for " + properties.contextPath() + ".");
        return 0;
    }

    private void printHelp() {
        context.out().println("Usage: auto-ant reload [options]");
        context.out().println();
        context.out().println("Reloads the configured Tomcat context using reload.strategy from auto-ant.properties.");
        context.out().println();
        context.out().println("Options:");
        context.out().println("  --root <path>              Project root. Defaults to current directory.");
        context.out().println("  --strategy <name>          manager, touch-webxml, or none.");
        context.out().println("  --reload-strategy <name>   Alias for --strategy.");
    }

    private record ReloadProperties(Path projectRoot, Properties shared, Properties local) {
        private static ReloadProperties load(Path projectRoot) throws IOException {
            Path sharedPath = projectRoot.resolve("auto-ant.properties");
            if (!Files.exists(sharedPath)) {
                throw new IllegalArgumentException("auto-ant.properties not found in "
                        + PathUtils.toPortableString(projectRoot)
                        + ". Run auto-ant init first.");
            }
            return new ReloadProperties(
                    projectRoot,
                    PropertiesUtils.loadIfExists(sharedPath),
                    PropertiesUtils.loadIfExists(projectRoot.resolve("auto-ant.local.properties"))
            );
        }

        private ReloadStrategy strategy() {
            return shared("reload.strategy")
                    .map(ReloadStrategy::parse)
                    .orElse(ReloadStrategy.NONE);
        }

        private String contextPath() {
            return shared("context.path")
                    .orElseGet(() -> "/" + shared("app.name").orElse("app"));
        }

        private Path deployedWebXml() {
            Path catalinaBase = PathUtils.resolve(projectRoot, local("catalina.base").or(() -> local("tomcat.home"))
                    .orElseThrow(() -> new IllegalArgumentException("Set catalina.base in auto-ant.local.properties.")));
            String appName = requiredShared("app.name");
            return catalinaBase.resolve("webapps").resolve(appName).resolve("WEB-INF").resolve("web.xml").normalize();
        }

        private String requiredShared(String key) {
            return shared(key).orElseThrow(() -> new IllegalArgumentException("Set " + key + " in auto-ant.properties."));
        }

        private String requiredLocal(String key) {
            return local(key).orElseThrow(() -> new IllegalArgumentException("Set " + key + " in auto-ant.local.properties."));
        }

        private Optional<String> shared(String key) {
            return property(shared, key);
        }

        private Optional<String> local(String key) {
            return property(local, key);
        }

        private Optional<String> property(Properties properties, String key) {
            return Optional.ofNullable(properties.getProperty(key)).map(String::trim).filter(value -> !value.isBlank());
        }
    }
}