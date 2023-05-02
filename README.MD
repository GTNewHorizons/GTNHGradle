# GTNH Gradle plugin

This plugin is the container for most of our shared buildscript logic for all GTNH mods and some other 1.7.10 mods.
The original buildscript grew to hundreds of lines of Groovy code, using this plugin we can use regular Java classes to compartmentalize that logic and make it easier to extend and update.

## Navigating the source code

The entrypoint for the plugin is in the [GTNHGradlePlugin](src/main/java/com/gtnewhorizons/gtnhgradle/GTNHGradlePlugin.java) class.
The main plugin class applies the plugin dependencies of GTNHGradle, including RFG, Maven publishing and the Download task,
and then registers the `gtnhGradle` extension object which can be used to activate individual modules of this plugin.

`test` tests make sure that applying the plugin works correctly in a simple Gradle setup.
Bulk of the testing is done in `functionalTest` which uses [Gradle TestKit](https://docs.gradle.org/8.1.1/userguide/test_kit.html) to test entire workflows in sandboxed Gradle environments.
