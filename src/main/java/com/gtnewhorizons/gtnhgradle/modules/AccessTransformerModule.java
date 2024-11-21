package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.mcp.MCPTasks;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/** Implements Access Transformers support */
public class AccessTransformerModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleAccessTransformers;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final MCPTasks mcpTasks = project.getExtensions()
            .getByType(MCPTasks.class);
        final Path projectRoot = project.getProjectDir()
            .toPath();
        final Path metaInf = projectRoot.resolve("src")
            .resolve("main")
            .resolve("resources")
            .resolve("META-INF");

        final SourceSetContainer sourceSets = project.getExtensions()
            .getByType(JavaPluginExtension.class)
            .getSourceSets();
        final ConfigurableFileCollection atList = mcpTasks.getDeobfuscationATs();

        if (!gtnh.configuration.accessTransformersFile.isEmpty()) {
            String commaSeparated = gtnh.configuration.accessTransformersFile.replaceAll("\\s+(\\s*)", ",$1");
            for (String atFile : commaSeparated.split(",")) {
                final Path targetFile = metaInf.resolve(atFile.trim());
                if (!Files.exists(metaInf.resolve(targetFile))) {
                    throw new GradleException(
                        "Could not resolve \"accessTransformersFile\"! Could not find " + targetFile);
                }
                atList.from(projectRoot.relativize(targetFile));
            }
        } else {
            boolean atsFound = false;
            for (File at : sourceSets.getByName("main")
                .getResources()
                .getFiles()) {
                if (at.getName()
                    .toLowerCase()
                    .endsWith("_at.cfg")) {
                    atsFound = true;
                    atList.from(at);
                }
            }
            for (File at : sourceSets.getByName("api")
                .getResources()
                .getFiles()) {
                if (at.getName()
                    .toLowerCase()
                    .endsWith("_at.cfg")) {
                    atsFound = true;
                    atList.from(at);
                }
            }
            if (atsFound) {
                gtnh.logger.warn(
                    "Found and added access transformers in the resources folder, please configure gradle.properties to explicitly mention them by name");
            }
        }

    }
}
