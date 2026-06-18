package com.gei.autoant.generate;

public final class VsCodeTasksWriter {
    public String write() {
        return "{\n"
                + "  \"version\": \"2.0.0\",\n"
                + "  \"tasks\": [\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: clean build\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": \"ant clean-build\",\n"
                + "      \"group\": \"build\",\n"
                + "      \"problemMatcher\": \"$javac\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: deploy exploded\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": \"ant deploy-exploded\",\n"
                + "      \"problemMatcher\": \"$javac\"\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: sync web\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": \"ant sync-web\",\n"
                + "      \"problemMatcher\": []\n"
                + "    },\n"
                + "    {\n"
                + "      \"label\": \"auto-ant: compile hot\",\n"
                + "      \"type\": \"shell\",\n"
                + "      \"command\": \"ant compile-hot\",\n"
                + "      \"problemMatcher\": \"$javac\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n";
    }
}