package com.gei.autoant.util;

import java.io.PrintStream;

public final class Console {
    private final PrintStream out;
    private final PrintStream err;

    public Console(PrintStream out, PrintStream err) {
        this.out = out;
        this.err = err;
    }

    public void info(String message) {
        out.println(message);
    }

    public void error(String message) {
        err.println(message);
    }
}