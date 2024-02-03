package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.jetbrains.annotations.NotNull;

/** Provides various well-known repositories to the buildscript */
public class WellKnownRepositoriesModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.includeWellKnownRepositories;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final RepositoryHandler repos = project.getRepositories();
        final ModUtils modUtils = project.getExtensions()
            .getByType(ModUtils.class);
        repos.exclusiveContent(ec -> {
            ec.forRepositories(repos.maven(mvn -> {
                mvn.setName("CurseMaven");
                mvn.setUrl("https://cursemaven.com");
            }));
            ec.filter(f -> { f.includeGroup("curse.maven"); });
        });
        repos.exclusiveContent(ec -> {
            ec.forRepositories(repos.maven(mvn -> {
                mvn.setName("Modrinth");
                mvn.setUrl("https://api.modrinth.com/maven");
            }));
            ec.filter(f -> { f.includeGroup("maven.modrinth"); });
        });
        repos.maven(mvn -> {
            mvn.setName("ic2");
            mvn.setUrl(
                modUtils.getLiveMirrorURL(10_000, "https://maven2.ic2.player.to/", "https://maven.ic2.player.to/"));
            mvn.mavenContent(c -> { c.includeGroup("net.industrial-craft"); });
            mvn.getMetadataSources()
                .artifact();
            mvn.getMetadataSources()
                .mavenPom();
        });
        // MMD maven often goes down with a broken certificate
        project.afterEvaluate(p -> {
            repos.maven(mvn -> {
                mvn.setName("MMD Maven");
                mvn.setUrl("https://maven.mcmoddev.com/");
            });
        });
    }
}
