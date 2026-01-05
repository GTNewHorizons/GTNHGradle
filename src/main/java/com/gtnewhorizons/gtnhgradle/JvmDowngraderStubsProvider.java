package com.gtnewhorizons.gtnhgradle;

import org.jetbrains.annotations.NotNull;

/**
 * Defines how JVM Downgrader API stubs are provided at runtime.
 */
public enum JvmDowngraderStubsProvider {

    /** Default: GTNHLib provides stubs at runtime (no shading needed) */
    GTNHLIB("gtnhlib"),
    /** Shade stubs into the mod jar */
    SHADE("shade"),
    /** External dependency provides stubs (user manages it) */
    EXTERNAL("external");

    private final String propertyValue;

    JvmDowngraderStubsProvider(@NotNull String propertyValue) {
        this.propertyValue = propertyValue;
    }

    @NotNull
    public static JvmDowngraderStubsProvider fromString(@NotNull String value) {
        if (value.isEmpty()) {
            return GTNHLIB;
        }

        for (JvmDowngraderStubsProvider provider : values()) {
            if (provider.propertyValue.equals(value)) {
                return provider;
            }
        }

        throw new IllegalArgumentException(
            "Invalid jvmDowngraderStubsProvider: '" + value
                + "'. Valid values are: shade, gtnhlib, external, or empty (defaults to gtnhlib).");
    }

    public boolean shouldShadeStubs() {
        return this == SHADE;
    }

    public boolean isExternal() {
        return this == EXTERNAL;
    }
}
