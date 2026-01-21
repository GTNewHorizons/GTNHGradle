package com.gtnewhorizons.gtnhgradle;

import org.jetbrains.annotations.NotNull;

/** Collections of version numbers and other similar "constants" that may need frequent updating. */
// Developer note: Do not depend on any other classes here, this class is parsed during the update process for
// collecting metadata about the next version.
public class UpdateableConstants {

    /** Latest Gradle version to update to. */
    // https://github.com/gradle/gradle/releases
    @SuppressWarnings("unused") // Used via reflection
    public static final @NotNull String NEWEST_GRADLE_VERSION = "9.2.1";

    /** Latest tag of ExampleMod with blowdryer settings */
    // https://github.com/GTNewHorizons/ExampleMod1.7.10/releases
    public static final @NotNull String NEWEST_BLOWDRYER_TAG = "0.2.2";

    /** Latest version of UniMixins */
    // https://github.com/LegacyModdingMC/UniMixins/releases
    public static final String NEWEST_UNIMIXINS = "io.github.legacymoddingmc:unimixins:0.1.23";

    /** Latest version of Jabel for modern Java support */
    public static final @NotNull String NEWEST_JABEL = "com.github.GTNewHorizons:jabel-javac-plugin:1.0.2-GTNH";
    /** Jabel stubs for @Desugar annotation compatibility when using JVM Downgrader */
    public static final @NotNull String JABEL_STUBS = "com.github.GTNewHorizons:jabel-stubs:1.0.0";
    /** Latest version of GTNHLib for modern Java support */
    // https://github.com/GTNewHorizons/GTNHLib/releases
    public static final @NotNull String NEWEST_GTNH_LIB = "com.github.GTNewHorizons:GTNHLib:0.9.0";
    /** Latest version of GTNHLib for modern Java support */
    // https://github.com/GTNewHorizons/lwjgl3ify/releases
    public static final @NotNull String NEWEST_LWJGL3IFY = "com.github.GTNewHorizons:lwjgl3ify:3.0.7";
    /** Latest version of GTNHLib for modern Java support */
    // https://github.com/GTNewHorizons/Hodgepodge/releases
    public static final @NotNull String NEWEST_HODGEPODGE = "com.github.GTNewHorizons:Hodgepodge:2.7.39";
    /** Latest version of LWJGL3 for modern Java support */
    // https://github.com/GTNewHorizons/lwjgl3ify/blob/master/gradle/libs.versions.toml - but check what latest
    // Minecraft uses too
    public static final @NotNull String NEWEST_LWJGL3 = "3.4.0";

    /** Minimum GTNHLib version required for JVM Downgrader stub support */
    // When jvmDowngraderStubsProvider=gtnhlib, GTNHLib must provide the stubs at runtime
    public static final @NotNull String MIN_GTNHLIB_FOR_JVMDG_STUBS = "com.github.GTNewHorizons:GTNHLib:0.9.0";

    /** JVM Downgrader API/stubs version for shading - can be snapshot */
    // https://maven.wagyourtail.xyz/#/snapshots/xyz/wagyourtail/jvmdowngrader
    public static final @NotNull String JVMDG_VERSION = "1.3.5";

    /** JVM Downgrader group ID */
    public static final @NotNull String JVMDG_GROUP = "xyz.wagyourtail.jvmdowngrader";

    /** JVM Downgrader Java API artifact coordinates (without version/classifier) */
    public static final @NotNull String JVMDG_API_ARTIFACT = JVMDG_GROUP + ":jvmdowngrader-java-api";

    /** JVM Downgrader snapshot repository URL */
    public static final @NotNull String JVMDG_SNAPSHOT_REPO = "https://maven.wagyourtail.xyz/snapshots";

    /**
     * Returns the full dependency notation for the downgraded JVMDG API jar.
     *
     * @param targetVersion The Java version to downgrade to (e.g., 8)
     * @return Dependency notation like "xyz.wagyourtail.jvmdowngrader:jvmdowngrader-java-api:1.3.5:downgraded-8"
     */
    public static @NotNull String jvmdgApiDependency(int targetVersion) {
        return JVMDG_API_ARTIFACT + ":" + JVMDG_VERSION + ":downgraded-" + targetVersion;
    }

    /** Minimum Lombok version required for Java 25+ bytecode support */
    // https://github.com/projectlombok/lombok/releases
    public static final @NotNull String MIN_LOMBOK_FOR_JAVA_25 = "1.18.40";

    /** Latest version of Spotless compatible with modern Java versions */
    // https://github.com/diffplug/spotless/blob/main/plugin-gradle/CHANGES.md
    public static final @NotNull String NEWEST_SPOTLESS = "8.1.0";
    /** Latest version of Checkstyle compatible with modern Java versions */
    // https://mvnrepository.com/artifact/com.puppycrawl.tools/checkstyle
    public static final @NotNull String NEWEST_CHECKSTYLE = "12.3.1";

    /** Latest HotSwapAgent release jar URL */
    // https://github.com/HotswapProjects/HotswapAgent/releases
    public static final @NotNull String NEWEST_HOTSWAPAGENT = "https://github.com/HotswapProjects/HotswapAgent/releases/download/RELEASE-2.0.1/hotswap-agent-2.0.1.jar";

    /** Specifier for the latest GTNHGradle version to update to */
    // Only update once a new major change is made, ensure a backport to the previous major's updater is released first.
    public static final @NotNull String NEWEST_GTNHGRADLE_SPEC = "com.gtnewhorizons:gtnhgradle:2.+";

    /** Latest Industrial Craft 2 version */
    public static final @NotNull String NEWEST_IC2_SPEC = "curse.maven:ic2-242638:2353971";
}
