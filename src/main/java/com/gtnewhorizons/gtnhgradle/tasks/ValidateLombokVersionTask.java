package com.gtnewhorizons.gtnhgradle.tasks;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

/**
 * Validates that Lombok versions are compatible with Java 25+ bytecode.
 */
public abstract class ValidateLombokVersionTask extends DefaultTask {

    /** @return Lombok version strings to validate */
    @Input
    public abstract ListProperty<String> getLombokVersions();

    /** @return Minimum required Lombok version */
    @Input
    public abstract Property<String> getMinimumVersion();

    /** Validates all Lombok versions meet the minimum requirement. */
    @TaskAction
    public void validate() {
        final String minVersion = getMinimumVersion().get();
        for (String version : getLombokVersions().get()) {
            if (!isVersionSufficient(version, minVersion)) {
                throw new GradleException(
                    "Lombok version " + version
                        + " does not support Java 25+ bytecode. "
                        + "Please upgrade to Lombok >= "
                        + minVersion
                        + ". "
                        + "See https://github.com/projectlombok/lombok/releases for the latest version.");
            }
        }
    }

    private static boolean isVersionSufficient(String version, String minVersion) {
        final ComparableVersion current = new ComparableVersion(version);
        final ComparableVersion minimum = new ComparableVersion(minVersion);
        return current.compareTo(minimum) >= 0;
    }
}
