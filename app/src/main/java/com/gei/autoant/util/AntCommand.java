package com.gei.autoant.util;

import java.nio.file.Path;

public final class AntCommand {
    public static final String DEFAULT_LOGGER_CLASS = "org.apache.tools.ant.DefaultLogger";

    private AntCommand() {
    }

    public static String target(String target) {
        return "ant -logger " + DEFAULT_LOGGER_CLASS + " " + target;
    }

    public static String target(Path projectRoot, String target) {
        Path buildFile = projectRoot.toAbsolutePath().normalize().resolve("build.xml");
        return targetBuildFile(buildFile, target);
    }

    public static String targetBuildFile(Path buildFile, String target) {
        return "ant -logger " + DEFAULT_LOGGER_CLASS + " -f " + quote(buildFile.toAbsolutePath().normalize()) + " " + target;
    }

    private static String quote(Path path) {
        return "\"" + PathUtils.toPortableString(path).replace("\"", "\\\"") + "\"";
    }
}
