
rootProject.name = "GTNHGradle"

pluginManagement {
  repositories {
    maven {
      // RetroFuturaGradle
      name = "GTNH Maven"
      url = uri("https://nexus.gtnewhorizons.com/repository/public/")
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
  // Automatic toolchain provisioning
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
