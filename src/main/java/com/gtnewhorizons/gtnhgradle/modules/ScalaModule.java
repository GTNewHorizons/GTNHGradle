package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import org.gradle.api.Project;
import org.gradle.api.plugins.scala.ScalaPlugin;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/** Enables Scala support when src/main/scala exists */
public class ScalaModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleScala;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        if (!gtnh.configuration.forceEnableScala && !project.file("src/main/scala")
            .exists()) {
            gtnh.logger.debug("No src/main/scala detected, skipping Scala initialization.");
            return;
        }

        project.getPluginManager()
            .apply(ScalaPlugin.class);

        // Set up Scala
        project.getTasks()
            .withType(ScalaCompile.class)
            .configureEach(
                sc -> {
                    sc.getOptions()
                        .setEncoding(StandardCharsets.UTF_8.name());
                });
    }
}
