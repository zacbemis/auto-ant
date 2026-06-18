package com.gei.autoant.model;

public enum DetectionStatus {
    CONFIDENT("detected confidently"),
    WARNING("detected with warning"),
    NOT_DETECTED("not detected"),
    USER_REQUIRED("user input required"),
    OVERRIDDEN("user override");

    private final String displayName;

    DetectionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}