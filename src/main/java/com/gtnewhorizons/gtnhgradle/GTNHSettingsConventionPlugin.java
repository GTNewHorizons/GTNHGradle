package com.gtnewhorizons.gtnhgradle;

import com.diffplug.blowdryer.BlowdryerSetup;
import com.diffplug.blowdryer.BlowdryerSetupPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.toolchains.foojay.FoojayToolchainsConventionPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Applies some shared settings.gradle logic used by the GTNH mod development ecosystem.
 */
@SuppressWarnings("unused") // used by Gradle
public class GTNHSettingsConventionPlugin implements Plugin<Settings> {

    @Override
    public void apply(final @NotNull Settings target) {
        final PropertiesConfiguration config = PropertiesConfiguration.GradleUtils.makePropertiesFrom(target);

        target.getPlugins()
            .apply(FoojayToolchainsConventionPlugin.class);
        if (!config.blowdryerTag.isEmpty()) {
            target.getPlugins()
                .apply(BlowdryerSetupPlugin.class);
            final BlowdryerSetup blowdryer = target.getExtensions()
                .getByType(BlowdryerSetup.class);
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
