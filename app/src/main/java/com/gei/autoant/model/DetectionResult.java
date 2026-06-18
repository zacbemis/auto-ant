package com.gei.autoant.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DetectionResult<T> {
    private final Optional<T> value;
    private final DetectionStatus status;
    private final List<String> warnings;
    private final Optional<String> overrideFlag;

    private DetectionResult(Optional<T> value, DetectionStatus status, List<String> warnings, Optional<String> overrideFlag) {
        this.value = Objects.requireNonNull(value, "value");
        this.status = Objects.requireNonNull(status, "status");
        this.warnings = List.copyOf(warnings);
        this.overrideFlag = Objects.requireNonNull(overrideFlag, "overrideFlag");
    }

    public static <T> DetectionResult<T> confident(T value, String overrideFlag) {
        return new DetectionResult<>(Optional.ofNullable(value), DetectionStatus.CONFIDENT, List.of(), Optional.ofNullable(overrideFlag));
    }

    public static <T> DetectionResult<T> overridden(T value, String overrideFlag) {
        return new DetectionResult<>(Optional.ofNullable(value), DetectionStatus.OVERRIDDEN, List.of(), Optional.ofNullable(overrideFlag));
    }

    public static <T> DetectionResult<T> warning(T value, String overrideFlag, String warning) {
        return warning(value, overrideFlag, List.of(warning));
    }

    public static <T> DetectionResult<T> warning(T value, String overrideFlag, List<String> warnings) {
        return new DetectionResult<>(Optional.ofNullable(value), DetectionStatus.WARNING, warnings, Optional.ofNullable(overrideFlag));
    }

    public static <T> DetectionResult<T> notDetected(String overrideFlag) {
        return notDetected(overrideFlag, List.of());
    }

    public static <T> DetectionResult<T> notDetected(String overrideFlag, List<String> warnings) {
        return new DetectionResult<>(Optional.empty(), DetectionStatus.NOT_DETECTED, warnings, Optional.ofNullable(overrideFlag));
    }

    public static <T> DetectionResult<T> userRequired(String overrideFlag, String warning) {
        return userRequired(overrideFlag, List.of(warning));
    }

    public static <T> DetectionResult<T> userRequired(String overrideFlag, List<String> warnings) {
        return new DetectionResult<>(Optional.empty(), DetectionStatus.USER_REQUIRED, warnings, Optional.ofNullable(overrideFlag));
    }

    public static <T> DetectionResult<T> userRequired(T value, String overrideFlag, String warning) {
        return userRequired(value, overrideFlag, List.of(warning));
    }

    public static <T> DetectionResult<T> userRequired(T value, String overrideFlag, List<String> warnings) {
        return new DetectionResult<>(Optional.ofNullable(value), DetectionStatus.USER_REQUIRED, warnings, Optional.ofNullable(overrideFlag));
    }

    public DetectionResult<T> withAdditionalWarnings(List<String> additionalWarnings) {
        if (additionalWarnings.isEmpty()) {
            return this;
        }
        List<String> combined = new ArrayList<>(warnings);
        combined.addAll(additionalWarnings);
        DetectionStatus nextStatus = status == DetectionStatus.NOT_DETECTED || status == DetectionStatus.USER_REQUIRED || status == DetectionStatus.OVERRIDDEN
                ? status
                : DetectionStatus.WARNING;
        return new DetectionResult<>(value, nextStatus, combined, overrideFlag);
    }

    public Optional<T> value() {
        return value;
    }

    public DetectionStatus status() {
        return status;
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    public Optional<String> overrideFlag() {
        return overrideFlag;
    }
}