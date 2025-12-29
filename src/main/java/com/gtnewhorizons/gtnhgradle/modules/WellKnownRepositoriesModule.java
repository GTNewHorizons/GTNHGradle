package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/** Provides various well-known repositories to the buildscript */
public class WellKnownRepositoriesModule implements GTNHModule {

    @Override
    public boolean isEnabled(GTNHGradlePlugin.@NotNull GTNHExtension gtnh) {
        return gtnh.configuration.includeWellKnownRepositories;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final RepositoryHandler repos = project.getRepositories();
        final ModUtils modUtils = project.getExtensions()
            .getByType(ModUtils.class);

        List<String> excludes = Arrays.asList(
            gtnh.configuration.excludeWellKnownRepositories.toUpperCase()
                .split(" "));

        if (!excludes.contains("CURSEMAVEN")) {
            repos.exclusiveContent(ec -> {
                ec.forRepositories(repos.maven(mvn -> {
                    mvn.setName("CurseMaven");
                    mvn.setUrl("https://cursemaven.com");
                }));
                ec.filter(f -> { f.includeGroup("curse.maven"); });
            });
        }

        if (!excludes.contains("MODRINTH")) {
            repos.exclusiveContent(ec -> {
                ec.forRepositories(repos.maven(mvn -> {
                    mvn.setName("Modrinth");
                    mvn.setUrl("https://api.modrinth.com/maven");
                }));
                ec.filter(f -> { f.includeGroup("maven.modrinth"); });
            });
        }
    }
}
