package com.gei.autoant.generate;

import com.gei.autoant.util.AntCommand;
import com.gei.autoant.util.JsonUtils;

import java.nio.file.Path;

public final class VsCodeTasksWriter {
    static final String GENERATED_HEADER = "// AUTO-ANT MANAGED TASKS - DO NOT EDIT AUTO-ANT TASKS DIRECTLY.\n"
            + "// This file may be updated by auto-ant init or auto-ant update.\n"
            + "// Add custom tasks with labels that do not start with \"auto-ant:\".\n";

    public String write() {
        return write(null);
    }

    public String write(Path buildFile) {
        String reconcile = "auto-ant reconcile --root \"${workspaceFolder}\"";
        return GENERATED_HEADER
                + "{\n"
                + "  \"version\": \"2.0.0\",\n"
                + "  \"tasks\": [\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: clean build\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": " + JsonUtils.quote(command(buildFile, "clean-build")) + ",\n"
                + "      \"group\": \"build\",\n"
                + "      \"problemMatcher\": \"$javac\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: reconcile deployment\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": " + JsonUtils.quote(reconcile) + ",\n"
                + "      \"problemMatcher\": \"$javac\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: develop frontend\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": " + JsonUtils.quote(develop("frontend")) + ",\n"
                + "      \"problemMatcher\": []\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: develop views\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": " + JsonUtils.quote(develop("views")) + ",\n"
                + "      \"problemMatcher\": []\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: develop classes\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": " + JsonUtils.quote(develop("classes")) + ",\n"
                + "      \"problemMatcher\": \"$javac\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: develop config\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": " + JsonUtils.quote(develop("config")) + ",\n"
                + "      \"problemMatcher\": []\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    private String command(Path buildFile, String target) {
        return buildFile == null ? AntCommand.target(target) : AntCommand.targetBuildFile(buildFile, target);
    }

    private String develop(String kind) {
        return "auto-ant develop --root \"${workspaceFolder}\" --kind " + kind;
    }
}
