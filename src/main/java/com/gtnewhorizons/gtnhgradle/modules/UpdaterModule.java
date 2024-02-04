package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

/** Allows automatic buildscript updates */
public class UpdaterModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleUpdater;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {

    }
}
