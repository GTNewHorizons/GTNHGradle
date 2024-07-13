package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.jetbrains.annotations.NotNull;

/** Provides extra repositories to the buildscript */
public class ExtraRepositoriesModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        if (configuration.includeWellKnownRepositories) {
            return false;
        }
        return !configuration.extraRepositories.isEmpty();
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final RepositoryHandler repos = project.getRepositories();

        if (!gtnh.configuration.extraRepositories.isEmpty()) {
            for (String repoName : gtnh.configuration.extraRepositories.split(" ")) {
                switch (repoName.toUpperCase()) {
                    case "CURSEMAVEN":
                        addRepo(repos, "CurseMaven", "https://cursemaven.com", "curse.maven");
                        break;
                    case "MODRINTH":
                        addRepo(repos, "Modrinth", "https://api.modrinth.com/maven", "maven.modrinth");
                        break;
                    case "MMD":
                        addRepo(repos, "MMD Maven", "https://maven.mcmoddev.com");
                        break;
                }
            }
        }
    }

    private static void addRepo(RepositoryHandler repos, String name, String url) {
        repos.maven(mvn -> {
            mvn.setName(name);
            mvn.setUrl(url);
        });
    }

    private static void addRepo(RepositoryHandler repos, String name, String url, String filter) {
        repos.exclusiveContent(ec -> {
            ec.forRepositories(repos.maven(mvn -> {
                mvn.setName(name);
                mvn.setUrl(url);
            }));
            ec.filter(f -> { f.includeGroup(filter); });
        });
    }
}
