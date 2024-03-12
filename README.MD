# GTNH Gradle plugin

This plugin is the container for most of our shared buildscript logic for all GTNH mods and some other 1.7.10 mods.
The original buildscript grew to hundreds of lines of Groovy code, using this plugin we can use regular Java classes to compartmentalize that logic and make it easier to extend and update.

See the [javadoc](https://www.gtnewhorizons.com/GTNHGradle/?version=master) - the entrypoint is the [plugin extension class](https://www.gtnewhorizons.com/GTNHGradle/master/javadoc/com/gtnewhorizons/gtnhgradle/GTNHGradlePlugin.GTNHExtension.html).

## Navigating the source code

The entrypoint for the plugin is in the [GTNHGradlePlugin](src/main/java/com/gtnewhorizons/gtnhgradle/GTNHGradlePlugin.java) class.
The main plugin class applies the plugin dependencies of GTNHGradle, including RFG, Maven publishing and the Download task,
and then registers the `gtnhGradle` extension object which can be used to activate individual modules of this plugin.

`test` tests make sure that applying the plugin works correctly in a simple Gradle setup.
Bulk of the testing is done in `functionalTest` which uses [Gradle TestKit](https://docs.gradle.org/8.1.1/userguide/test_kit.html) to test entire workflows in sandboxed Gradle environments.

## Updating from the previous buildscript

0. Make sure you're on the latest `master` commit and you pulled the recent repository changes ;-)
1. Copy over the `gradle/` directory and `build.gradle`, `gradlew` and `gradlew.bat` files from ExampleMod1.7.10 to the mod folder.
2. Update `settings.gradle` `pluginManagement{}` and `plugins{}` block to the following: (keep the old blowdryerSetup as-is)
   ```groovy
    pluginManagement {
        repositories {
            maven {
                // RetroFuturaGradle
                name "GTNH Maven"
                url "https://nexus.gtnewhorizons.com/repository/public/"
                mavenContent {
                    includeGroup("com.gtnewhorizons")
                    includeGroupByRegex("com\\.gtnewhorizons\\..+")
                }
            }
            gradlePluginPortal()
            mavenCentral()
            mavenLocal()
        }
    }

    plugins {
        id 'com.gtnewhorizons.gtnhsettingsconvention' version '1.0.7'
    }
   ```
3. Run the automated migration while temporarily ignoring the new blowdryer setup with `./gradlew updateBuildscript -Pgtnh.settings.blowdryerTag=MIGRATION-MAGIC`
4. Check the updated files for any obvious mistakes
5. Commit

## Disabling modules

GTNHGradle uses some hidden [properties](src/main/java/com/gtnewhorizons/gtnhgradle/PropertiesConfiguration.java) to disable full modules of the plugin.
Not every combination of toggles is supported, but here are a couple useful ones for non-GTNH projects:
```properties

# Disable git-based version detection, make sure to set project.version to a sensible value yourself
gtnh.modules.gitVersion = false

# Disable all GTNH code style checks (like Spotless autoformatting, CheckStyle rules, and any future additions)
gtnh.modules.codeStyle = false

# Disables lwjgl3ify-based utilities for working with and running with modern Java versions
gtnh.modules.modernJava = false

```
