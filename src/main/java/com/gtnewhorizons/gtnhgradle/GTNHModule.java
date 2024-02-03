package com.gtnewhorizons.gtnhgradle;

import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;

/**
 * A toggleable module of the GTNH buildscript plugin.
 */
public interface GTNHModule {

    /**
     * @param configuration The project configuration
     * @return If this module should be enabled for the input project settings
     */
    boolean isEnabled(@NotNull PropertiesConfiguration configuration);

    /**
     * Activates this module on the given project.
     *
     * @param gtnh    The GTNH extension providing access to various gradle services and configuration
     * @param project The project to activate the module on
     * @throws Throwable Any exception to allow easier module writing
     */
    void apply(@NotNull GTNHGradlePlugin.GTNHExtension gtnh, @NotNull Project project) throws Throwable;

    /**
     * Checks if the module should be enabled, and if it is - activates it.
     * Registers a gradle project ext property "activatedModuleClassName" set to true or false depending if the module
     * was enabled.
     * Also registers another ext property "gtnhModuleClassName" set to the instance of the constructed module
     * (regardless of enabled status).
     *
     * @param moduleClass The module class to enable.
     * @param gtnh        The GTNH extension providing access to various gradle services and configuration
     * @param project     The project to activate the module on
     */
    static void applyIfEnabled(@NotNull Class<? extends GTNHModule> moduleClass,
        @NotNull GTNHGradlePlugin.GTNHExtension gtnh, @NotNull Project project) {
        final String name = moduleClass.getSimpleName();
        final String extActivated = "activated" + name;
        final String extInstance = "gtnh" + name;
        final ExtraPropertiesExtension extraProperties = project.getExtensions()
            .getExtraProperties();

        if (extraProperties.has(extInstance)) {
            project.getLogger()
                .warn("Attempted another activation of module {}, skipping.", name, new Throwable());
            return;
        }

        final GTNHModule instance;
        try {
            instance = moduleClass.getConstructor()
                .newInstance();
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        final boolean enabled = instance.isEnabled(gtnh.configuration);
        if (enabled) {
            try {
                instance.apply(gtnh, project);
            } catch (Throwable t) {
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else {
                    throw new RuntimeException(t);
                }
            }
        }

        extraProperties.set(extActivated, enabled);
        extraProperties.set(extInstance, instance);
    }

}
