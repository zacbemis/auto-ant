package com.gei.autoant.generate;

import java.util.Map;

public final class TemplateRenderer {
    public String render(String template, Map<String, String> values) {
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }
}