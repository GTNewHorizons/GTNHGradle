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
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A helper for accessing gradle Properties entries configuring the GTNH plugins.
 */
@SuppressWarnings({ "CanBeFinal", "unused" })
public final class PropertiesConfiguration {

    // <editor-fold desc="Settings.gradle properties">
    /** See annotation */
    @Prop(
        name = "gtnh.settings.exampleModGithubOwner",
        isSettings = true,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Github owner of the ExampleMod repo to use")
    public @NotNull String exampleModGithubOwner = "GTNewHorizons";

    /** See annotation */
    @Prop(
        name = "gtnh.settings.exampleModGithubProject",
        isSettings = true,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Github project name of the ExampleMod repo to use")
    public @NotNull String exampleModGithubProject = "ExampleMod1.7.10";

    /** See annotation */
    @Prop(
        name = "gtnh.settings.blowdryerTag",
        isSettings = true,
        preferPopulated = true,
        required = false,
        docComment = """
            ExampleMod tag to use as Blowdryer (Spotless, etc.) settings version, leave empty to disable.
            LOCAL to test local config updates.
            """)
    public @NotNull String blowdryerTag = UpdateableConstants.NEWEST_BLOWDRYER_TAG;
    // </editor-fold>

    // <editor-fold desc="Project properties">
    // <editor-fold desc="Module toggles">
    /** See annotation */
    @Prop(
        name = "gtnh.modules.gitVersion",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Whether to automatically set the version based on the VERSION environment variable or the current git status.")
    public boolean moduleGitVersion = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.codeStyle",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Sets up code style plugins like Spotless and Checkstyle.")
    public boolean moduleCodeStyle = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.toolchain",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Sets up the Java/Scala/Kotlin toolchain for mod compilation.")
    public boolean moduleToolchain = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.structureCheck",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Checks the project structure for obvious mistakes.")
    public boolean moduleStructureCheck = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.accessTransformers",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Loads and packages access transformers.")
    public boolean moduleAccessTransformers = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.mixin",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Easy UniMixins support.")
    public boolean moduleMixin = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.standardScripts",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Standard script loading, like addon.gradle.")
    public boolean moduleStandardScripts = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.oldGradleEmulation",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Emulates various old gradle version behaviours for backwards compatibility - HTTP protocol support, \"compile\" configuration, etc.")
    public boolean moduleOldGradleEmulation = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.modernJava",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Modern Java run support via lwjgl3ify.")
    public boolean moduleModernJava = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.ideIntegration",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "IDE Integration (IntelliJ, Eclipse)")
    public boolean moduleIdeIntegration = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.publishing",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Publishing targets (Maven, mod hosting platforms).")
    public boolean modulePublishing = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.utility",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Utility tasks and functions.")
    public boolean moduleUtility = true;

    /** See annotation */
    @Prop(
        name = "gtnh.modules.updater",
        isSettings = false,
        preferPopulated = false,
        required = false,
        hidden = true,
        docComment = "Buildscript updater module.")
    public boolean moduleUpdater = true;
    // </editor-fold>

    // <editor-fold desc="Various settings">
    /** See annotation */
    @Prop(name = "modName", isSettings = false, preferPopulated = true, required = true, docComment = """
        Human-readable mod name, available for mcmod.info population.
        """)
    public @NotNull String modName = "MyMod";

    /** See annotation */
    @Prop(
        name = "modId",
        isSettings = false,
        preferPopulated = true,
        required = true,
        docComment = """
            Case-sensitive identifier string, available for mcmod.info population and used for automatic mixin JSON generation.
            Conventionally lowercase.
            """)
    public @NotNull String modId = "mymodid";

    /** See annotation */
    @Prop(name = "modGroup", isSettings = false, preferPopulated = true, required = true, docComment = """
        Root package of the mod, used to find various classes in other properties,
        mcmod.info substitution, enabling assertions in run tasks, etc.
        """)
    public @NotNull String modGroup = "com.myname.mymodid";

    /** See annotation */
    @Prop(
        name = "useModGroupForPublishing",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            Whether to use modGroup as the maven publishing group.
            Due to a history of using JitPack, the default is com.github.GTNewHorizons for all mods.
            """)
    public boolean useModGroupForPublishing = false;

    /** See annotation */
    @Prop(name = "autoUpdateBuildScript", isSettings = false, preferPopulated = true, required = false, docComment = """
        Updates your build.gradle and settings.gradle automatically whenever an update is available.
        """)
    public boolean autoUpdateBuildScript = false;

    /** See annotation */
    @Prop(name = "minecraftVersion", isSettings = false, preferPopulated = true, required = false, docComment = """
        Version of Minecraft to target
        """)
    public @NotNull String minecraftVersion = "1.7.10";

    /** See annotation */
    @Prop(name = "forgeVersion", isSettings = false, preferPopulated = true, required = false, docComment = """
        Version of Minecraft Forge to target
        """)
    public @NotNull String forgeVersion = "10.13.4.1614";

    /** See annotation */
    @Prop(name = "channel", isSettings = false, preferPopulated = true, required = false, docComment = """
        Specify an MCP channel for dependency deobfuscation and the deobfParams task.
        """)
    public @NotNull String channel = "stable";

    /** See annotation */
    @Prop(name = "mappingsVersion", isSettings = false, preferPopulated = true, required = false, docComment = """
        Specify an MCP mappings version for dependency deobfuscation and the deobfParams task.
        """)
    public @NotNull String mappingsVersion = "12";

    /** See annotation */
    @Prop(name = "remoteMappings", isSettings = false, preferPopulated = true, required = false, docComment = """
        Defines other MCP mappings for dependency deobfuscation.
        """)
    public @NotNull String remoteMappings = "https://raw.githubusercontent.com/MinecraftForge/FML/1.7.10/conf/";

    /** See annotation */
    @Prop(
        name = "developmentEnvironmentUserName",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            # Select a default username for testing your mod. You can always override this per-run by running
            `./gradlew runClient --username=AnotherPlayer`, or configuring this command in your IDE.
            """)
    public @NotNull String developmentEnvironmentUserName = "Developer";

    /** See annotation */
    @Prop(
        name = "enableModernJavaSyntax",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            Enables using modern Java syntax (up to version 17) via Jabel, while still targeting JVM 8.
            See https://github.com/bsideup/jabel for details on how this works.
            """)
    public boolean enableModernJavaSyntax = false;

    /** See annotation */
    @Prop(
        name = "enableGenericInjection",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            Enables injecting missing generics into the decompiled source code for a better coding experience.
            Turns most publicly visible List, Map, etc. into proper List<E>, Map<K, V> types.
            """)
    public boolean enableGenericInjection = false;

    /** See annotation */
    @Prop(
        name = "generateGradleTokenClass",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            Generate a class with a String field for the mod version named as defined below.
            If generateGradleTokenClass is empty or not missing, no such class will be generated.
            If gradleTokenVersion is empty or missing, the field will not be present in the class.
            """)
    public @NotNull String generateGradleTokenClass = "";

    /** See annotation */
    @Prop(name = "gradleTokenVersion", isSettings = false, preferPopulated = true, required = false, docComment = """
        Name of the token containing the project's current version to generate/replace.
        """)
    public @NotNull String gradleTokenVersion = "VERSION";

    /** See annotation */
    @Prop(
        name = "gradleTokenModId",
        isSettings = false,
        preferPopulated = true,
        required = false,
        hidden = true,
        docComment = """
            [DEPRECATED] Mod ID replacement token.
            """)
    public @NotNull String deprecatedGradleTokenModId = "";
    /** See annotation */
    @Prop(
        name = "gradleTokenModName",
        isSettings = false,
        preferPopulated = true,
        required = false,
        hidden = true,
        docComment = """
            [DEPRECATED] Mod name replacement token.
            """)
    public @NotNull String deprecatedGradleTokenModName = "";
    /** See annotation */
    @Prop(
        name = "gradleTokenModGroup",
        isSettings = false,
        preferPopulated = true,
        required = false,
        hidden = true,
        docComment = """
            [DEPRECATED] Mod Group replacement token.
            """)
    public @NotNull String deprecatedGradleTokenModGroup = "";

    /** See annotation */
    @Prop(
        name = "replaceGradleTokenInFile",
        isSettings = false,
        preferPopulated = false,
        required = false,
        docComment = """
            [DEPRECATED]
            Multiple source files can be defined here by providing a comma-separated list: Class1.java,Class2.java,Class3.java
            public static final String VERSION = "GRADLETOKEN_VERSION";
            The string's content will be replaced with your mod's version when compiled. You should use this to specify your mod's
            version in @Mod([...], version = VERSION, [...]).
            Leave these properties empty to skip individual token replacements.
            """)
    public @NotNull String replaceGradleTokenInFile = "";

    /** See annotation */
    @Prop(
        name = "apiPackage",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            In case your mod provides an API for other mods to implement you may declare its package here. Otherwise, you can
            leave this property empty.
            Example value: (apiPackage = api) + (modGroup = com.myname.mymodid) -> com.myname.mymodid.api
            """)
    public @NotNull String apiPackage = "";

    /** See annotation */
    @Prop(
        name = "accessTransformersFile",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            Specify the configuration file for Forge's access transformers here. It must be placed into /src/main/resources/META-INF/
            There can be multiple files in a space-separated list.
            Example value: mymodid_at.cfg nei_at.cfg
            """)
    public @NotNull String accessTransformersFile = "";

    /** See annotation */
    @Prop(name = "usesMixins", isSettings = false, preferPopulated = true, required = false, docComment = """
        Provides setup for Mixins if enabled. If you don't know what mixins are: Keep it disabled!
        """)
    public boolean usesMixins = false;

    /** See annotation */
    @Prop(name = "usesMixinsDebug", isSettings = false, preferPopulated = true, required = false, docComment = """
        Adds some debug arguments like verbose output and class export.
        """)
    public boolean usesMixinsDebug = false;

    /** See annotation */
    @Prop(name = "mixinPlugin", isSettings = false, preferPopulated = true, required = false, docComment = """
        Specify the location of your implementation of IMixinConfigPlugin. Leave it empty otherwise.
        """)
    public @NotNull String mixinPlugin = "";

    /** See annotation */
    @Prop(
        name = "mixinsPackage",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            Specify the package that contains all of your Mixins. You may only place Mixins in this package or the build will fail!
            """)
    public @NotNull String mixinsPackage = "";

    /** See annotation */
    @Prop(
        name = "coreModClass",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            Specify the core mod entry class if you use a core mod. This class must implement IFMLLoadingPlugin!
            This parameter is for legacy compatibility only
            Example value: (coreModClass = asm.FMLPlugin) + (modGroup = com.myname.mymodid) -> com.myname.mymodid.asm.FMLPlugin
            """)
    public @NotNull String coreModClass = "";

    /** See annotation */
    @Prop(
        name = "containsMixinsAndOrCoreModOnly",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            If your project is only a consolidation of mixins or a core mod and does NOT contain a 'normal' mod ( = some class
            that is annotated with @Mod) you want this to be true. When in doubt: leave it on false!
            """)
    public boolean containsMixinsAndOrCoreModOnly = false;

    /** See annotation */
    @Prop(name = "forceEnableMixins", isSettings = false, preferPopulated = true, required = false, docComment = """
        Enables Mixins even if this mod doesn't use them, useful if one of the dependencies uses mixins.
        """)
    public boolean forceEnableMixins = false;

    /** See annotation */
    @Prop(
        name = "usesShadowedDependencies",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            If enabled, you may use 'shadowCompile' for dependencies. They will be integrated into your jar. It is your
            responsibility to check the license and request permission for distribution if required.
            """)
    public boolean usesShadowedDependencies = false;
    /** See annotation */
    @Prop(
        name = "minimizeShadowedDependencies",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            If disabled, won't remove unused classes from shadowed dependencies. Some libraries use reflection to access
            their own classes, making the minimization unreliable.
            """)
    public boolean minimizeShadowedDependencies = true;
    /** See annotation */
    @Prop(
        name = "relocateShadowedDependencies",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            If disabled, won't rename the shadowed classes.
            """)
    public boolean relocateShadowedDependencies = true;

    /** See annotation */
    @Prop(
        name = "includeWellKnownRepositories",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            Adds the GTNH maven, CurseMaven, IC2/Player maven, and some more well-known 1.7.10 repositories.
            """)
    public boolean includeWellKnownRepositories = true;

    /** See annotation */
    @Prop(
        name = "usesMavenPublishing",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            Change these to your Maven coordinates if you want to publish to a custom Maven repository instead of the default GTNH Maven.
            Authenticate with the MAVEN_USER and MAVEN_PASSWORD environment variables.
            If you need a more complex setup disable maven publishing here and add a publishing repository to addon.gradle.
            """)
    public boolean usesMavenPublishing = true;

    /** See annotation */
    @Prop(name = "mavenPublishUrl", isSettings = false, preferPopulated = false, required = false, docComment = """
        Maven repository to publish the mod to.
        """)
    public @NotNull String mavenPublishUrl = GTNHConstants.GTNH_MAVEN_RELEASES_REPOSITORY;

    /** See annotation */
    @Prop(
        name = "modrinthProjectId",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            Publishing to Modrinth requires you to set the MODRINTH_TOKEN environment variable to your current Modrinth API token.

            The project's ID on Modrinth. Can be either the slug or the ID.
            Leave this empty if you don't want to publish to Modrinth.
            """)
    public @NotNull String modrinthProjectId = "";

    /** See annotation */
    @Prop(name = "modrinthRelations", isSettings = false, preferPopulated = true, required = false, docComment = """
        The project's relations on Modrinth. You can use this to refer to other projects on Modrinth.
        Syntax: scope1-type1:name1;scope2-type2:name2;...
        Where scope can be one of [required, optional, incompatible, embedded],
              type can be one of [project, version],
              and the name is the Modrinth project or version slug/id of the other mod.
        Example: required-project:fplib;optional-project:gasstation;incompatible-project:gregtech
        Note: GTNH Mixins is automatically set as a required dependency if usesMixins = true
        """)
    public @NotNull String modrinthRelations = "";

    /** See annotation */
    @Prop(
        name = "curseForgeProjectId",
        isSettings = false,
        preferPopulated = true,
        required = false,
        docComment = """
            Publishing to CurseForge requires you to set the CURSEFORGE_TOKEN environment variable to one of your CurseForge API tokens.

            The project's numeric ID on CurseForge. You can find this in the About Project box.
            Leave this empty if you don't want to publish on CurseForge.
            """)
    public @NotNull String curseForgeProjectId = "";

    /** See annotation */
    @Prop(name = "curseForgeRelations", isSettings = false, preferPopulated = true, required = false, docComment = """
        The project's relations on CurseForge. You can use this to refer to other projects on CurseForge.
        Syntax: type1:name1;type2:name2;...
        Where type can be one of [requiredDependency, embeddedLibrary, optionalDependency, tool, incompatible],
              and the name is the CurseForge project slug of the other mod.
        Example: requiredDependency:railcraft;embeddedLibrary:cofhlib;incompatible:buildcraft
        Note: UniMixins is automatically set as a required dependency if usesMixins = true.
        """)
    public @NotNull String curseForgeRelations = "";

    /** See annotation */
    @Prop(
        name = "customArchiveBaseName",
        isSettings = false,
        preferPopulated = false,
        required = false,
        docComment = """
            Optional parameter to customize the produced artifacts. Use this to preserve artifact naming when migrating older
            projects. New projects should not use this parameter.
            """)
    public @NotNull String customArchiveBaseName = "";

    /** See annotation */
    @Prop(name = "versionPattern", isSettings = false, preferPopulated = false, required = false, docComment = """
        Optional parameter to have the build automatically fail if an illegal version is used.
        This can be useful if you e.g. only want to allow versions in the form of '1.1.xxx'.
        The check is ONLY performed if the version is a git tag.
        Note: the specified string must be escaped, so e.g. 1\\\\.1\\\\.\\\\d+ instead of 1\\.1\\.\\d+
        """)
    public @NotNull String versionPattern = "";

    /** See annotation */
    @Prop(
        name = "noPublishedSources",
        isSettings = false,
        preferPopulated = false,
        required = false,
        defaultInComment = "true",
        docComment = """
            Uncomment to prevent the source code from being published.
            """)
    public boolean noPublishedSources = false;

    /** See annotation */
    @Prop(
        name = "disableSpotless",
        isSettings = false,
        preferPopulated = false,
        required = false,
        defaultInComment = "true",
        docComment = """
            Uncomment this to disable Spotless checks.
            This should only be uncommented to keep it easier to sync with upstream/other forks.
            That is, if there is no other active fork/upstream, NEVER change this.
            """)
    public boolean disableSpotless = false;

    /** See annotation */
    @Prop(
        name = "disableCheckstyle",
        isSettings = false,
        preferPopulated = false,
        required = false,
        defaultInComment = "true",
        docComment = """
            Uncomment this to disable Checkstyle checks (currently wildcard import check).
            """)
    public boolean disableCheckstyle = false;

    /** See annotation */
    @Prop(
        name = "ideaOverrideBuildType",
        isSettings = false,
        preferPopulated = false,
        required = false,
        defaultInComment = "idea",
        docComment = """
            Override the IDEA build type. Valid values are: "" (leave blank, do not override), "idea" (force use native IDEA build), "gradle"
            (force use delegated build).
            This is meant to be set in $HOME/.gradle/gradle.properties.
            e.g. add "systemProp.org.gradle.project.ideaOverrideBuildType=idea" will override the build type to be native build.
            WARNING: If you do use this option, it will overwrite whatever you have in your existing projects. This might not be what you want!
            Usually there is no need to uncomment this here as other developers do not necessarily use the same build type as you.
            """)
    public @NotNull String ideaOverrideBuildType = "";

    /** See annotation */
    @Prop(
        name = "ideaCheckSpotlessOnBuild",
        isSettings = false,
        preferPopulated = false,
        required = false,
        docComment = """
            Whether IDEA should run spotless checks when pressing the Build button.
            This is meant to be set in $HOME/.gradle/gradle.properties.
            """)
    public boolean ideaCheckSpotlessOnBuild = true;
    // </editor-fold>
    // </editor-fold>

    // API
    /** Fills all properties with default values. */
    public PropertiesConfiguration() {}

    /**
     * Separate factory functions for {@link PropertiesConfiguration} construction to allow isolated loading of the
     * parent class.
     */
    public static class GradleUtils {

        /**
         * Fills properties from the root directory's gradle.properties file and command line "-P" properties.
         *
         * @param settings The Gradle {@link Settings} object, from a settings plugin
         *                 {@link org.gradle.api.Plugin#apply(Object)} parameter
         * @return The initialized properties
         */
        public static PropertiesConfiguration makePropertiesFrom(final Settings settings) {
            final PropertiesConfiguration self = new PropertiesConfiguration();
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
            self.initFromProperties(props, null);
            return self;
        }

        /**
         * Fills properties from the current project's state.
         *
         * @param project The Gradle {@link Project} to read the properties from.
         * @return The initialized properties
         */
        public static PropertiesConfiguration makePropertiesFrom(final Project project) {
            final PropertiesConfiguration self = new PropertiesConfiguration();
            self.initFromProperties(
                project.getProperties(),
                project.getExtensions()
                    .getExtraProperties()::set);
            return self;
        }
    }

    /**
     * Initializes the properties based on the given data
     *
     * @param props     Input properties
     * @param onMissing Function to call if a property was found to be missing
     */
    public void initFromProperties(Map<?, ?> props, BiConsumer<String, Object> onMissing) {
        final Field[] fields = getClass().getDeclaredFields();
        for (final Field field : fields) {
            final Prop prop = field.getAnnotation(Prop.class);
            if (prop == null) {
                continue;
            }
            final String key = prop.name();
            final Object value = props.getOrDefault(key, null);
            try {
                if (value == null) {
                    if (prop.required()) {
                        throw new IllegalArgumentException(
                            "Required gradle property " + key + " is missing from project properties!");
                    }
                    if (onMissing != null) {
                        onMissing.accept(key, field.get(this));
                    }
                    continue;
                }
                String strValue = value.toString();
                if (strValue == null) {
                    strValue = "";
                } else {
                    strValue = strValue.trim();
                }
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

    private static final Pattern BLOWDRYER_SETTINGS_PATTERN = Pattern
        .compile("'GTNewHorizons/ExampleMod1\\.7\\.10'\\s*,\\s*'tag'\\s*,\\s*'([^']+)'\\s*\\)");

    /**
     * @param settingsPath   Path to the settings.gradle file
     * @param originalValues The old parsed properties
     * @return An updated properties file contents
     * @throws Throwable for convenience
     */
    public String generateUpdatedProperties(Path settingsPath, Map<String, String> originalValues) throws Throwable {
        final String settingsContents = new String(Files.readAllBytes(settingsPath), StandardCharsets.UTF_8);

        // Migrate from pre-GTNHGradle buildscript
        final Matcher blowdryerMatch = BLOWDRYER_SETTINGS_PATTERN.matcher(settingsContents);
        if (blowdryerMatch.find()) {
            final String group = blowdryerMatch.group(1);
            originalValues.put("gtnh.settings.blowdryerTag", group);
            System.out
                .println("Found old settings blowdryer tag pointing to " + group + ", migrating to gradle.properties");
        }

        final StringBuilder sb = new StringBuilder();
        final String newline = System.lineSeparator();

        try {
            final Field[] fields = getClass().getDeclaredFields();
            for (final Field field : fields) {
                final Prop prop = field.getAnnotation(Prop.class);
                if (prop == null) {
                    continue;
                }
                final String key = prop.name();
                final Object defaultValue = field.get(this);
                final String originalValue = originalValues.remove(key);

                if (originalValue == null && defaultValue == null) {
                    continue;
                }
                if (originalValue == null && prop.hidden()) {
                    continue;
                }

                final String[] docComment = prop.docComment()
                    .trim()
                    .split("\n");
                for (final String docLine : docComment) {
                    sb.append("# ");
                    sb.append(docLine);
                    sb.append(newline);
                }
                if (originalValue == null && !prop.preferPopulated()) {
                    sb.append("# ");
                }
                sb.append(key);
                sb.append(" =");
                final String valueToPrint;
                if (originalValue != null) {
                    valueToPrint = originalValue;
                } else if (!prop.preferPopulated() && !prop.defaultInComment()
                    .equals("!")) {
                        valueToPrint = prop.defaultInComment();
                    } else {
                        valueToPrint = defaultValue.toString();
                    }
                if (!valueToPrint.isEmpty()) {
                    sb.append(' ');
                    sb.append(valueToPrint);
                }
                sb.append(newline);
                sb.append(newline);
            }
            if (!originalValues.isEmpty()) {
                sb.append("# Non-GTNH properties\n");
                for (final Map.Entry<String, String> entry : originalValues.entrySet()) {
                    sb.append(entry.getKey());
                    sb.append(" = ");
                    sb.append(entry.getValue());
                    sb.append(newline);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }

        return sb.toString();
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
        boolean isSettings() default false;

        /** @return Should the property's value be frozen in the properties file on plugin update? */
        boolean preferPopulated() default true;

        /** @return Should a missing value for this property raise an error? */
        boolean required() default false;

        /** @return Should the property be hidden from properties if not already defined? */
        boolean hidden() default false;

        /** @return The default value to put in a commented-out version of this property, default to actual default. */
        @NotNull
        String defaultInComment() default "!";

        /** @return User documentation for the property */
        @NotNull
        String docComment() default "";
    }
}
