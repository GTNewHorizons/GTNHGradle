
rootProject.name = "GTNHGradle"

pluginManagement {
  repositories {
    maven {
      // RetroFuturaGradle
      name = "GTNH Maven"
      url = uri("http://jenkins.usrv.eu:8081/nexus/content/groups/public/")
      isAllowInsecureProtocol = true
      mavenContent {
        includeGroup("com.gtnewhorizons")
        includeGroup("com.gtnewhorizons.retrofuturagradle")
        includeGroup("com.gtnewhorizons.gtnhgradle")
      }
    }
    gradlePluginPortal()
    mavenCentral()
    mavenLocal()
  }
}

plugins {
  // Automatic toolchain provisioning
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.4.0"
}
