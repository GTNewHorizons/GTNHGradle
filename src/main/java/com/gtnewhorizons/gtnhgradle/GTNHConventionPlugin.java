package com.gtnewhorizons.gtnhgradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Applies {@link GTNHGradlePlugin} and turns on all standard GTNH modules based on the properties set on the project.
 */
@SuppressWarnings("unused") // used by Gradle
public class GTNHConventionPlugin implements Plugin<Project> {

    @Override
    public void apply(@NotNull Project project) {
        project.getPlugins()
            .apply(GTNHGradlePlugin.class);

        final GTNHGradlePlugin.GTNHExtension gtnh = project.getExtensions()
            .findByType(GTNHGradlePlugin.GTNHExtension.class);
        Objects.requireNonNull(gtnh);
        final PropertiesConfiguration config = gtnh.configuration;

        gtnh.applyAllModules(project);

    }
}
