package com.gei.autoant.generate;

import com.gei.autoant.model.ProjectModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class UpdateGenerator {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path projectRoot;

    public UpdateGenerator(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    public GenerationResult update(ProjectModel model) throws IOException {
        List<GeneratedFile> files = new ArrayList<>();
        PropertiesWriter propertiesWriter = new PropertiesWriter();
        Path buildFile = projectRoot.resolve(InitGenerator.AUTO_ANT_BUILD_FILE);

        files.add(writeGeneratedWithBackup(buildFile, new BuildXmlWriter().write(model)));
        files.add(writeIfMissing(projectRoot.resolve(InitGenerator.AUTO_ANT_USER_BUILD_FILE), new UserBuildXmlWriter().write()));
        files.add(new PropertiesMerger().mergeMissing(projectRoot.resolve("auto-ant.properties"), propertiesWriter.writeShared(model), projectRoot));
        files.add(new PropertiesMerger().mergeMissing(projectRoot.resolve("auto-ant.local.properties"), propertiesWriter.writeLocal(model), projectRoot));
        files.addAll(updateVsCodeFiles(model, buildFile));
        files.add(new GitignoreWriter().update(projectRoot.resolve(".gitignore")));

        return new GenerationResult(buildFile, files);
    }

    private List<GeneratedFile> updateVsCodeFiles(ProjectModel model, Path buildFile) throws IOException {
        List<GeneratedFile> files = new ArrayList<>();
        Path vscodeDir = projectRoot.resolve(".vscode");
        Files.createDirectories(vscodeDir);
        files.add(writeOrMergeTasks(vscodeDir.resolve("tasks.json"), new VsCodeTasksWriter().write(buildFile)));
        files.add(writeOrMergeSettings(vscodeDir.resolve("settings.json"), new VsCodeSettingsWriter().write(model, buildFile)));
        return files;
    }

    private GeneratedFile writeGeneratedWithBackup(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent() == null ? projectRoot : target.getParent());
        String relativePath = relative(target);
        if (Files.notExists(target)) {
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return new GeneratedFile(target, WriteStatus.CREATED, "Created " + relativePath + ".");
        }
        String existing = Files.readString(target, StandardCharsets.UTF_8);
        if (existing.equals(content)) {
            return new GeneratedFile(target, WriteStatus.UNCHANGED, relativePath + " is up to date.");
        }
        Path backup = backupPath(target);
        Files.copy(target, backup);
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return new GeneratedFile(target, WriteStatus.UPDATED,
                "Updated " + relativePath + "; backup saved to " + relative(backup) + ".");
    }

    private GeneratedFile writeIfMissing(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent() == null ? projectRoot : target.getParent());
        String relativePath = relative(target);
        if (Files.exists(target)) {
            return new GeneratedFile(target, WriteStatus.UNCHANGED,
                    relativePath + " already exists; leaving user customizations untouched.");
        }
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return new GeneratedFile(target, WriteStatus.CREATED,
                "Created " + relativePath + " for user Ant customizations.");
    }

    private GeneratedFile writeOrMergeTasks(Path target, String generatedTasks) throws IOException {
        if (Files.notExists(target)) {
            Files.writeString(target, generatedTasks, StandardCharsets.UTF_8);
            return new GeneratedFile(target, WriteStatus.CREATED, "Created " + relative(target) + ".");
        }
        String existing = Files.readString(target, StandardCharsets.UTF_8);
        String merged = new VsCodeTasksMerger().merge(existing, generatedTasks);
        if (merged == null) {
            return writeAlternate(target.resolveSibling("tasks.auto-ant-new.json"), generatedTasks,
                    "Existing " + relative(target) + " could not be merged");
        }
        if (existing.equals(merged)) {
            return new GeneratedFile(target, WriteStatus.UNCHANGED, relative(target) + " is up to date.");
        }
        Files.writeString(target, merged, StandardCharsets.UTF_8);
        return new GeneratedFile(target, WriteStatus.UPDATED,
                "Updated " + relative(target) + ": refreshed auto-ant tasks and preserved user tasks.");
    }

    private GeneratedFile writeOrMergeSettings(Path target, String generatedSettings) throws IOException {
        if (Files.notExists(target)) {
            Files.writeString(target, generatedSettings, StandardCharsets.UTF_8);
            return new GeneratedFile(target, WriteStatus.CREATED, "Created " + relative(target) + ".");
        }
        String existing = Files.readString(target, StandardCharsets.UTF_8);
        String merged = new VsCodeSettingsMerger().merge(existing, generatedSettings);
        if (merged == null) {
            return writeAlternate(target.resolveSibling("settings.auto-ant-new.json"), generatedSettings,
                    "Existing " + relative(target) + " could not be merged");
        }
        if (existing.equals(merged)) {
            return new GeneratedFile(target, WriteStatus.UNCHANGED, relative(target) + " is up to date.");
        }
        Files.writeString(target, merged, StandardCharsets.UTF_8);
        return new GeneratedFile(target, WriteStatus.UPDATED,
                "Updated " + relative(target) + ": refreshed auto-ant settings and preserved unrelated settings.");
    }

    private GeneratedFile writeAlternate(Path alternate, String content, String reason) throws IOException {
        Path target = nextAvailableAlternate(alternate);
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return new GeneratedFile(target, WriteStatus.CREATED_ALTERNATE,
                reason + "; wrote " + relative(target) + " instead.");
    }

    private Path backupPath(Path target) {
        String backupName = target.getFileName() + ".auto-ant-backup-" + LocalDateTime.now().format(BACKUP_TIMESTAMP);
        return nextAvailableAlternate(target.resolveSibling(backupName));
    }

    private Path nextAvailableAlternate(Path alternate) {
        if (Files.notExists(alternate)) {
            return alternate;
        }
        Path parent = alternate.getParent();
        String fileName = alternate.getFileName().toString();
        int counter = 2;
        while (true) {
            Path candidate = parent.resolve(fileName + "." + counter);
            if (Files.notExists(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    private String relative(Path path) {
        return projectRoot.relativize(path).toString().replace('\\', '/');
    }
}