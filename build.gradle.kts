plugins {
    `java-gradle-plugin`
    id("com.palantir.git-version") version "3.0.0"
    `maven-publish`
    id("com.diffplug.spotless") version "6.25.0"
    id("com.github.gmazzo.buildconfig") version "5.3.5"
}

val gitVersion: groovy.lang.Closure<String> by extra

group = "com.gtnewhorizons"
val detectedVersion: String = System.getenv("VERSION") ?: gitVersion()
version = detectedVersion

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {}

repositories {
    maven {
        name = "gtnh"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
    }
    mavenCentral()
    gradlePluginPortal()
}

fun pluginDep(name: String, version: String): String {
    return "${name}:${name}.gradle.plugin:${version}"
}

dependencies {
    annotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:1.0.1")
    testAnnotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:1.0.1")
    compileOnly("com.github.bsideup.jabel:jabel-javac-plugin:1.0.1") { isTransitive = false }
    // workaround for https://github.com/bsideup/jabel/issues/174
    annotationProcessor("net.java.dev.jna:jna-platform:5.13.0")

    // All these plugins will be present in the classpath of the project using our plugin, but not activated until explicitly applied
    api(pluginDep("com.gtnewhorizons.retrofuturagradle","1.4.2"))

    // Settings plugins
    api(pluginDep("com.diffplug.blowdryerSetup", "1.7.1"))
    api(pluginDep("org.gradle.toolchains.foojay-resolver-convention", "0.9.0"))

    // Project plugins
    api(pluginDep("com.github.johnrengelman.shadow", "8.1.1"))
    api(pluginDep("com.palantir.git-version", "3.0.0"))
    api(pluginDep("org.jetbrains.gradle.plugin.idea-ext", "1.1.8"))
    api(pluginDep("org.jetbrains.kotlin.jvm", "2.1.0"))
    api(pluginDep("org.jetbrains.kotlin.kapt", "2.1.0"))
    api(pluginDep("com.google.devtools.ksp", "2.1.0-1.0.29"))
    api(pluginDep("org.ajoberstar.grgit", "4.1.1")) // 4.1.1 is the last jvm8 supporting version, unused, available for addon.gradle
    api(pluginDep("io.github.goooler.shadow", "8.1.7"))
    api(pluginDep("de.undercouch.download", "5.6.0"))
    api(pluginDep("com.github.gmazzo.buildconfig", "3.1.0")) // Unused, available for addon.gradle
    api(pluginDep("com.modrinth.minotaur", "2.8.7"))
    api(pluginDep("net.darkhax.curseforgegradle", "1.1.24"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    plugins {
        website.set("https://github.com/GTNewHorizons/GTNHGradle")
        vcsUrl.set("https://github.com/GTNewHorizons/GTNHGradle.git")
        isAutomatedPublishing = false
        create("gtnhGradle") {
            id = "com.gtnewhorizons.gtnhgradle"
            implementationClass = "com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin"
            displayName = "GTNHGradle"
            description = "Shared buildscript logic for all GTNH mods and some other 1.7.10 mods"
            tags.set(listOf("minecraft", "modding"))
        }
        create("gtnhConvention") {
            id = "com.gtnewhorizons.gtnhconvention"
            implementationClass = "com.gtnewhorizons.gtnhgradle.GTNHConventionPlugin"
            displayName = "GTNHConvention"
            description = "Shared buildscript logic for all GTNH mods and some other 1.7.10 mods - automatically applies all features"
            tags.set(listOf("minecraft", "modding"))
        }
        create("gtnhSettingsConvention") {
            id = "com.gtnewhorizons.gtnhsettingsconvention"
            implementationClass = "com.gtnewhorizons.gtnhgradle.GTNHSettingsConventionPlugin"
            displayName = "GTNHConvention"
            description = "Shared Settings logic for all GTNH mods and some other 1.7.10 mods"
            tags.set(listOf("minecraft", "modding"))
        }
    }
}

// Spotless autoformatter
// See https://github.com/diffplug/spotless/tree/main/plugin-gradle
// Can be locally toggled via spotless:off/spotless:on comments
spotless {
    encoding("UTF-8")

    format ("misc") {
        target(".gitignore")

        trimTrailingWhitespace()
        indentWithSpaces(4)
        endWithNewline()
    }
    java {
        target("src/*/java/**/*.java", "src/*/scala/**/*.java")

        toggleOffOn()
        removeUnusedImports()
        trimTrailingWhitespace()
        eclipse("4.19").configFile("spotless.eclipseformat.xml")
    }
}

buildConfig {
    useJavaOutput()
    this.packageName = "com.gtnewhorizons.gtnhgradle"
    buildConfigField("VERSION", detectedVersion)
}

// Enable Jabel for java 8 bytecode from java 17 sources
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
        vendor.set(JvmVendorSpec.AZUL)
    }
    withSourcesJar()
    withJavadocJar()
}
tasks.javadoc {
    javadocTool.set(javaToolchains.javadocToolFor {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.AZUL)
    })
    with(options as StandardJavadocDocletOptions) {
        links(
            "https://docs.gradle.org/${gradle.gradleVersion}/javadoc/",
            "https://docs.oracle.com/en/java/javase/17/docs/api/"
        )
    }
}
tasks.withType<JavaCompile> {
    sourceCompatibility = "17" // for the IDE support
    options.release.set(8)
    options.encoding = "UTF-8"

    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.AZUL)
    })
}

tasks.wrapper.configure {
    gradleVersion = "8.5"
    distributionType = Wrapper.DistributionType.ALL
}

configurations["functionalTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])
configurations["functionalTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["functionalTestAnnotationProcessor"].extendsFrom(configurations["testAnnotationProcessor"])

// Add a task to run the functional tests
val functionalTest by tasks.registering(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}

gradlePlugin.testSourceSets.add(functionalTestSourceSet)

tasks.check {
    // Run the functional tests as part of `check`
    dependsOn(functionalTest)
}

tasks.test {
    // Use JUnit Jupiter for unit tests.
    useJUnitPlatform()
    // Skip git-based versioning inside the tests
    environment("VERSION", "1.0.0")
}


publishing {
    publications {
        create<MavenPublication>("gtnhGradle") {
            artifactId = "gtnhgradle"
            from(components["java"])
        }
        // From org.gradle.plugin.devel.plugins.MavenPluginPublishPlugin.createMavenMarkerPublication
        for (declaration in gradlePlugin.plugins) {
            create<MavenPublication>(declaration.name + "PluginMarkerMaven") {
                artifactId = declaration.id + ".gradle.plugin"
                groupId = declaration.id
                pom {
                    name.set(declaration.displayName)
                    description.set(declaration.description)
                    withXml {
                        val root = asElement()
                        val document = root.ownerDocument
                        val dependencies = root.appendChild(document.createElement("dependencies"))
                        val dependency = dependencies.appendChild(document.createElement("dependency"))
                        val groupId = dependency.appendChild(document.createElement("groupId"))
                        groupId.textContent = project.group.toString()
                        val artifactId = dependency.appendChild(document.createElement("artifactId"))
                        artifactId.textContent = "gtnhgradle"
                        val version = dependency.appendChild(document.createElement("version"))
                        version.textContent = project.version.toString()
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = uri("https://nexus.gtnewhorizons.com/repository/releases/")
            credentials {
                username = System.getenv("MAVEN_USER") ?: "NONE"
                password = System.getenv("MAVEN_PASSWORD") ?: "NONE"
            }
        }
    }
}

