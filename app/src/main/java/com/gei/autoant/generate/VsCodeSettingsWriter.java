package com.gei.autoant.generate;

import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.util.JsonUtils;
import com.gei.autoant.util.PathUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

public final class VsCodeSettingsWriter {
    private static final String FRONTEND_EXTENSIONS = "html|htm|css|js|ts|png|jpg|jpeg|gif|svg|webp|ico|woff|woff2";
    private static final String JSP_VIEW_EXTENSIONS = "jsp|jspf|tag|tagx|tld";
    private static final String WEB_INF_VIEW_EXTENSIONS = FRONTEND_EXTENSIONS + "|" + JSP_VIEW_EXTENSIONS;

    public String write(ProjectModel model) {
        return write(model, model.projectRoot().resolve(InitGenerator.AUTO_ANT_BUILD_FILE));
    }

    public String write(ProjectModel model, Path buildFile) {
        String frontendPattern = publicFilesUnderWebRootPattern(ModelValues.relativePath(model, ModelValues.webRoot(model)));
        String webInfViewPattern = "("
                + filesUnderPattern(ModelValues.relativePath(model, ModelValues.webInf(model)), "\\.(" + WEB_INF_VIEW_EXTENSIONS + ")$")
                + "|"
                + filesUnderPattern(ModelValues.relativePath(model, ModelValues.webRoot(model)), "\\.(" + JSP_VIEW_EXTENSIONS + ")$")
                + ")";
        String javaPattern = ModelValues.sourceRoots(model).stream()
                .map(sourceRoot -> filesUnderPattern(ModelValues.relativePath(model, sourceRoot), "\\.java$"))
                .collect(Collectors.joining("|", "(", ")"));
        String configPattern = ".*(WEB-INF[/\\\\]web\\.xml|context\\.xml|\\.(properties|xml|jar))$";
        String referencedLibraries = referencedLibraries(model);
        String jdkSettings = jdkSettings(model);
        String developCommand = "auto-ant develop --root " + quoteShellPath(model.projectRoot()) + " --kind ";

        return "// AUTO-ANT MANAGED SETTINGS - EDIT WITH CARE.\n"
                + "// auto-ant update may refresh auto-ant-managed keys such as filewatcher commands,\n"
                + "// Java runtime settings, terminal Java environment, and referenced libraries.\n"
                + "// Unrelated user settings are preserved.\n"
                + "{\n"
                + "  \"java.project.referencedLibraries\": [\n"
                + referencedLibraries
                + "  ],\n"
                + jdkSettings
                + "  \"filewatcher.isSyncRunEvents\": true,\n"
                + "  \"filewatcher.autoClearConsole\": false,\n"
                + "  \"filewatcher.commands\": [\n"
                + command(frontendPattern, developCommand + "frontend") + ",\n"
                + command(webInfViewPattern, developCommand + "views") + ",\n"
                + command(javaPattern, developCommand + "classes") + ",\n"
                + command(configPattern, developCommand + "config") + "\n"
                + "  ]\n"
                + "}\n";
    }

    private String jdkSettings(ProjectModel model) {
        String jdkHome = ModelValues.jdkHome(model);
        if (jdkHome.isBlank()) {
            return "";
        }

        String jdkBin = withPathSegment(jdkHome, "bin");
        return "  \"java.jdt.ls.java.home\": " + JsonUtils.quote(jdkHome) + ",\n"
                + "  \"java.configuration.runtimes\": [\n"
                + "    {\n"
                + "      \"name\": \"" + javaRuntimeName(model) + "\",\n"
                + "      \"path\": " + JsonUtils.quote(jdkHome) + ",\n"
                + "      \"default\": true\n"
                + "    }\n"
                + "  ],\n"
                + "  \"terminal.integrated.env.windows\": {\n"
                + "    \"JAVA_HOME\": " + JsonUtils.quote(jdkHome) + ",\n"
                + "    \"PATH\": " + JsonUtils.quote(jdkBin + ";${env:PATH}") + "\n"
                + "  },\n"
                + "  \"terminal.integrated.env.osx\": {\n"
                + "    \"JAVA_HOME\": " + JsonUtils.quote(jdkHome) + ",\n"
                + "    \"PATH\": " + JsonUtils.quote(jdkBin + ":${env:PATH}") + "\n"
                + "  },\n"
                + "  \"terminal.integrated.env.linux\": {\n"
                + "    \"JAVA_HOME\": " + JsonUtils.quote(jdkHome) + ",\n"
                + "    \"PATH\": " + JsonUtils.quote(jdkBin + ":${env:PATH}") + "\n"
                + "  },\n";
    }

    private String javaRuntimeName(ProjectModel model) {
        int release = ModelValues.javaRelease(model);
        return release == 8 ? "JavaSE-1.8" : "JavaSE-" + release;
    }

    private String referencedLibraries(ProjectModel model) {
        List<String> libraries = new ArrayList<>();
        for (Path libraryRoot : ModelValues.libraryRoots(model)) {
            libraries.add(withJarGlob(ModelValues.relativePath(model, libraryRoot)));
        }
        model.tomcatHome().value()
                .map(path -> path.resolve("lib"))
                .map(PathUtils::toPortableString)
                .map(this::withJarGlob)
                .ifPresent(libraries::add);

        return new LinkedHashSet<>(libraries).stream()
                .map(JsonUtils::quote)
                .map(value -> "    " + value)
                .collect(Collectors.joining(",\n", "", libraries.isEmpty() ? "" : "\n"));
    }

    private String withJarGlob(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/**/*.jar";
    }

    private String withPathSegment(String path, String segment) {
        String normalized = path.replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/" + segment;
    }

    private String quoteShellPath(Path path) {
        return "\"" + PathUtils.toPortableString(path.toAbsolutePath().normalize()).replace("\"", "\\\"") + "\"";
    }

    private String command(String match, String command) {
        return "    {\n"
                + "      \"match\": " + JsonUtils.quote(match) + ",\n"
                + "      \"event\": \"onFileChange,onFileCreate,onFileDelete\",\n"
                + "      \"isAsync\": false,\n"
                + "      \"cmd\": " + JsonUtils.quote(command) + "\n"
                + "    }";
    }

    private String filesUnderPattern(String relativePath, String fileSuffixPattern) {
        return pathPattern(relativePath) + ".*" + fileSuffixPattern;
    }

    private String publicFilesUnderWebRootPattern(String relativePath) {
        return pathPattern(relativePath) + "(?!(WEB-INF|META-INF)[/\\\\]).*\\.(" + FRONTEND_EXTENSIONS + ")$";
    }

    private String pathPattern(String relativePath) {
        String normalized = relativePath.replace('\\', '/').trim();
        if (normalized.isEmpty() || ".".equals(normalized)) {
            return ".*";
        }
        String segments = Arrays.stream(normalized.split("/"))
                .filter(segment -> !segment.isBlank() && !".".equals(segment))
                .map(this::quoteRegexLiteral)
                .collect(Collectors.joining("[/\\\\]"));
        if (segments.isEmpty()) {
            return ".*";
        }
        return ".*[/\\\\]" + segments + "[/\\\\]";
    }

    private String quoteRegexLiteral(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isRegexMetaCharacter(ch)) {
                builder.append('\\');
            }
            builder.append(ch);
        }
        return builder.toString();
    }

    private boolean isRegexMetaCharacter(char ch) {
        return "\\.[]{}()+-*?^$|".indexOf(ch) >= 0;
    }
}
