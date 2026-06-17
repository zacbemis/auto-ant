package com.gei.autoant.cli;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class CommandLine {
    private final Map<String, List<String>> options;
    private final List<String> positionals;

    private CommandLine(Map<String, List<String>> options, List<String> positionals) {
        this.options = options;
        this.positionals = positionals;
    }

    public static CommandLine parse(String[] args) {
        Map<String, List<String>> options = new LinkedHashMap<>();
        List<String> positionals = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--") && arg.length() > 2) {
                String rawName;
                String value;
                int equalsIndex = arg.indexOf('=');
                if (equalsIndex > 2) {
                    rawName = arg.substring(2, equalsIndex);
                    value = arg.substring(equalsIndex + 1);
                } else {
                    rawName = arg.substring(2);
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        value = args[++i];
                    } else {
                        value = "true";
                    }
                }
                options.computeIfAbsent(normalize(rawName), ignored -> new ArrayList<>()).add(value);
            } else if (arg.startsWith("-") && arg.length() > 1) {
                String shortName = arg.substring(1);
                options.computeIfAbsent(normalize(shortName), ignored -> new ArrayList<>()).add("true");
            } else {
                positionals.add(arg);
            }
        }

        return new CommandLine(options, positionals);
    }

    public Optional<String> option(String name) {
        List<String> values = options.get(normalize(name));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.get(values.size() - 1));
    }

    public List<String> options(String name) {
        List<String> values = options.get(normalize(name));
        if (values == null) {
            return List.of();
        }
        return Collections.unmodifiableList(values);
    }

    public boolean hasOption(String name) {
        return options.containsKey(normalize(name));
    }

    public boolean hasAnyOption(String... names) {
        for (String name : names) {
            if (hasOption(name)) {
                return true;
            }
        }
        return false;
    }

    public List<String> positionals() {
        return Collections.unmodifiableList(positionals);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}