package com.gei.autoant.watch;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public final class ChangeBatch {
    private final List<Path> paths;

    public ChangeBatch(Collection<Path> paths) {
        Objects.requireNonNull(paths, "paths");
        this.paths = List.copyOf(new LinkedHashSet<>(paths));
    }

    public static ChangeBatch of(Path... paths) {
        List<Path> values = new ArrayList<>();
        for (Path path : paths) {
            values.add(path);
        }
        return new ChangeBatch(values);
    }

    public List<Path> paths() {
        return paths;
    }

    public boolean isEmpty() {
        return paths.isEmpty();
    }

    public ChangeKind classify(FileClassifier classifier) {
        Objects.requireNonNull(classifier, "classifier");
        boolean hasFrontend = false;
        boolean hasBackend = false;
        boolean hasConfig = false;

        for (Path path : paths) {
            ChangeKind kind = classifier.classify(path);
            if (kind == ChangeKind.CONFIG) {
                hasConfig = true;
            } else if (kind == ChangeKind.BACKEND) {
                hasBackend = true;
            } else if (kind == ChangeKind.FRONTEND) {
                hasFrontend = true;
            }
        }

        if (hasConfig) {
            return ChangeKind.CONFIG;
        }
        if (hasBackend) {
            return ChangeKind.BACKEND;
        }
        if (hasFrontend) {
            return ChangeKind.FRONTEND;
        }
        return ChangeKind.IGNORED;
    }
}