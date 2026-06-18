package com.gei.autoant.generate;

import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.util.JsonUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class VsCodeSettingsWriter {
    private static final String FRONTEND_EXTENSIONS = "jsp|jspf|html|htm|css|js|ts|png|jpg|jpeg|gif|svg|webp|ico|woff|woff2";

    public String write(ProjectModel model) {
        String webDirPattern = pathPattern(ModelValues.relativePath(model, ModelValues.webRoot(model)));
        String frontendPattern = webDirPattern + ".*\\.(" + FRONTEND_EXTENSIONS + ")$";

        return "{\n"
                + "  \"filewatcher.isSyncRunEvents\": true,\n"
                + "  \"filewatcher.autoClearConsole\": false,\n"
                + "  \"filewatcher.commands\": [\n"
                + "    {\n"
                + "      \"match\": " + JsonUtils.quote(frontendPattern) + ",\n"
                + "      \"event\": \"onFileChange\",\n"
                + "      \"isAsync\": false,\n"
                + "      \"cmd\": \"ant sync-web\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
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