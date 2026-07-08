package com.gei.autoant.generate;

import com.gei.autoant.model.ProjectModel;

public final class PropertiesWriter {
    public String writeShared(ProjectModel model) {
        String appName = ModelValues.appName(model);
        String contextDeployName = ModelValues.contextDeployName(model);
        String webDir = ModelValues.relativePath(model, ModelValues.webRoot(model));
        String webInfDir = ModelValues.relativePath(model, ModelValues.webInf(model));
        String srcDirs = ModelValues.commaPaths(model, ModelValues.sourceRoots(model));
        String libDirs = ModelValues.commaPaths(model, ModelValues.libraryRoots(model));

        return "# AUTO-ANT PROJECT CONFIGURATION\n"
                + "# Shared project settings used by auto-ant.build.xml.\n"
                + "# auto-ant update may add new missing keys but should not overwrite existing values.\n"
                + "# Commit this file if your team wants shared auto-ant project settings.\n"
                + "\n"
                + "app.name=" + appName + "\n"
                + "context.path=" + ModelValues.contextPath(model) + "\n"
                + "context.deploy.name=" + contextDeployName + "\n"
                + "context.descriptor.file.name=" + ModelValues.contextDescriptorFileName(model) + "\n"
                + "java.release=" + ModelValues.javaRelease(model) + "\n"
                + "\n"
                + "src.dirs=" + srcDirs + "\n"
                + "web.dir=" + webDir + "\n"
                + "webinf.dir=" + webInfDir + "\n"
                + "lib.dirs=" + libDirs + "\n"
                + "\n"
                + "build.dir=build\n"
                + "build.web.dir=build/web\n"
                + "classes.dir=build/web/WEB-INF/classes\n"
                + "dist.dir=dist\n"
                + "war.name=" + contextDeployName + ".war\n"
                + "\n"
                + "reload.strategy=" + ModelValues.reloadStrategy(model) + "\n";
    }

    public String writeLocal(ProjectModel model) {
        String contextDeployName = ModelValues.contextDeployName(model);
        String tomcatHome = ModelValues.tomcatHome(model);
        String deployDir = tomcatHome.isBlank() ? "" : tomcatHome + "/webapps/" + contextDeployName;
        String contextDescriptorDir = tomcatHome.isBlank() ? "" : tomcatHome + "/conf/Catalina/localhost";
        String deployDirLine = deployDir.isBlank()
                ? "# deploy.dir=\n"
                : "deploy.dir=" + deployDir + "\n";
        String contextDescriptorDirLine = contextDescriptorDir.isBlank()
                ? "# context.descriptor.dir=\n"
                : "context.descriptor.dir=" + contextDescriptorDir + "\n";
        return "# AUTO-ANT LOCAL USER CONFIGURATION\n"
                + "# Local machine settings. Do not commit this file.\n"
                + "# auto-ant may add new missing keys during update, but should not overwrite your values.\n"
                + "\n"
                + "tomcat.home=" + tomcatHome + "\n"
                + "catalina.base=" + tomcatHome + "\n"
                + "# Exploded webapp directory used by sync-web, compile-hot, and touch-webxml reload.\n"
                + "# Defaults to ${catalina.base}/webapps/${context.deploy.name} when not set here.\n"
                + deployDirLine
                + "# Tomcat context descriptor directory. The generated Ant deploy-exploded target writes\n"
                + "# ${context.descriptor.file.name} here so Tomcat can use the configured context path.\n"
                + "# Defaults to ${catalina.base}/conf/Catalina/localhost when not set here.\n"
                + contextDescriptorDirLine
                + "# Leave unset when deploy.dir is inside ${catalina.base}/webapps. Tomcat will use\n"
                + "# ${context.deploy.name} as the appBase folder name. Set this only when deploy.dir/docBase\n"
                + "# points outside Tomcat webapps, for example C:/dev/git/MyApp/build/web.\n"
                + "# context.descriptor.docBase=\n"
                + "\n"
                + "tomcat.manager.url=" + ModelValues.managerUrl(model) + "\n"
                + "tomcat.manager.user=\n"
                + "tomcat.manager.password=\n";
    }
}