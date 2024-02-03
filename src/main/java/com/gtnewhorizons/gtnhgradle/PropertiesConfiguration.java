package com.gtnewhorizons.gtnhgradle;

import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * A helper for accessing gradle Properties entries configuring the GTNH plugins.
 */
public final class PropertiesConfiguration {

    // === Settings properties
    /** See annotation */
    @Prop(
        name = "gtnh.settings.exampleModGithubOwner",
        isSettings = true,
        preferPopulated = false,
        trim = true,
        docComment = "Github owner of the ExampleMod repo to use")
    public @NotNull String exampleModGithubOwner = "GTNewHorizons";

    /** See annotation */
    @Prop(
        name = "gtnh.settings.exampleModGithubProject",
        isSettings = true,
        preferPopulated = false,
        trim = true,
        docComment = "Github project name of the ExampleMod repo to use")
    public @NotNull String exampleModGithubProject = "ExampleMod1.7.10";

    /** See annotation */
    @Prop(
        name = "gtnh.settings.blowdryerTag",
        isSettings = true,
        preferPopulated = true,
        trim = true,
        docComment = "ExampleMod tag to use as Blowdryer (Spotless, etc.) settings version, leave empty to disable. LOCAL to test local config updates.")
    public @NotNull String blowdryerTag = "0.2.2";

    // === Project properties
    // == Module toggles
    /** See annotation */
    @Prop(
        name = "gtnh.modules.gitVersion",
        isSettings = false,
        preferPopulated = false,
        trim = true,
        docComment = "Whether to automatically set the version based on the VERSION environment variable or the current git status.")
    public boolean moduleGitVersion;
    // == Other
    /** See annotation */
    @Prop(name = "versionPattern", isSettings = false, preferPopulated = false, trim = true, docComment = """
        Optional parameter to have the build automatically fail if an illegal version is used.
        This can be useful if you e.g. only want to allow versions in the form of '1.1.xxx'.
        The check is ONLY performed if the version is a git tag.
        Note: the specified string must be escaped, so e.g. 1\\\\.1\\\\.\\\\d+ instead of 1\\.1\\.\\d+
        """)
    public @NotNull String versionPattern = "";

    // API
    /** Fills all properties with default values. */
    public PropertiesConfiguration() {}

    /**
     * Fills properties from the root directory's gradle.properties file and command line "-P" properties.
     *
     * @param settings The Gradle {@link Settings} object, from a settings plugin
     *                 {@link org.gradle.api.Plugin#apply(Object)} parameter
     */
    public PropertiesConfiguration(final Settings settings) {
        this();
        final Path rootDir = settings.getRootDir()
            .toPath();
        final Path propertiesFile = rootDir.resolve("gradle.properties");
        final Properties props = new Properties();
        if (Files.exists(propertiesFile)) {
            try (final Reader rdr = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
                props.load(rdr);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        for (Map.Entry<String, String> entry : settings.getStartParameter()
            .getProjectProperties()
            .entrySet()) {
            props.setProperty(entry.getKey(), entry.getValue());
        }
        initFromProperties(props);
    }

    /**
     * Fills properties from the current project's state.
     *
     * @param project The Gradle {@link Project} to read the properties from.
     */
    public PropertiesConfiguration(final Project project) {
        this();
        initFromProperties(project.getProperties());
    }

    private void initFromProperties(Map<?, ?> props) {
        final Field[] fields = getClass().getDeclaredFields();
        for (final Field field : fields) {
            final Prop prop = field.getAnnotation(Prop.class);
            if (prop == null) {
                continue;
            }
            final String key = prop.name();
            final Object value = props.getOrDefault(key, null);
            if (value == null) {
                continue;
            }
            String strValue = value.toString();
            if (strValue == null) {
                strValue = "";
            } else if (prop.trim()) {
                strValue = strValue.trim();
            }
            try {
                if (field.getType() == String.class) {
                    field.set(this, strValue);
                } else if (field.getType() == boolean.class) {
                    field.setBoolean(this, Boolean.parseBoolean(strValue));
                } else if (field.getType() == Boolean.class) {
                    field.set(this, Boolean.parseBoolean(strValue));
                } else if (field.getType() == int.class) {
                    field.setInt(this, Integer.parseInt(strValue));
                } else if (field.getType() == Integer.class) {
                    field.set(this, Integer.parseInt(strValue));
                }
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Property metadata */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Prop {

        /** @return Name of the property to search for */
        @NotNull
        String name();

        /** @return Is the property is used globally across many projects from a settings.gradle context? */
        boolean isSettings();

        /** @return Should the property's value be frozen in the properties file on plugin update? */
        boolean preferPopulated();

        /** @return Should the property's value be trimmed before parsing/saving? */
        boolean trim();

        /** @return User documentation for the property */
        @NotNull
        String docComment();
    }
}
