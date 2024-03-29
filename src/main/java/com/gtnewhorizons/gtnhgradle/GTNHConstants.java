package com.gtnewhorizons.gtnhgradle;

import org.jetbrains.annotations.NotNull;

/** Various useful constants related to GTNH projects. */
@SuppressWarnings("unused")
public class GTNHConstants {

    /** URL of the "public" GTNH Maven repository, contains all our and mirrored packages */
    public static final @NotNull String GTNH_MAVEN_PUBLIC_REPOSITORY = "https://nexus.gtnewhorizons.com/repository/public/";
    /** URL of the "releases" GTNH Maven repository, contains only our mod packages */
    public static final @NotNull String GTNH_MAVEN_RELEASES_REPOSITORY = "https://nexus.gtnewhorizons.com/repository/releases/";
    /** The current Checkstyle configuration file contents used */
    public static final @NotNull String CHECKSTYLE_CONFIG = """
        <!DOCTYPE module PUBLIC
          "-//Puppy Crawl//DTD Check Configuration 1.3//EN"
          "http://www.puppycrawl.com/dtds/configuration_1_3.dtd">
        <module name="Checker">
          <module name="TreeWalker">
            <!-- Use CHECKSTYLE:OFF and CHECKSTYLE:ON comments to suppress checkstyle lints in a block -->
            <module name="SuppressionCommentFilter"/>
            <module name="AvoidStarImport">
              <!-- Allow static wildcard imports for cases like Opcodes and LWJGL classes, these don't get created accidentally by the IDE -->
              <property name="allowStaticMemberImports" value="true"/>
            </module>
          </module>
        </module>
        """;

    /** The default directory for Java sources */
    public static final @NotNull String JAVA_SOURCES_DIR = "src/main/java/";
    /** The default directory for Scala sources */
    public static final @NotNull String SCALA_SOURCES_DIR = "src/main/scala/";
    /** The default directory for Kotlin sources */
    public static final @NotNull String KOTLIN_SOURCES_DIR = "src/main/kotlin/";
    /** Name of the String property defined on the project that contains the detected version */
    public static final @NotNull String MOD_VERSION_PROPERTY = "modVersion";
    /** Name of the String property defined on the project that contains the settings.gradle File instance */
    public static final @NotNull String SETTINGS_GRADLE_FILE_PROPERTY = "gtnhGradleSettingsLocation";

}
