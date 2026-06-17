package com.gei.autoant.model;

import java.util.Locale;

public enum ReloadStrategy {
    MANAGER("manager"),
    TOUCH_WEBXML("touch-webxml"),
    NONE("none");

    private final String propertyValue;

    ReloadStrategy(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public static ReloadStrategy parse(String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (ReloadStrategy strategy : values()) {
            if (strategy.propertyValue.equals(normalized)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unknown reload strategy: " + value + ". Expected manager, touch-webxml, or none.");
    }
}