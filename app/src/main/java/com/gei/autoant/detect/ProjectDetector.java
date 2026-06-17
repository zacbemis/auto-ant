package com.gei.autoant.detect;

import com.gei.autoant.model.DetectionResult;
import com.gei.autoant.model.LibraryRoot;
import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.model.ReloadStrategy;
import com.gei.autoant.model.ServletNamespace;
import com.gei.autoant.model.SourceRoot;
import com.gei.autoant.model.WebRoot;
import com.gei.autoant.prompt.NonInteractiveOptions;

import java.nio.file.Path;
import java.util.List;

public final class ProjectDetector {
    public ProjectModel detect(Path projectRoot, NonInteractiveOptions options) {
        Path root = projectRoot.toAbsolutePath().normalize();

        DetectionResult<String> appName = options.appName()
                .map(value -> DetectionResult.overridden(value, "--app"))
                .orElseGet(() -> DetectionResult.confident(inferAppName(root), "--app"));

        DetectionResult<String> contextPath = options.contextPath()
                .map(value -> DetectionResult.overridden(value, "--context"))
                .orElseGet(() -> DetectionResult.confident("/" + appName.value().orElse(inferAppName(root)), "--context"));

        DetectionResult<List<SourceRoot>> sourceRoots = options.sourceRoots().isEmpty()
                ? new SourceRootDetector().detect(root)
                : DetectionResult.overridden(options.sourceRoots().stream().map(SourceRoot::new).toList(), "--src");

        WebRootDetector webRootDetector = new WebRootDetector();
        DetectionResult<WebRoot> webRoot = options.webRoot()
                .map(value -> DetectionResult.overridden(webRootDetector.fromOverride(value, options.webInf().orElse(null)), "--web"))
                .orElseGet(() -> webRootDetector.detect(root));

        if (options.webRoot().isEmpty() && options.webInf().isPresent() && webRoot.value().isPresent()) {
            WebRoot detected = webRoot.value().get();
            webRoot = DetectionResult.overridden(webRootDetector.fromOverride(detected.path(), options.webInf().get()), "--webinf");
        }

        DetectionResult<List<LibraryRoot>> libraryRoots = options.libraryRoots().isEmpty()
                ? new LibraryDetector().detect(root, webRoot)
                : DetectionResult.overridden(options.libraryRoots().stream().map(LibraryRoot::new).toList(), "--lib");

        DetectionResult<ServletNamespace> servletNamespace = new ServletNamespaceDetector().detect(sourceRoots.value().orElse(List.of()));
        DetectionResult<String> recommendedTomcat = recommendedTomcat(servletNamespace);

        DetectionResult<Path> tomcatHome = options.tomcatHome()
                .map(value -> DetectionResult.overridden(value, "--tomcat"))
                .orElseGet(() -> new TomcatDetector().detect());

        DetectionResult<Path> antExecutable = options.antExecutable()
                .map(value -> DetectionResult.overridden(value, "--ant"))
                .orElseGet(() -> new AntDetector().detect());

        DetectionResult<Integer> javaRelease = options.javaRelease()
                .map(value -> DetectionResult.overridden(value, "--java"))
                .orElseGet(() -> new JavaDetector().detect());

        DetectionResult<ReloadStrategy> reloadStrategy = options.reloadStrategy()
                .map(value -> DetectionResult.overridden(value, "--reload-strategy"))
                .orElseGet(() -> DetectionResult.confident(ReloadStrategy.MANAGER, "--reload-strategy"));

        DetectionResult<String> tomcatManagerUrl = options.tomcatManagerUrl()
                .map(value -> DetectionResult.overridden(value, "--tomcat-manager-url"))
                .orElseGet(() -> DetectionResult.confident("http://localhost:8080/manager/text", "--tomcat-manager-url"));

        List<String> warnings = ProjectModel.collectWarnings(
                sourceRoots,
                webRoot,
                libraryRoots,
                servletNamespace,
                recommendedTomcat,
                tomcatHome,
                antExecutable,
                javaRelease
        );

        return new ProjectModel(
                root,
                appName,
                contextPath,
                sourceRoots,
                webRoot,
                libraryRoots,
                servletNamespace,
                recommendedTomcat,
                tomcatHome,
                antExecutable,
                javaRelease,
                reloadStrategy,
                tomcatManagerUrl,
                warnings
        );
    }

    private String inferAppName(Path root) {
        Path fileName = root.getFileName();
        return fileName == null ? "app" : fileName.toString();
    }

    private DetectionResult<String> recommendedTomcat(DetectionResult<ServletNamespace> namespace) {
        if (namespace.value().isEmpty()) {
            return DetectionResult.notDetected(null, List.of("Servlet namespace is unknown, so Tomcat major version cannot be recommended."));
        }
        return switch (namespace.value().get()) {
            case JAVAX -> DetectionResult.confident("Tomcat 9.x", null);
            case JAKARTA -> DetectionResult.confident("Tomcat 10/11", null);
            case BOTH -> DetectionResult.warning("Tomcat version unclear", null, "Mixed servlet namespaces make Tomcat recommendation ambiguous.");
            case UNKNOWN -> DetectionResult.notDetected(null);
        };
    }
}