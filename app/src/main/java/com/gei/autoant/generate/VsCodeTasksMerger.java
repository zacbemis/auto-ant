package com.gei.autoant.generate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VsCodeTasksMerger {
    String merge(String existingTasks, String generatedTasks) {
        TasksFile existing = readTasks(stripGeneratedHeader(existingTasks));
        TasksFile generated = readTasks(stripLeadingLineComments(generatedTasks));
        if (existing == null || generated == null) {
            return null;
        }

        Map<String, String> generatedAutoAntTasks = new LinkedHashMap<>();
        for (String task : generated.tasks()) {
            String label = readStringProperty(task, "label");
            if (label != null && label.startsWith("auto-ant:")) {
                generatedAutoAntTasks.put(label, task);
            }
        }

        List<String> mergedTasks = new ArrayList<>();
        for (String task : existing.tasks()) {
            String label = readStringProperty(task, "label");
            if (label != null && label.startsWith("auto-ant:")) {
                if (generatedAutoAntTasks.containsKey(label)) {
                    mergedTasks.add(generatedAutoAntTasks.remove(label));
                }
            } else {
                mergedTasks.add(task);
            }
        }
        mergedTasks.addAll(generatedAutoAntTasks.values());

        StringBuilder builder = new StringBuilder(VsCodeTasksWriter.GENERATED_HEADER).append("{\n");
        builder.append("  \"version\": ").append(existing.version()).append(",\n");
        for (Member member : existing.otherMembers()) {
            builder.append(indent(member.text(), 2)).append(",\n");
        }
        builder.append("  \"tasks\": [\n");
        for (int i = 0; i < mergedTasks.size(); i++) {
            builder.append(indent(mergedTasks.get(i), 4));
            if (i + 1 < mergedTasks.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private TasksFile readTasks(String json) {
        List<Member> members = readMembers(json);
        if (members == null) {
            return null;
        }

        String version = "\"2.0.0\"";
        List<String> tasks = null;
        List<Member> otherMembers = new ArrayList<>();
        for (Member member : members) {
            if ("version".equals(member.key())) {
                version = member.valueText();
            } else if ("tasks".equals(member.key())) {
                tasks = readArrayObjects(member.valueText());
            } else {
                otherMembers.add(member);
            }
        }
        if (tasks == null) {
            return null;
        }
        return new TasksFile(version, tasks, otherMembers);
    }

    private List<Member> readMembers(String json) {
        String trimmed = json == null ? "" : json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        List<String> parts = splitTopLevel(trimmed.substring(1, trimmed.length() - 1));
        List<Member> members = new ArrayList<>();
        for (String part : parts) {
            String memberText = part.trim();
            if (memberText.isEmpty()) {
                continue;
            }
            int colon = topLevelColon(memberText);
            if (colon < 0) {
                return null;
            }
            String key = readJsonString(memberText.substring(0, colon).trim());
            if (key == null) {
                return null;
            }
            members.add(new Member(key, memberText, memberText.substring(colon + 1).trim()));
        }
        return members;
    }

    private List<String> readArrayObjects(String valueText) {
        String trimmed = valueText.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return null;
        }
        List<String> values = splitTopLevel(trimmed.substring(1, trimmed.length() - 1));
        List<String> objects = new ArrayList<>();
        for (String value : values) {
            String object = value.trim();
            if (object.isEmpty()) {
                continue;
            }
            if (!object.startsWith("{") || !object.endsWith("}")) {
                return null;
            }
            objects.add(object);
        }
        return objects;
    }

    private String readStringProperty(String objectText, String propertyName) {
        List<Member> members = readMembers(objectText);
        if (members == null) {
            return null;
        }
        for (Member member : members) {
            if (propertyName.equals(member.key())) {
                return readJsonString(member.valueText());
            }
        }
        return null;
    }

    private int topLevelColon(String text) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
            } else if (ch == '"') {
                inString = true;
            } else if (ch == '{' || ch == '[') {
                depth++;
            } else if (ch == '}' || ch == ']') {
                depth--;
            } else if (ch == ':' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private List<String> splitTopLevel(String content) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{' || ch == '[') {
                depth++;
            } else if (ch == '}' || ch == ']') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                parts.add(content.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(content.substring(start));
        return parts;
    }

    private String readJsonString(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("\"") || !trimmed.endsWith("\"")) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = 1; i < trimmed.length() - 1; i++) {
            char ch = trimmed.charAt(i);
            if (escaped) {
                builder.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String stripGeneratedHeader(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        int index = 0;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        if (json.startsWith(VsCodeTasksWriter.GENERATED_HEADER, index)) {
            return json.substring(index + VsCodeTasksWriter.GENERATED_HEADER.length());
        }
        return json;
    }

    private String stripLeadingLineComments(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        int index = 0;
        while (index < json.length()) {
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
            if (index + 1 >= json.length() || json.charAt(index) != '/' || json.charAt(index + 1) != '/') {
                break;
            }
            while (index < json.length() && json.charAt(index) != '\n') {
                index++;
            }
        }
        return json.substring(index);
    }

    private String indent(String text, int spaces) {
        String prefix = " ".repeat(spaces);
        return prefix + text.replace("\n", "\n" + prefix);
    }

    private record TasksFile(String version, List<String> tasks, List<Member> otherMembers) {
    }

    private record Member(String key, String text, String valueText) {
    }
}