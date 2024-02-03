package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.repositories.UrlArtifactRepository;
import org.jetbrains.annotations.NotNull;

/**
 * Emulates various old gradle version behaviours for backwards compatibility - HTTP protocol support, "compile"
 * configuration, etc.
 */
public class OldGradleEmulationModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleOldGradleEmulation;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        project.getRepositories()
            .configureEach(repo -> {
                if (repo instanceof UrlArtifactRepository urlRepo) {
                    if (urlRepo.getUrl() != null && "http".equalsIgnoreCase(
                        urlRepo.getUrl()
                            .getScheme())
                        && !urlRepo.isAllowInsecureProtocol()) {
                        gtnh.logger.warn(
                            "Deprecated: Allowing insecure connections for repo '" + repo.getName()
                                + "' - add 'allowInsecureProtocol = true' to silence");
                        urlRepo.setAllowInsecureProtocol(true);
                    }
                }
            });
        final ConfigurationContainer cfg = project.getConfigurations();
        final Configuration oldCompile = cfg.create("compile", c -> {
            c.setDescription("Deprecated: use api or implementation instead, gets put in api");
            c.setCanBeConsumed(false);
            c.setCanBeResolved(false);
            c.setVisible(false);
        });
        final Configuration oldTestCompile = cfg.create("testCompile", c -> {
            c.setDescription("Deprecated: use testImplementation instead");
            c.setCanBeConsumed(false);
            c.setCanBeResolved(false);
            c.setVisible(false);
        });
        cfg.getByName("api")
            .extendsFrom(oldCompile);
        cfg.getByName("testImplementation")
            .extendsFrom(oldTestCompile);

        project.afterEvaluate(p -> {
            final ConfigurationContainer configs = p.getConfigurations();
            final Configuration compile = configs.getByName("compile");
            final Configuration testCompile = configs.getByName("testCompile");
            if (!compile.getDependencies()
                .isEmpty()
                || !testCompile.getDependencies()
                    .isEmpty()) {
                gtnh.logger.warn(
                    "This project uses deprecated `compile` dependencies, please migrate to using `api` and `implementation`");
                gtnh.logger.warn(
                    "For more details, see https://github.com/GTNewHorizons/ExampleMod1.7.10/blob/master/dependencies.gradle");
            }
        });
    }
}
