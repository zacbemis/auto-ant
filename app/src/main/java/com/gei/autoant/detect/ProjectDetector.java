package com.gei.autoant.detect;

import com.gei.autoant.model.DetectionResult;
import com.gei.autoant.model.LibraryRoot;
import com.gei.autoant.model.ProjectModel;
import com.gei.autoant.model.ReloadStrategy;
import com.gei.autoant.model.ServletNamespace;
import com.gei.autoant.model.SourceRoot;
import com.gei.autoant.model.WebRoot;
import com.gei.autoant.prompt.NonInteractiveOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProjectDetector {
    public ProjectModel detect(Path projectRoot, NonInteractiveOptions options) {
        Path requestedRoot = projectRoot.toAbsolutePath().normalize();
        RootSelection rootSelection = selectProjectRoot(requestedRoot);
        Path root = rootSelection.root();

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
                .orElseGet(() -> DetectionResult.userRequired("--tomcat", "Tomcat home must be selected explicitly. Rerun with --tomcat <path> or use --interactive."));

        DetectionResult<Path> antExecutable = options.antExecutable()
                .map(value -> DetectionResult.overridden(value, "--ant"))
                .orElseGet(() -> new AntDetector().detect());

        DetectionResult<Integer> javaRelease = options.javaRelease()
                .map(value -> DetectionResult.overridden(value, "--java"))
                .orElseGet(() -> DetectionResult.userRequired("--java", "Java release must be selected explicitly. For Java 1.8, use --java 8."));

        DetectionResult<ReloadStrategy> reloadStrategy = options.reloadStrategy()
                .map(value -> DetectionResult.overridden(value, "--reload-strategy"))
                .orElseGet(() -> DetectionResult.confident(ReloadStrategy.MANAGER, "--reload-strategy"));

        DetectionResult<String> tomcatManagerUrl = options.tomcatManagerUrl()
                .map(value -> DetectionResult.overridden(value, "--tomcat-manager-url"))
                .orElseGet(() -> DetectionResult.confident("http://localhost:8080/manager/text", "--tomcat-manager-url"));

        DetectionResult<Path> projectRootResult = rootSelection.result();

        List<String> warnings = ProjectModel.collectWarnings(
                projectRootResult,
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
                projectRootResult,
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

    private RootSelection selectProjectRoot(Path requestedRoot) {
        if (!Files.isDirectory(requestedRoot)) {
            return new RootSelection(
                    requestedRoot,
                    DetectionResult.userRequired(requestedRoot, "--root", "Project root folder does not exist. Choose the folder that contains the web app source root and web root.")
            );
        }

        DetectionResult<List<SourceRoot>> sourceRoots = new SourceRootDetector().detect(requestedRoot);
        DetectionResult<WebRoot> webRoot = new WebRootDetector().detect(requestedRoot);
        boolean sourceDetected = sourceRoots.value().isPresent() && !sourceRoots.value().get().isEmpty();
        boolean webDetected = webRoot.value().isPresent();
        if (sourceDetected && webDetected) {
            return new RootSelection(requestedRoot, DetectionResult.confident(requestedRoot, "--root"));
        }

        List<Path> childAppRoots = childAppRoots(requestedRoot);
        List<String> warnings = new ArrayList<>();
        if (childAppRoots.size() == 1) {
            Path childRoot = childAppRoots.get(0);
            warnings.add("Selected folder appears to be a parent repository, not the web app root.");
            warnings.add("Using child web app root: " + childRoot.getFileName());
            warnings.add("If this is not correct, rerun with --root <path> or use --interactive.");
            return new RootSelection(childRoot, DetectionResult.warning(childRoot, "--root", warnings));
        }

        if (!sourceDetected && !webDetected) {
            warnings.add("No recognizable legacy Java web app layout was found at this folder.");
            if (!childAppRoots.isEmpty()) {
                warnings.add("Possible app roots below this folder: " + childAppRoots.stream().map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.joining(", ")));
            }
            warnings.add("Choose the folder that directly contains source and web directories, for example FEMSWeb rather than its parent repository folder.");
            return new RootSelection(requestedRoot, DetectionResult.userRequired(requestedRoot, "--root", warnings));
        }

        warnings.add("Project root looks incomplete: " + (sourceDetected ? "source root detected" : "source root missing")
                + ", " + (webDetected ? "web root detected" : "web root missing") + ".");
        return new RootSelection(requestedRoot, DetectionResult.warning(requestedRoot, "--root", warnings));
    }

    private List<Path> childAppRoots(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var children = Files.list(root)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(child -> new SourceRootDetector().detect(child).value().isPresent()
                            && new WebRootDetector().detect(child).value().isPresent())
                    .limit(10)
                    .toList();
        } catch (java.io.IOException ex) {
            return List.of();
        }
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

    private record RootSelection(Path root, DetectionResult<Path> result) {
    }
}