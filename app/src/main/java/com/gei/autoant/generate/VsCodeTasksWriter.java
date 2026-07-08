package com.gei.autoant.generate;

import com.gei.autoant.util.AntCommand;
import com.gei.autoant.util.JsonUtils;

import java.nio.file.Path;

public final class VsCodeTasksWriter {
    public String write() {
        return write(null);
    }

    public String write(Path buildFile) {
        return "// AUTO-ANT MANAGED TASKS - DO NOT EDIT AUTO-ANT TASKS DIRECTLY.\n"
                + "// This file may be updated by auto-ant init or auto-ant update.\n"
                + "// Add custom tasks with labels that do not start with \"auto-ant:\".\n"
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
                + "      \"label\": \"auto-ant: deploy exploded\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": " + JsonUtils.quote(command(buildFile, "deploy-exploded")) + ",\n"
                + "      \"problemMatcher\": \"$javac\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: sync web\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": " + JsonUtils.quote(command(buildFile, "sync-web")) + ",\n"
                + "      \"problemMatcher\": []\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: compile hot\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": " + JsonUtils.quote(command(buildFile, "compile-hot")) + ",\n"
                + "      \"problemMatcher\": \"$javac\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }

    private String command(Path buildFile, String target) {
        return buildFile == null ? AntCommand.target(target) : AntCommand.targetBuildFile(buildFile, target);
    }
}