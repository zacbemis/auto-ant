package com.gei.autoant.generate;

import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.util.AntCommand;
import com.gei.autoant.util.JsonUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class VsCodeSettingsWriter {
    private static final String FRONTEND_EXTENSIONS = "jsp|jspf|html|htm|css|js|ts|png|jpg|jpeg|gif|svg|webp|ico|woff|woff2";
    private static final String WEB_INF_VIEW_EXTENSIONS = FRONTEND_EXTENSIONS + "|tag|tagx|tld";

    public String write(ProjectModel model) {
        String frontendPattern = publicFilesUnderWebRootPattern(ModelValues.relativePath(model, ModelValues.webRoot(model)));
        String webInfViewPattern = filesUnderPattern(ModelValues.relativePath(model, ModelValues.webInf(model)), "\\.(" + WEB_INF_VIEW_EXTENSIONS + ")$");
        String configPattern = ".*(WEB-INF[/\\\\]web\\.xml|context\\.xml|\\.(properties|xml|jar))$";

        return "{\n"
                + "  \"filewatcher.isSyncRunEvents\": true,\n"
                + "  \"filewatcher.autoClearConsole\": false,\n"
                + "  \"filewatcher.commands\": [\n"
                + command(frontendPattern, AntCommand.target(model.projectRoot(), "sync-web")) + ",\n"
                + command(webInfViewPattern, AntCommand.target(model.projectRoot(), "sync-web-inf")) + ",\n"
                + command(configPattern, AntCommand.target(model.projectRoot(), "deploy-exploded") + " && auto-ant reload") + "\n"
                + "  ]\n"
                + "}\n";
    }

    private String command(String match, String command) {
        return "    {\n"
                + "      \"match\": " + JsonUtils.quote(match) + ",\n"
                + "      \"event\": \"onFileChange\",\n"
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