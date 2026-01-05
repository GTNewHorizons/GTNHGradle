package com.gtnewhorizons.gtnhgradle;

import org.jetbrains.annotations.NotNull;

/**
 * Defines the available modes for modern Java syntax support.
 */
public enum ModernJavaSyntaxMode {

    /** No modern syntax support - Java 8 only */
    FALSE("false"),
    /** Jabel-based syntax support (syntax only, no stdlib APIs) */
    JABEL("jabel"),
    /** JVM Downgrader support (syntax + stdlib APIs, multi-release jar) */
    JVM_DOWNGRADER("jvmDowngrader"),
    /** Native modern Java (no downgrading, requires modern JVM at runtime) */
    MODERN("modern");

    private final String propertyValue;

    ModernJavaSyntaxMode(@NotNull String propertyValue) {
        this.propertyValue = propertyValue;
    }

    /** The property value string for this mode */
    @NotNull
    public String getPropertyValue() {
        return propertyValue;
    }

    /** Parses a property string, handling backwards-compatible boolean values. */
    @NotNull
    public static ModernJavaSyntaxMode fromString(@NotNull String value) {
        if (value.isEmpty()) {
            return FALSE;
        }

        // Handle backwards-compatible boolean values
        if (value.equalsIgnoreCase("true")) {
            return JABEL;
        }
        if (value.equalsIgnoreCase("false")) {
            return FALSE;
        }

        for (ModernJavaSyntaxMode mode : values()) {
            if (mode.propertyValue.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
            "Invalid value for enableModernJavaSyntax: '" + value
                + "'. "
                + "Valid values are: false, jabel, jvmDowngrader, modern");
    }

    public boolean usesJvmDowngrader() {
        return this == JVM_DOWNGRADER;
    }

    public boolean requiresModernStdlib() {
        return this == JVM_DOWNGRADER || this == MODERN;
    }
}
