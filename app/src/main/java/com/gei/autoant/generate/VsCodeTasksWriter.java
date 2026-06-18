package com.gei.autoant.generate;

import com.gei.autoant.util.AntCommand;

public final class VsCodeTasksWriter {
    public String write() {
        return "{\n"
                + "  \"version\": \"2.0.0\",\n"
                + "  \"tasks\": [\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: clean build\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": \"" + AntCommand.target("clean-build") + "\",\n"
                + "      \"group\": \"build\",\n"
                + "      \"problemMatcher\": \"$javac\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: deploy exploded\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": \"" + AntCommand.target("deploy-exploded") + "\",\n"
                + "      \"problemMatcher\": \"$javac\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: sync web\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": \"" + AntCommand.target("sync-web") + "\",\n"
                + "      \"problemMatcher\": []\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: compile hot\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": \"" + AntCommand.target("compile-hot") + "\",\n"
                + "      \"problemMatcher\": \"$javac\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }
}