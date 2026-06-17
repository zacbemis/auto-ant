package com.gei.autoant.generate;

import com.gei.autoant.model.ProjectModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class InitGenerator {
    private final Path projectRoot;

    public InitGenerator(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    public GenerationResult generate(ProjectModel model) throws IOException {
        List<GeneratedFile> files = new ArrayList<>();
        PropertiesWriter propertiesWriter = new PropertiesWriter();

        files.add(writeSafely(projectRoot.resolve("build.xml"), projectRoot.resolve("build.xml.auto-ant-new"), new BuildXmlWriter().write(model)));
        files.add(writeSafely(projectRoot.resolve("auto-ant.properties"), projectRoot.resolve("auto-ant.properties.auto-ant-new"), propertiesWriter.writeShared(model)));
        files.add(writeSafely(projectRoot.resolve("auto-ant.local.properties"), projectRoot.resolve("auto-ant.local.properties.auto-ant-new"), propertiesWriter.writeLocal(model)));

        Path vscodeDir = projectRoot.resolve(".vscode");
        Files.createDirectories(vscodeDir);
        files.add(writeSafely(vscodeDir.resolve("tasks.json"), vscodeDir.resolve("tasks.auto-ant-new.json"), new VsCodeTasksWriter().write()));
        files.add(new GitignoreWriter().update(projectRoot.resolve(".gitignore")));

        return new GenerationResult(files);
    }

    private GeneratedFile writeSafely(Path primary, Path alternate, String content) throws IOException {
        Path target = Files.exists(primary) ? nextAvailableAlternate(alternate) : primary;
        Files.createDirectories(target.getParent() == null ? projectRoot : target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        if (target.equals(primary)) {
            return new GeneratedFile(target, WriteStatus.CREATED, "Created " + projectRoot.relativize(target).toString().replace('\\', '/') + ".");
        }
        return new GeneratedFile(target, WriteStatus.CREATED_ALTERNATE,
                "Existing " + projectRoot.relativize(primary).toString().replace('\\', '/')
                        + " found; wrote " + projectRoot.relativize(target).toString().replace('\\', '/') + " instead.");
    }

    private Path nextAvailableAlternate(Path alternate) {
        if (!Files.exists(alternate)) {
            return alternate;
        }
        Path parent = alternate.getParent();
        String fileName = alternate.getFileName().toString();
        int counter = 2;
        while (true) {
            Path candidate = parent.resolve(fileName + "." + counter);
            if (!Files.exists(candidate)) {
                return candidate;
            }
            counter++;
        }
    }
}