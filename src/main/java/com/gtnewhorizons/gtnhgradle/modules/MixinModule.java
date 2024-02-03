package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.MinecraftExtension;
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

/** Easy Unimixins support. */
public class MixinModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleMixin;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final MinecraftExtension minecraft = project.getExtensions()
            .getByType(MinecraftExtension.class);
        final ModUtils modUtils = project.getExtensions()
            .getByType(ModUtils.class);

        if (gtnh.configuration.usesMixins || gtnh.configuration.forceEnableMixins) {
            if (gtnh.configuration.usesMixinsDebug) {
                minecraft.getExtraRunJvmArguments()
                    .addAll(
                        "-Dmixin.debug.countInjections=true",
                        "-Dmixin.debug.verbose=true",
                        "-Dmixin.debug.export=true");
            }
        }
    }
}
