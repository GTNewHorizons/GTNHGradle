package com.gtnewhorizons.gtnhgradle;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
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

    private static final Logger LOGGER = Logging.getLogger(ModernJavaSyntaxMode.class);

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
            LOGGER.warn(
                "enableModernJavaSyntax=true is deprecated. Use 'jabel' for Jabel syntax support, "
                    + "or 'jvmDowngrader' for full modern Java with stdlib APIs.");
            return JABEL;
        }
        if (value.equalsIgnoreCase("false")) {
            return FALSE;
        }

        for (ModernJavaSyntaxMode mode : values()) {
            if (mode.propertyValue.equalsIgnoreCase(value)) {
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
