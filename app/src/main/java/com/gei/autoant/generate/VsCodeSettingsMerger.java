package com.gei.autoant.generate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class VsCodeSettingsMerger {
    String merge(String existingSettings, String generatedSettings) {
        List<Member> existingMembers = readMembers(existingSettings);
        List<Member> generatedMembers = readMembers(generatedSettings);
        if (existingMembers == null || generatedMembers == null) {
            return generatedSettings;
        }

        Map<String, Member> generatedByKey = new LinkedHashMap<>();
        for (Member member : generatedMembers) {
            generatedByKey.put(member.key(), member);
        }

        List<Member> merged = new ArrayList<>();
        for (Member member : existingMembers) {
            if (!generatedByKey.containsKey(member.key())) {
                merged.add(member);
            }
        }
        merged.addAll(generatedByKey.values());

        StringBuilder builder = new StringBuilder("{\n");
        for (int i = 0; i < merged.size(); i++) {
            builder.append(indent(merged.get(i).text()));
            if (i + 1 < merged.size()) {
                builder.append(',');
            }
            builder.append('\n');
        }
        return builder.append("}\n").toString();
    }

    private List<Member> readMembers(String json) {
        String trimmed = json == null ? "" : json.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
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
            String key = readKey(memberText);
            if (key == null) {
                return null;
            }
            members.add(new Member(key, memberText));
        }
        return members;
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

    private String readKey(String memberText) {
        if (!memberText.startsWith("\"")) {
            return null;
        }
        StringBuilder key = new StringBuilder();
        boolean escaped = false;
        for (int i = 1; i < memberText.length(); i++) {
            char ch = memberText.charAt(i);
            if (escaped) {
                key.append(ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                return key.toString();
            } else {
                key.append(ch);
            }
        }
        return null;
    }

    private String indent(String text) {
        return "  " + text.replace("\n", "\n  ");
    }

    private record Member(String key, String text) {
    }
}