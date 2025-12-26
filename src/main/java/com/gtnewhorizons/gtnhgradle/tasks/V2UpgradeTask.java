package com.gtnewhorizons.gtnhgradle.tasks;

import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public abstract class V2UpgradeTask extends DefaultTask {

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

    @Inject
    public V2UpgradeTask() {
        this.setGroup("GTNH Buildscript");
        this.setDescription("Upgrades the build script to the 2.x major version");
        // Ensure the task always runs
        this.getOutputs()
            .upToDateWhen(Specs.satisfyNone());
    }

    @TaskAction
    public void performUpgrade() throws Throwable {
        updateProperties();
        updateSettings();
        getLogger().warn(
            "Make sure to run ./gradlew updateBuildScript after this command finishes to finish the v2 upgrade process.");
    }

    private void updateProperties() throws Throwable {
        final Path settingsPath = getSettingsGradle().getAsFile()
            .get()
            .toPath();
        PropertiesConfiguration propsObject = new PropertiesConfiguration();

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

        // Enable parallel builds and configuration cache
        originalProps.put("org.gradle.configuration-cache", "true");
        originalProps.put("org.gradle.parallel", "true");

        final String newProps = propsObject.generateUpdatedProperties(settingsPath, originalProps);
        Files.write(propertiesPath, newProps.getBytes(StandardCharsets.UTF_8));
    }

    private void updateSettings() throws Throwable {
        final Path settingsPath = getSettingsGradle().getAsFile()
            .get()
            .toPath();
        SettingsUpdater updater = new SettingsUpdater();
        updater.update(settingsPath, "2.0.7");
    }
}
