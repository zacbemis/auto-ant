package com.gei.autoant.generate;

import com.gei.autoant.model.ProjectModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class InitGenerator {
    public static final String AUTO_ANT_BUILD_FILE = "auto-ant.build.xml";
    public static final String AUTO_ANT_USER_BUILD_FILE = "auto-ant.user.xml";

    private final Path projectRoot;

    public InitGenerator(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    public GenerationResult generate(ProjectModel model) throws IOException {
        List<GeneratedFile> files = new ArrayList<>();
        PropertiesWriter propertiesWriter = new PropertiesWriter();
        Path buildFile = buildFile();

        files.add(writeGenerated(buildFile, new BuildXmlWriter().write(model)));
        files.add(writeIfMissing(userBuildFile(), new UserBuildXmlWriter().write()));
        files.add(writeSafely(projectRoot.resolve("auto-ant.properties"), projectRoot.resolve("auto-ant.properties.auto-ant-new"), propertiesWriter.writeShared(model)));
        files.add(writeSafely(projectRoot.resolve("auto-ant.local.properties"), projectRoot.resolve("auto-ant.local.properties.auto-ant-new"), propertiesWriter.writeLocal(model)));

        files.addAll(writeVsCodeFiles(model, buildFile));
        files.add(new GitignoreWriter().update(projectRoot.resolve(".gitignore")));

        return new GenerationResult(buildFile, files);
    }

    public GenerationResult generateVsCode(ProjectModel model) throws IOException {
        Path buildFile = buildFile();
        return new GenerationResult(buildFile, writeVsCodeFiles(model, buildFile));
    }

    private List<GeneratedFile> writeVsCodeFiles(ProjectModel model, Path buildFile) throws IOException {
        List<GeneratedFile> files = new ArrayList<>();
        Path vscodeDir = projectRoot.resolve(".vscode");
        Files.createDirectories(vscodeDir);
        files.add(writeSafely(vscodeDir.resolve("tasks.json"), vscodeDir.resolve("tasks.auto-ant-new.json"), new VsCodeTasksWriter().write(buildFile)));
        files.add(writeOrMergeSettings(vscodeDir.resolve("settings.json"), new VsCodeSettingsWriter().write(model, buildFile)));
        return files;
    }

    private Path buildFile() {
        return projectRoot.resolve(AUTO_ANT_BUILD_FILE);
    }

    private Path userBuildFile() {
        return projectRoot.resolve(AUTO_ANT_USER_BUILD_FILE);
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

    private GeneratedFile writeOrMergeSettings(Path target, String generatedSettings) throws IOException {
        if (Files.notExists(target)) {
            return writeSafely(target, target.resolveSibling("settings.auto-ant-new.json"), generatedSettings);
        }

        String existing = Files.readString(target, StandardCharsets.UTF_8);
        String merged = new VsCodeSettingsMerger().merge(existing, generatedSettings);
        if (merged == null) {
            return writeSafely(target, target.resolveSibling("settings.auto-ant-new.json"), generatedSettings);
        }
        if (existing.equals(merged)) {
            return new GeneratedFile(target, WriteStatus.UNCHANGED,
                    projectRoot.relativize(target).toString().replace('\\', '/') + " is up to date.");
        }

        Files.writeString(target, merged, StandardCharsets.UTF_8);
        return new GeneratedFile(target, WriteStatus.UPDATED,
                "Updated " + projectRoot.relativize(target).toString().replace('\\', '/') + ".");
    }

    private GeneratedFile writeGenerated(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent() == null ? projectRoot : target.getParent());
        boolean existed = Files.exists(target);
        if (existed && Files.readString(target, StandardCharsets.UTF_8).equals(content)) {
            return new GeneratedFile(target, WriteStatus.UNCHANGED,
                    projectRoot.relativize(target).toString().replace('\\', '/') + " is up to date.");
        }
        Files.writeString(target, content, StandardCharsets.UTF_8);
        if (existed) {
            return new GeneratedFile(target, WriteStatus.UPDATED,
                    "Updated " + projectRoot.relativize(target).toString().replace('\\', '/') + ".");
        }
        return new GeneratedFile(target, WriteStatus.CREATED,
                "Created " + projectRoot.relativize(target).toString().replace('\\', '/') + ".");
    }

    private GeneratedFile writeIfMissing(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent() == null ? projectRoot : target.getParent());
        String relativePath = projectRoot.relativize(target).toString().replace('\\', '/');
        if (Files.exists(target)) {
            return new GeneratedFile(target, WriteStatus.UNCHANGED,
                    relativePath + " already exists; leaving user customizations untouched.");
        }
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return new GeneratedFile(target, WriteStatus.CREATED,
                "Created " + relativePath + " for user Ant customizations.");
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