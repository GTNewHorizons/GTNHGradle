package com.gtnewhorizons.gtnhgradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/** The task to update the buildscript(s) to the latest GTNHGradle version */
public abstract class UpdateBuildscriptTask extends DefaultTask {

    /**
     * @return The settings.gradle[.kts] file to update
     */
    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getSettingsGradle();

    /**
     * @return The properties.gradle[.kts] file to update
     */
    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getPropertiesGradle();

    /**
     * @return The version to update to
     */
    @Input
    public abstract Property<String> getNewestVersion();

    /**
     * @return The jar of the version to update to
     */
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getNewestVersionJar();

    /**
     * For dependency injection
     */
    @Inject
    public UpdateBuildscriptTask() {
        this.setGroup("GTNH Buildscript");
        this.setDescription("Updates the build script to the latest version");
        // Ensure the task always runs
        this.getOutputs()
            .upToDateWhen(Specs.satisfyNone());
        // Update the wrapper too
        if (!Boolean.getBoolean("DISABLE_BUILDSCRIPT_GRADLE_UPDATE")) {
            this.dependsOn("wrapper");
        }
    }

    /**
     * Runs the update process
     *
     * @throws Throwable for convenience
     */
    @TaskAction
    public void doUpdate() throws Throwable {
        final File jar = getNewestVersionJar().getAsFile()
            .get();
        final URL jarUrl;
        try {
            jarUrl = jar.toURI()
                .toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        try (final URLClassLoader jarLoader = new URLClassLoader(new URL[] { jarUrl }, null)) {
            updateProperties(jarLoader);
            updateSettings(jarLoader);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void updateProperties(URLClassLoader newJarLoader) throws Exception {
        final Path settingsPath = getSettingsGradle().getAsFile()
            .get()
            .toPath();

        final Class<?> propsClass = Class
            .forName("com.gtnewhorizons.gtnhgradle.PropertiesConfiguration", true, newJarLoader);
        final Object propsObject = propsClass.getConstructor()
            .newInstance();
        final Method generateUpdatedPropertiesMethod = propsClass
            .getMethod("generateUpdatedProperties", Path.class, Map.class);

        final Properties p = new Properties();
        final Path propertiesPath = getPropertiesGradle().getAsFile()
            .get()
            .toPath();
        try (final Reader rdr = Files.newBufferedReader(propertiesPath, StandardCharsets.UTF_8)) {
            p.load(rdr);
        }
        final Map<String, String> originalProps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<Object, Object> entry : p.entrySet()) {
            originalProps.put(
                entry.getKey()
                    .toString(),
                entry.getValue()
                    .toString());
        }

        final String newProps = (String) generateUpdatedPropertiesMethod
            .invoke(propsObject, settingsPath, originalProps);
        Files.writeString(propertiesPath, newProps, StandardCharsets.UTF_8);
    }

    private void updateSettings(URLClassLoader newJarLoader) throws Exception {
        final Path settingsPath = getSettingsGradle().getAsFile()
            .get()
            .toPath();
        final Class<?> upClass = Class
            .forName("com.gtnewhorizons.gtnhgradle.tasks.SettingsUpdater", true, newJarLoader);
        final Object upInstance = upClass.getConstructor()
            .newInstance();
        final Method update = upClass.getMethod("update", Path.class);
        update.invoke(upInstance, settingsPath);
    }
}
