package com.gtnewhorizons.gtnhgradle.tasks;

import com.gtnewhorizons.gtnhgradle.BuildConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The settings update task, loaded as an isolated class from the future when updating the buildscript. */
public class SettingsUpdater {

    private static final Pattern BLOWDRYER_BLOCK_PATTERN = Pattern.compile("blowdryerSetup\\s*\\{[^}]*}");
    private static final Pattern PLUGIN_VERSION_PATTERN = Pattern.compile(
        "(['\"]com\\.gtnewhorizons\\.gtnhsettingsconvention['\"]\\s*\\)?\\s*version\\s*\\(?['\"])([^'\"]+)(['\"])");

    /**
     * Runs the update process
     *
     * @param settingsPath Path to the settings.gradle file
     * @throws Throwable for convenience
     */
    @SuppressWarnings("unused") // used by reflection
    public void update(Path settingsPath) throws Throwable {
        final String oldSettings = Files.readString(settingsPath, StandardCharsets.UTF_8);
        String newSettings = oldSettings;

        final Matcher blowdryerBlock = BLOWDRYER_BLOCK_PATTERN.matcher(newSettings);
        if (blowdryerBlock.find()) {
            System.out.println("Removed the old blowdryerSetup block from settings.gradle");
            newSettings = blowdryerBlock.replaceAll("");
        }

        final Matcher versionMatcher = PLUGIN_VERSION_PATTERN.matcher(newSettings);
        if (versionMatcher.find()) {
            final String preVersion = versionMatcher.group(1);
            final String oldVersion = versionMatcher.group(2);
            final String postVersion = versionMatcher.group(3);
            final String newVersion = BuildConfig.VERSION;
            if (oldVersion.equals(newVersion)) {
                System.out.println("Settings.gradle plugin already at the newest version of " + newVersion);
            } else {
                System.out.println("Found plugin version update " + newVersion + " from " + oldVersion + ", applying");
            }
            newSettings = newSettings.substring(0, versionMatcher.start(2)) + newVersion
                + newSettings.substring(versionMatcher.end(2));
        }

        // noinspection StringEquality
        if (newSettings != oldSettings) {
            Files.writeString(settingsPath, newSettings, StandardCharsets.UTF_8);
        }
    }
}
