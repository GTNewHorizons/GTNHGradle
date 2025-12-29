package com.gtnewhorizons.gtnhgradle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for {@link ModernJavaSyntaxMode} */
class ModernJavaSyntaxModeTest {

    @ParameterizedTest
    @CsvSource({ "false, FALSE", "FALSE, FALSE", "jabel, JABEL", "JABEL, JABEL", "jvmDowngrader, JVM_DOWNGRADER",
        "JVMDOWNGRADER, JVM_DOWNGRADER", "modern, MODERN", "MODERN, MODERN", "'', FALSE" })
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

    @Test
    void usesJvmDowngrader() {
        assertFalse(ModernJavaSyntaxMode.FALSE.usesJvmDowngrader());
        assertFalse(ModernJavaSyntaxMode.JABEL.usesJvmDowngrader());
        assertTrue(ModernJavaSyntaxMode.JVM_DOWNGRADER.usesJvmDowngrader());
        assertFalse(ModernJavaSyntaxMode.MODERN.usesJvmDowngrader());
    }

    @Test
    void requiresModernStdlib() {
        assertFalse(ModernJavaSyntaxMode.FALSE.requiresModernStdlib());
        assertFalse(ModernJavaSyntaxMode.JABEL.requiresModernStdlib());
        assertTrue(ModernJavaSyntaxMode.JVM_DOWNGRADER.requiresModernStdlib());
        assertTrue(ModernJavaSyntaxMode.MODERN.requiresModernStdlib());
    }

    @Test
    void getPropertyValue() {
        assertEquals("false", ModernJavaSyntaxMode.FALSE.getPropertyValue());
        assertEquals("jabel", ModernJavaSyntaxMode.JABEL.getPropertyValue());
        assertEquals("jvmDowngrader", ModernJavaSyntaxMode.JVM_DOWNGRADER.getPropertyValue());
        assertEquals("modern", ModernJavaSyntaxMode.MODERN.getPropertyValue());
    }
}
