package com.gtnewhorizons.gtnhgradle.modules;

import com.diffplug.blowdryer.Blowdryer;
import com.diffplug.gradle.spotless.SpotlessPlugin;
import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.gtnhgradle.GTNHConstants;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import org.gradle.api.Project;
import org.gradle.api.plugins.quality.CheckstyleExtension;
import org.gradle.api.plugins.quality.CheckstylePlugin;
import org.gradle.api.tasks.TaskContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Enables and configures Spotless and Checkstyle.
 */
public class CodeStyleModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleCodeStyle;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) {
        if (!gtnh.configuration.disableSpotless) {
            project.getPlugins()
                .apply(SpotlessPlugin.class);
            project.apply(oca -> { oca.from(Blowdryer.file("spotless.gradle")); });
        }
        if (!gtnh.configuration.disableCheckstyle) {
            project.getPlugins()
                .apply(CheckstylePlugin.class);
            final TaskContainer tasks = project.getTasks();
            for (final String disabledTask : ImmutableList.of(
                "checkstylePatchedMc",
                "checkstyleMcLauncher",
                "checkstyleIdeVirtualMain",
                "checkstyleInjectedTags")) {
                tasks.named(disabledTask)
                    .configure(t -> t.setEnabled(false));
            }
            final CheckstyleExtension checkstyle = project.getExtensions()
                .getByType(CheckstyleExtension.class);
            checkstyle.setConfig(
                project.getResources()
                    .getText()
                    .fromString(GTNHConstants.CHECKSTYLE_CONFIG));
        }
    }
}
