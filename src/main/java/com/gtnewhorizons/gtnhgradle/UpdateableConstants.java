package com.gtnewhorizons.gtnhgradle;

import org.jetbrains.annotations.NotNull;

/** Collections of version numbers and other similar "constants" that may need frequent updating. */
public class UpdateableConstants {

    /** Latest tag of ExampleMod with blowdryer settings */
    // https://github.com/GTNewHorizons/ExampleMod1.7.10/releases
    public static final @NotNull String NEWEST_BLOWDRYER_TAG = "0.2.2";

    /** Latest version of UniMixins */
    public static final String NEWEST_UNIMIXINS = "io.github.legacymoddingmc:unimixins:0.1.15";

    /** Latest version of Jabel for modern Java support */
    public static final @NotNull String NEWEST_JABEL = "com.github.bsideup.jabel:jabel-javac-plugin:1.0.1";
    /** Latest version of GTNHLib for modern Java support */
    // https://github.com/GTNewHorizons/GTNHLib/releases
    public static final @NotNull String NEWEST_GTNH_LIB = "com.github.GTNewHorizons:GTNHLib:0.2.3";
    /** Latest version of GTNHLib for modern Java support */
    // https://github.com/GTNewHorizons/lwjgl3ify/releases
    public static final @NotNull String NEWEST_LWJGL3IFY = "com.github.GTNewHorizons:lwjgl3ify:1.5.14";
    /** Latest version of GTNHLib for modern Java support */
    // https://github.com/GTNewHorizons/Hodgepodge/releases
    public static final @NotNull String NEWEST_HODGEPODGE = "com.github.GTNewHorizons:Hodgepodge:2.4.19";
    /** Latest version of LWJGL3 for modern Java support */
    // https://github.com/LWJGL/lwjgl3/releases - but check what latest Minecraft uses too
    public static final @NotNull String NEWEST_LWJGL3 = "3.3.2";

    /** Latest HotSwapAgent release jar URL */
    // https://github.com/HotswapProjects/HotswapAgent/releases
    public static final @NotNull String NEWEST_HOTSWAPAGENT = "https://github.com/HotswapProjects/HotswapAgent/releases/download/1.4.2-SNAPSHOT/hotswap-agent-1.4.2-SNAPSHOT.jar";

}
