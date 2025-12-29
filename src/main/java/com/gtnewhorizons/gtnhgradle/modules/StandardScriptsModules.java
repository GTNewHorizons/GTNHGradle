package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.retrofuturagradle.shadow.com.google.common.collect.ImmutableList;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

/** All modules for loading standard scripts */
public class StandardScriptsModules {

    private static abstract class StandardScriptModule implements GTNHModule {

        /** @return Name of the script to execute without the extension */
        public abstract @NotNull String getScriptName();

        @Override
        public final boolean isEnabled(GTNHGradlePlugin.@NotNull GTNHExtension gtnh) {
            return gtnh.configuration.moduleStandardScripts;
        }

        @Override
        public final void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project)
            throws Throwable {
            final String scriptName = getScriptName();
            final String groovyName = scriptName + ".gradle";
            final String ktsName = scriptName + ".gradle.kts";
            final String localGroovyName = scriptName + ".local.gradle";
            final String localKtsName = scriptName + ".local.gradle.kts";
            for (final String name : ImmutableList.of(groovyName, ktsName, localGroovyName, localKtsName)) {
                if (project.file(name)
                    .exists()) {
                    project.apply(oca -> { oca.from(name); });
                }
            }
        }
    }

    /** Early addon script */
    public static class AddonScriptModule extends StandardScriptModule {

        @Override
        public @NotNull String getScriptName() {
            return "addon";
        }
    }

    /** Dependencies script */
    public static class DependenciesScriptModule extends StandardScriptModule {

        @Override
        public @NotNull String getScriptName() {
            return "dependencies";
        }
    }

    /** Repositories script */
    public static class RepositoriesScriptModule extends StandardScriptModule {

        @Override
        public @NotNull String getScriptName() {
            return "repositories";
        }
    }

    /** Late addon script */
    public static class LateAddonScriptModule extends StandardScriptModule {

        @Override
        public @NotNull String getScriptName() {
            return "addon.late";
        }
    }
}
