package com.gei.autoant.model;

public enum ServletNamespace {
    JAVAX("javax.servlet"),
    JAKARTA("jakarta.servlet"),
    BOTH("both javax.servlet and jakarta.servlet"),
    UNKNOWN("unknown");

    private final String displayName;

    ServletNamespace(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}