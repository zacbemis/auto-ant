package com.gei.autoant.prompt;

import java.util.Optional;

public interface PromptService {
    Optional<String> promptOverride(String label, String detectedValue);
}