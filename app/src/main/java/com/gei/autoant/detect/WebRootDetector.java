package com.gei.autoant.detect;

import com.gei.autoant.model.DetectionResult;
import com.gei.autoant.model.WebRoot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WebRootDetector {
    private static final List<String> CANDIDATES = List.of(
            "src/main/webapp",
            "web",
            "WebContent",
            "WebRoot",
            "public"
    );

    public DetectionResult<WebRoot> detect(Path projectRoot) {
        List<WebRoot> matches = new ArrayList<>();
        for (String candidate : CANDIDATES) {
            Path path = projectRoot.resolve(candidate).normalize();
            if (Files.isDirectory(path)) {
                matches.add(score(path));
            }
        }

        if (matches.isEmpty()) {
            return DetectionResult.notDetected("--web", List.of("No web root candidate was found."));
        }

        matches.sort(Comparator.comparingInt(WebRoot::score).reversed());
        WebRoot best = matches.get(0);
        List<WebRoot> sameScore = matches.stream().filter(root -> root.score() == best.score()).toList();

        if (sameScore.size() > 1) {
            return DetectionResult.warning(best, "--web", "Multiple possible web roots were detected with the same score.");
        }
        if (!best.webXmlPresent()) {
            return DetectionResult.warning(best, "--web", "Web root was detected, but WEB-INF/web.xml was not found.");
        }
        return DetectionResult.confident(best, "--web");
    }

    public WebRoot fromOverride(Path webRoot, Path webInf) {
        Path normalizedWebRoot = webRoot.normalize();
        Path normalizedWebInf = webInf == null ? normalizedWebRoot.resolve("WEB-INF").normalize() : webInf.normalize();
        boolean webXmlPresent = Files.isRegularFile(normalizedWebInf.resolve("web.xml"));
        return new WebRoot(normalizedWebRoot, normalizedWebInf, webXmlPresent, scoreValue(normalizedWebRoot, normalizedWebInf));
    }

    private WebRoot score(Path path) {
        Path webInf = path.resolve("WEB-INF").normalize();
        boolean webXmlPresent = Files.isRegularFile(webInf.resolve("web.xml"));
        return new WebRoot(path, webInf, webXmlPresent, scoreValue(path, webInf));
    }

    private int scoreValue(Path webRoot, Path webInf) {
        int score = 1;
        if (Files.isDirectory(webInf)) {
            score += 5;
        }
        if (Files.isRegularFile(webInf.resolve("web.xml"))) {
            score += 10;
        }
        if (Files.isRegularFile(webRoot.resolve("index.jsp"))) {
            score += 3;
        }
        if (Files.isRegularFile(webRoot.resolve("index.html"))) {
            score += 2;
        }
        return score;
    }
}