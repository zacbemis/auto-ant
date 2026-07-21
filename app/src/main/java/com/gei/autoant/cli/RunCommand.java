package com.gei.autoant.cli;

import com.gei.autoant.generate.InitGenerator;
import com.gei.autoant.run.AntRunner;
import com.gei.autoant.run.CommandResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class RunCommand {
    private final CommandContext context;

    public RunCommand(CommandContext context) {
        this.context = context;
    }

    public int run(String[] args) {
        CommandLine commandLine = CommandLine.parse(args);
        if (commandLine.hasAnyOption("help", "h")) {
            printHelp();
            return 0;
        }
        if (commandLine.positionals().isEmpty()) {
            printHelp();
            return 2;
        }

        List<String> liveMutating = List.of("deploy-exploded", "deploy-war", "branch-refresh", "sync-web", "sync-web-inf", "compile-hot");
        if (commandLine.positionals().stream().anyMatch(liveMutating::contains)) {
            context.err().println("run: direct live-mutating Ant targets are disabled. Use auto-ant reconcile.");
            return 2;
        }

        try {
            CommandResult result = new AntRunner(context.out(), context.err()).runTargets(context.projectRoot(), selectedBuildFile(), commandLine.positionals());
            return result.exitCode();
        } catch (IOException ex) {
            context.err().println("run: failed to execute Ant: " + ex.getMessage());
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            context.err().println("run: interrupted");
            return 130;
        }
    }

    private void printHelp() {
        context.out().println("Usage: auto-ant run <ant-target> [more-targets]");
        context.out().println();
        context.out().println("Examples:");
        context.out().println("  auto-ant run clean-build");
        context.out().println("  auto-ant reconcile");
        printAvailableTargets();
    }

    private void printAvailableTargets() {
        context.out().println();
        Path buildXml = selectedBuildFile();
        String relativeBuildFile = relativeBuildFile(buildXml);
        if (Files.notExists(buildXml)) {
            context.out().println("Available Ant targets: " + relativeBuildFile + " not found. Run auto-ant init first.");
            return;
        }

        List<AntTarget> targets;
        try {
            targets = readTargets(buildXml);
        } catch (Exception ex) {
            context.out().println("Available Ant targets: unable to read " + relativeBuildFile + ": " + ex.getMessage());
            return;
        }

        if (targets.isEmpty()) {
            context.out().println("Available Ant targets: none found in " + relativeBuildFile + ".");
            return;
        }

        context.out().println("Available Ant targets from " + relativeBuildFile + ":");
        for (AntTarget target : targets) {
            if (target.description().isBlank()) {
                context.out().println("  " + target.name());
            } else {
                context.out().println("  " + target.name() + " - " + target.description());
            }
        }
    }

    private Path selectedBuildFile() {
        return context.projectRoot().resolve(InitGenerator.AUTO_ANT_BUILD_FILE);
    }

    private String relativeBuildFile(Path buildFile) {
        return context.projectRoot().relativize(buildFile).toString().replace('\\', '/');
    }

    private List<AntTarget> readTargets(Path buildXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        NodeList nodes = factory.newDocumentBuilder().parse(buildXml.toFile()).getElementsByTagName("target");
        List<AntTarget> targets = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = element.getAttribute("name").trim();
            if (!name.isBlank()) {
                targets.add(new AntTarget(name, element.getAttribute("description").trim()));
            }
        }
        return targets;
    }

    private record AntTarget(String name, String description) {
    }
}
