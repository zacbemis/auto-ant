package com.gei.autoant.watch;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileClassifierTest {
    private final FileClassifier classifier = new FileClassifier();

    @Test
    void classifiesFrontendFiles() {
        assertEquals(ChangeKind.FRONTEND, classifier.classify(Path.of("web/index.jsp")));
        assertEquals(ChangeKind.FRONTEND, classifier.classify(Path.of("web/assets/app.CSS")));
        assertEquals(ChangeKind.FRONTEND, classifier.classify(Path.of("web/assets/logo.svg")));
    }

    @Test
    void classifiesBackendFiles() {
        assertEquals(ChangeKind.BACKEND, classifier.classify(Path.of("src/com/example/UserServlet.java")));
    }

    @Test
    void classifiesConfigAndLibraryFiles() {
        assertEquals(ChangeKind.CONFIG, classifier.classify(Path.of("web/WEB-INF/web.xml")));
        assertEquals(ChangeKind.CONFIG, classifier.classify(Path.of("web/META-INF/context.xml")));
        assertEquals(ChangeKind.CONFIG, classifier.classify(Path.of("web/WEB-INF/lib/example.jar")));
        assertEquals(ChangeKind.CONFIG, classifier.classify(Path.of("src/conf/app.properties")));
    }

    @Test
    void ignoresUnknownFiles() {
        assertEquals(ChangeKind.IGNORED, classifier.classify(Path.of("README.md")));
    }
}