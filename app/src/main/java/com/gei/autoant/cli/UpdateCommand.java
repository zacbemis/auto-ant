package com.gei.autoant.cli;

import com.gei.autoant.detect.ProjectDetector;
import com.gei.autoant.generate.GeneratedFile;
import com.gei.autoant.generate.GenerationResult;
import com.gei.autoant.generate.UpdateGenerator;
import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.model.ReloadStrategy;
import com.gei.autoant.prompt.NonInteractiveOptions;
import com.gei.autoant.util.PathUtils;
import com.gei.autoant.util.PropertiesUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

public final class UpdateCommand {
    private final CommandContext context;

    public UpdateCommand(CommandContext context) {
        this.context = context;
    }

    public int run(String[] args) {
        CommandLine commandLine = CommandLine.parse(args);
        if (commandLine.hasAnyOption("help", "h")) {
            printHelp();
            return 0;
        }

        try {
            NonInteractiveOptions options = seedFromExistingProperties(NonInteractiveOptions.from(commandLine, context.projectRoot()));
            ProjectModel model = new ProjectDetector().detect(options.projectRoot(), options);
            if (options.interactive()) {
                options = new InteractiveOptionsPrompter(context).promptForOverrides(model, options);
                model = new ProjectDetector().detect(options.projectRoot(), options);
            }

            GenerationResult result = new UpdateGenerator(model.projectRoot()).update(model, sharedOverrideKeys(commandLine), localOverrideKeys(commandLine));
            context.out().println("auto-ant update");
            context.out().println();
            for (GeneratedFile generatedFile : result.files()) {
                context.out().println(generatedFile.message());
            }
            context.out().println();
            context.out().println("Active auto-ant build file: " + model.projectRoot().relativize(result.buildFile()).toString().replace('\\', '/'));
            context.out().println("No deploy was run. Use auto-ant reconcile (or auto-ant reconcile --confirm-stopped) to redeploy safely.");
            return 0;
        } catch (IllegalArgumentException ex) {
            context.err().println("update: " + ex.getMessage());
            return 2;
        } catch (IOException ex) {
            context.err().println("update: failed to update files: " + ex.getMessage());
            return 1;
        }
    }

    private NonInteractiveOptions seedFromExistingProperties(NonInteractiveOptions options) throws IOException {
        Path root = options.projectRoot();
        Properties shared = PropertiesUtils.loadIfExists(root.resolve("auto-ant.properties"));
        Properties local = PropertiesUtils.loadIfExists(root.resolve("auto-ant.local.properties"));
        NonInteractiveOptions.Builder builder = options.toBuilder();

        if (options.appName().isEmpty()) {
            property(shared, "app.name").ifPresent(builder::appName);
        }
        if (options.contextPath().isEmpty()) {
            property(shared, "context.path").ifPresent(builder::contextPath);
        }
        if (options.javaRelease().isEmpty()) {
            property(shared, "java.release").map(this::parseInteger).ifPresent(builder::javaRelease);
        }
        if (options.sourceRoots().isEmpty()) {
            property(shared, "src.dirs").map(value -> PathUtils.parsePathList(root, value)).ifPresent(builder::sourceRoots);
        }
        if (options.webRoot().isEmpty()) {
            property(shared, "web.dir").map(value -> PathUtils.resolve(root, value)).ifPresent(builder::webRoot);
        }
        if (options.webInf().isEmpty()) {
            property(shared, "webinf.dir").map(value -> PathUtils.resolve(root, value)).ifPresent(builder::webInf);
        }
        if (options.libraryRoots().isEmpty()) {
            property(shared, "lib.dirs").map(value -> PathUtils.parsePathList(root, value)).ifPresent(builder::libraryRoots);
        }
        if (options.reloadStrategy().isEmpty()) {
            property(shared, "reload.strategy").map(ReloadStrategy::parse).ifPresent(builder::reloadStrategy);
        }
        if (options.tomcatManagerUrl().isEmpty()) {
            property(local, "tomcat.manager.url").or(() -> property(shared, "tomcat.manager.url")).ifPresent(builder::tomcatManagerUrl);
        }
        if (options.tomcatHome().isEmpty()) {
            property(local, "tomcat.home").map(value -> PathUtils.resolve(root, value)).ifPresent(builder::tomcatHome);
        }
        if (options.jdkHome().isEmpty()) {
            java.util.Optional<String> jdkHome = property(local, "jdk.home");
            if (jdkHome.isEmpty()) {
                jdkHome = javaHomeFromVsCodeSettings(root.resolve(".vscode/settings.json"));
            }
            jdkHome
                    .map(value -> PathUtils.resolve(root, value))
                    .ifPresent(builder::jdkHome);
        }

        return builder.build();
    }

    private java.util.Optional<String> javaHomeFromVsCodeSettings(Path settingsJson) throws IOException {
        if (java.nio.file.Files.notExists(settingsJson)) {
            return java.util.Optional.empty();
        }
        String content = java.nio.file.Files.readString(settingsJson);
        String key = "\"java.jdt.ls.java.home\"";
        int keyIndex = content.indexOf(key);
        if (keyIndex < 0) {
            return java.util.Optional.empty();
        }
        int colon = content.indexOf(':', keyIndex + key.length());
        if (colon < 0) {
            return java.util.Optional.empty();
        }
        int valueStart = content.indexOf('"', colon + 1);
        if (valueStart < 0) {
            return java.util.Optional.empty();
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int i = valueStart + 1; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (escaped) {
                value.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                String parsed = value.toString().trim();
                return parsed.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(parsed);
            } else {
                value.append(ch);
            }
        }
        return java.util.Optional.empty();
    }

    private java.util.Optional<String> property(Properties properties, String key) {
        return java.util.Optional.ofNullable(properties.getProperty(key)).map(String::trim).filter(value -> !value.isEmpty());
    }

    private Set<String> sharedOverrideKeys(CommandLine commandLine) {
        Set<String> keys = new LinkedHashSet<>();
        if (commandLine.hasOption("app")) {
            keys.add("app.name");
        }
        if (commandLine.hasAnyOption("context", "context-path")) {
            keys.add("context.path");
            keys.add("context.deploy.name");
            keys.add("context.descriptor.file.name");
        }
        if (commandLine.hasOption("java")) {
            keys.add("java.release");
        }
        if (commandLine.hasAnyOption("src", "source")) {
            keys.add("src.dirs");
        }
        if (commandLine.hasOption("web")) {
            keys.add("web.dir");
        }
        if (commandLine.hasAnyOption("webinf", "web-inf")) {
            keys.add("webinf.dir");
        }
        if (commandLine.hasAnyOption("lib", "libs")) {
            keys.add("lib.dirs");
        }
        return keys;
    }

    private Set<String> localOverrideKeys(CommandLine commandLine) {
        Set<String> keys = new LinkedHashSet<>();
        if (commandLine.hasOption("tomcat")) {
            keys.add("tomcat.home");
            keys.add("catalina.base");
            keys.add("deploy.dir");
            keys.add("context.descriptor.dir");
        }
        if (commandLine.hasAnyOption("context", "context-path")) {
            keys.add("deploy.dir");
            keys.add("context.descriptor.dir");
        }
        if (commandLine.hasAnyOption("jdk", "jdk-home")) {
            keys.add("jdk.home");
        }
        if (commandLine.hasAnyOption("tomcat-manager-url", "manager-url")) {
            keys.add("tomcat.manager.url");
        }
        return keys;
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expected integer value for java.release, got: " + value);
        }
    }

    private void printHelp() {
        context.out().println("Usage: auto-ant update [options]");
        context.out().println();
        context.out().println("Updates auto-ant generated files for the current auto-ant version without deploying.");
        context.out().println("Existing project/local properties are preserved; missing keys are appended.");
        context.out().println("VS Code auto-ant tasks/settings are refreshed while unrelated user content is preserved.");
        context.out().println();
        context.out().println("Options match doctor/init detection overrides, including --root, --src, --web, --lib, --tomcat, --java, and --interactive.");
    }
}
