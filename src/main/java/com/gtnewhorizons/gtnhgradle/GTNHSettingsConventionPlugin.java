package com.gtnewhorizons.gtnhgradle;

import com.diffplug.blowdryer.BlowdryerSetup;
import com.diffplug.blowdryer.BlowdryerSetupPlugin;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.PluginManager;
import org.gradle.toolchains.foojay.FoojayToolchainsConventionPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Applies some shared settings.gradle logic used by the GTNH mod development ecosystem.
 */
@SuppressWarnings("unused") // used by Gradle
public abstract class GTNHSettingsConventionPlugin implements Plugin<Settings> {

    @Override
    public void apply(final @NotNull Settings target) {
        final Logger logger = Logging.getLogger(GTNHSettingsConventionPlugin.class);
        final PropertiesConfiguration config = PropertiesConfiguration.GradleUtils.makePropertiesFrom(target);
        final PluginManager plugins = target.getPluginManager();
        final JavaVersion currentJava = JavaVersion.current();

        if (config.dynamicSpotlessVersion) {
            // Add the correct version of spotless to classpath
            final String spotlessVersion;
            spotlessVersion = UpdateableConstants.NEWEST_SPOTLESS;
            logger.info("Adding Spotless {} to classpath due to Java {}", spotlessVersion, currentJava);
            target.getGradle()
                .beforeProject(prj -> {
                    prj.getBuildscript()
                        .getDependencies()
                        .add("classpath", "com.diffplug.spotless:spotless-plugin-gradle:" + spotlessVersion);
                });
        }

        plugins.apply(FoojayToolchainsConventionPlugin.class);
        if (!config.blowdryerTag.isEmpty()) {
            plugins.apply(BlowdryerSetupPlugin.class);
            final BlowdryerSetup blowdryer = target.getExtensions()
                .getByType(BlowdryerSetup.class);
            if (!config.blowdryerTag.equals("MIGRATION-MAGIC")) {
                blowdryer.repoSubfolder("gtnhShared");
                if ("LOCAL".equals(config.blowdryerTag)) {
                    blowdryer.devLocal(".");
                } else {
                    blowdryer.github(
                        config.exampleModGithubOwner + "/" + config.exampleModGithubProject,
                        BlowdryerSetup.GitAnchorType.TAG,
                        config.blowdryerTag);
                }
            }
        }

        target.getGradle()
            .beforeProject(p -> {
                p.getExtensions()
                    .getExtraProperties()
                    .set(
                        GTNHConstants.SETTINGS_GRADLE_FILE_PROPERTY,
                        target.getBuildscript()
                            .getSourceFile());
            });
    }
}
