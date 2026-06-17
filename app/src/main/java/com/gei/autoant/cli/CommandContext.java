package com.gei.autoant.cli;

import com.gei.autoant.prompt.PromptService;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Objects;

public final class CommandContext {
    private final Path projectRoot;
    private final PrintStream out;
    private final PrintStream err;
    private final PromptService promptService;

    public CommandContext(Path projectRoot, PrintStream out, PrintStream err, PromptService promptService) {
        this.projectRoot = Objects.requireNonNull(projectRoot, "projectRoot");
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
        this.promptService = Objects.requireNonNull(promptService, "promptService");
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public PrintStream out() {
        return out;
    }

    public PrintStream err() {
        return err;
    }

    public PromptService promptService() {
        return promptService;
    }
}