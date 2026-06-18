package com.gei.autoant.watch;

import com.gei.autoant.run.CommandResult;

import java.io.IOException;

@FunctionalInterface
public interface TargetRunner {
    CommandResult run(String target) throws IOException, InterruptedException;
}