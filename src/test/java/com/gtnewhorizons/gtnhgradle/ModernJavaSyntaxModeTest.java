package com.gtnewhorizons.gtnhgradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for {@link ModernJavaSyntaxMode} */
class ModernJavaSyntaxModeTest {

    @ParameterizedTest
    @CsvSource({ "false, FALSE", "FALSE, FALSE", "jabel, JABEL", "jvmDowngrader, JVM_DOWNGRADER", "modern, MODERN",
        "'', FALSE" })
    void fromString_validValues(String input, ModernJavaSyntaxMode expected) {
        assertEquals(expected, ModernJavaSyntaxMode.fromString(input));
    }

    @Test
    void fromString_trueBackwardsCompat() {
        // "true" should map to JABEL for backwards compatibility (with deprecation warning)
        assertEquals(ModernJavaSyntaxMode.JABEL, ModernJavaSyntaxMode.fromString("true"));
        assertEquals(ModernJavaSyntaxMode.JABEL, ModernJavaSyntaxMode.fromString("TRUE"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "invalid", "yes", "no", "jabel2", "downgrader" })
    void fromString_invalidValues(String input) {
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> ModernJavaSyntaxMode.fromString(input));
        assertTrue(
            ex.getMessage()
                .contains("Invalid value for enableModernJavaSyntax"));
        assertTrue(
            ex.getMessage()
                .contains(input));
    }

    @ParameterizedTest
    @EnumSource(ModernJavaSyntaxMode.class)
    void usesJvmDowngrader(ModernJavaSyntaxMode mode) {
        boolean expected = switch (mode) {
            case FALSE, JABEL, MODERN -> false;
            case JVM_DOWNGRADER -> true;
        };
        assertEquals(expected, mode.usesJvmDowngrader());
    }

    @ParameterizedTest
    @EnumSource(ModernJavaSyntaxMode.class)
    void requiresModernStdlib(ModernJavaSyntaxMode mode) {
        boolean expected = switch (mode) {
            case FALSE, JABEL -> false;
            case JVM_DOWNGRADER, MODERN -> true;
        };
        assertEquals(expected, mode.requiresModernStdlib());
    }

    @ParameterizedTest
    @EnumSource(ModernJavaSyntaxMode.class)
    void getPropertyValue(ModernJavaSyntaxMode mode) {
        String expected = switch (mode) {
            case FALSE -> "false";
            case JABEL -> "jabel";
            case JVM_DOWNGRADER -> "jvmDowngrader";
            case MODERN -> "modern";
        };
        assertEquals(expected, mode.getPropertyValue());
    }
}
