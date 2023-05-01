/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.gtnewhorizons.gtnhgradle;

import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.api.Project;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A simple unit test for the 'com.gtnewhorizons.gtnhgradle.greeting' plugin.
 */
class GTNHGradlePluginTest {

    private static Project makeGtnhProject() {
        final Project project = ProjectBuilder.builder()
            .build();
        project.getPlugins()
            .apply("com.gtnewhorizons.gtnhgradle");
        return project;
    }

    @Test
    void pluginRegistersATask() {
        final Project project = makeGtnhProject();

        assertNotNull(
            project.getTasks()
                .findByName("downloadVanillaJars")); // Check RFG tasks
    }

    @Test
    void gtnhExtensionAccessible() {
        final Project project = makeGtnhProject();
        assertInstanceOf(
            GTNHGradlePlugin.GTNHExtension.class,
            project.getExtensions()
                .getByName("gtnhGradle"));
    }
}
