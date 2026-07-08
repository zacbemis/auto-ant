package com.gei.autoant.generate;

public final class UserBuildXmlWriter {
    public String write() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<project name=\"auto-ant-user-customizations\">\n"
                + "    <!--\n"
                + "      User-owned auto-ant extension file.\n"
                + "\n"
                + "      Put custom Ant properties, paths, targets, or helper macros here.\n"
                + "      auto-ant creates this file if missing, but will not overwrite it once it exists.\n"
                + "    -->\n"
                + "</project>\n";
    }
}