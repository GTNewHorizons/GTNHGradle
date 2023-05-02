plugins {
    `java-gradle-plugin`
    id("com.palantir.git-version") version "3.0.0"
    id("maven-publish")
    id("com.diffplug.spotless") version "6.12.0"
    id("com.github.gmazzo.buildconfig") version "3.1.0"
}

val gitVersion: groovy.lang.Closure<String> by extra

group = "com.gtnewhorizons"
version = gitVersion()

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {}

repositories {
    maven {
        name = "gtnh"
        isAllowInsecureProtocol = true
        url = uri("http://jenkins.usrv.eu:8081/nexus/content/groups/public/")
    }
    mavenCentral()
    gradlePluginPortal()
}

fun pluginDep(name: String, version: String): String {
    return "${name}:${name}.gradle.plugin:${version}"
}

dependencies {
    annotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:1.0.0")
    testAnnotationProcessor("com.github.bsideup.jabel:jabel-javac-plugin:1.0.0")
    compileOnly("com.github.bsideup.jabel:jabel-javac-plugin:1.0.0") { isTransitive = false }

    // All these plugins will be present in the classpath of the project using our plugin, but not activated until explicitly applied
    api(pluginDep("com.gtnewhorizons.retrofuturagradle","1.3.8"))
    api(pluginDep("com.github.johnrengelman.shadow", "8.1.1"))
    api(pluginDep("com.palantir.git-version", "3.0.0"))
    api(pluginDep("org.jetbrains.gradle.plugin.idea-ext", "1.1.7"))
    api(pluginDep("org.jetbrains.kotlin.jvm", "1.8.0"))
    api(pluginDep("org.jetbrains.kotlin.kapt", "1.8.0"))
    api(pluginDep("com.google.devtools.ksp", "1.8.0-1.0.9"))
    api(pluginDep("org.ajoberstar.grgit", "4.1.1")) // 4.1.1 is the last jvm8 supporting version, unused, available for addon.gradle
    api(pluginDep("com.github.johnrengelman.shadow", "8.1.1"))
    api(pluginDep("de.undercouch.download", "5.4.0"))
    api(pluginDep("com.github.gmazzo.buildconfig", "3.1.0")) // Unused, available for addon.gradle
    api(pluginDep("com.diffplug.spotless", "6.13.0")) // 6.13.0 is the last jvm8 supporting version
    api(pluginDep("com.modrinth.minotaur", "2.+"))
    api(pluginDep("com.matthewprenger.cursegradle", "1.4.0"))

    api(pluginDep("com.diffplug.spotless", "6.12.0")) {
        exclude("org.codehaus.groovy", "groovy")
        exclude("org.codehaus.groovy", "groovy-xml")
    }

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
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
        eclipse("4.19.0").configFile("spotless.eclipseformat.xml")
    }
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
    with((options as StandardJavadocDocletOptions)) {
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
}


publishing {
    publications {
        create<MavenPublication>("gtnhGradle") {
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
                        artifactId.textContent = project.name
                        val version = dependency.appendChild(document.createElement("version"))
                        version.textContent = project.version.toString()
                    }
                }
            }
        }
    }

    repositories {
        maven {
            url = uri("http://jenkins.usrv.eu:8081/nexus/content/repositories/releases")
            isAllowInsecureProtocol = true
            credentials {
                username = System.getenv("MAVEN_USER") ?: "NONE"
                password = System.getenv("MAVEN_PASSWORD") ?: "NONE"
            }
        }
    }
}

