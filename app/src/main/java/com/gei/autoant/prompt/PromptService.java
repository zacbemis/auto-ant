package com.gei.autoant.prompt;

import java.util.Optional;

public interface PromptService {
    Optional<String> promptOverride(String label, String detectedValue);

    default Optional<String> promptRequired(String label, String detectedValue) {
        return promptOverride(label, detectedValue);
    }
}