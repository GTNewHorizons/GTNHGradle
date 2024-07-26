package com.gtnewhorizons.gtnhgradle;

import org.jetbrains.annotations.NotNull;

/** Collections of version numbers and other similar "constants" that may need frequent updating. */
// Developer note: Do not depend on any other classes here, this class is parsed during the update process for
// collecting metadata about the next version.
public class UpdateableConstants {

    /** Latest Gradle version to update to. */
    // https://github.com/gradle/gradle/releases
    @SuppressWarnings("unused") // Used via reflection
    public static final @NotNull String NEWEST_GRADLE_VERSION = "8.9";

    /** Latest tag of ExampleMod with blowdryer settings */
    // https://github.com/GTNewHorizons/ExampleMod1.7.10/releases
    public static final @NotNull String NEWEST_BLOWDRYER_TAG = "0.2.2";

    /** Latest version of UniMixins */
    public static final String NEWEST_UNIMIXINS = "io.github.legacymoddingmc:unimixins:0.1.17";

    /** Latest version of Jabel for modern Java support */
    public static final @NotNull String NEWEST_JABEL = "com.github.bsideup.jabel:jabel-javac-plugin:1.0.1";
    /** Latest version of GTNHLib for modern Java support */
    // https://github.com/GTNewHorizons/GTNHLib/releases
    public static final @NotNull String NEWEST_GTNH_LIB = "com.github.GTNewHorizons:GTNHLib:0.3.3";
    /** Latest version of GTNHLib for modern Java support */
    // https://github.com/GTNewHorizons/lwjgl3ify/releases
    public static final @NotNull String NEWEST_LWJGL3IFY = "com.github.GTNewHorizons:lwjgl3ify:2.1.1";
    /** Latest version of GTNHLib for modern Java support */
    // https://github.com/GTNewHorizons/Hodgepodge/releases
    public static final @NotNull String NEWEST_HODGEPODGE = "com.github.GTNewHorizons:Hodgepodge:2.5.38";
    /** Latest version of LWJGL3 for modern Java support */
    // https://github.com/LWJGL/lwjgl3/releases - but check what latest Minecraft uses too
    public static final @NotNull String NEWEST_LWJGL3 = "3.3.3";
    /** Latest version of Spotless compatible with Java 8 */
    // https://github.com/diffplug/spotless/blob/main/plugin-gradle/CHANGES.md
    public static final @NotNull String NEWEST_SPOTLESS_JAVA8 = "6.13.0";
    /** Latest version of Spotless compatible with modern Java versions */
    // https://github.com/diffplug/spotless/blob/main/plugin-gradle/CHANGES.md
    public static final @NotNull String NEWEST_SPOTLESS = "6.25.0";

    /** Latest HotSwapAgent release jar URL */
    // https://github.com/HotswapProjects/HotswapAgent/releases
    public static final @NotNull String NEWEST_HOTSWAPAGENT = "https://github.com/HotswapProjects/HotswapAgent/releases/download/1.4.2-SNAPSHOT/hotswap-agent-1.4.2-SNAPSHOT.jar";

    /** Specifier for the latest GTNHGradle version to update to */
    // Only update once a new major change is made, ensure a backport to the previous major's updater is released first.
    public static final @NotNull String NEWEST_GTNHGRADLE_SPEC = "com.gtnewhorizons:gtnhgradle:1.+";

    /** Latest Industrial Craft 2 version */
    public static final @NotNull String NEWEST_IC2_SPEC = "curse.maven:ic2-242638:2353971";
}
