package com.gtnewhorizons.gtnhgradle.modules;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.mcp.MCPTasks;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Configures the Java/Scala/Kotlin toolchain settings */
public abstract class ToolchainModule implements GTNHModule {

    /** @return Gradle-provided */
    @Inject
    public abstract JavaToolchainService getToolchainService();

    /** For dependency injection */
    @Inject
    public ToolchainModule() {}

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleToolchain;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final TaskContainer tasks = project.getTasks();
        final JavaPluginExtension java = project.getExtensions()
            .getByType(JavaPluginExtension.class);

        // Set up Java
        final int javaVersion = gtnh.configuration.enableModernJavaSyntax ? 17 : 8;
        java.getToolchain()
            .getVendor()
            .set(JvmVendorSpec.AZUL);
        java.getToolchain()
            .getLanguageVersion()
            .set(JavaLanguageVersion.of(javaVersion));
        if (!gtnh.configuration.noPublishedSources) {
            java.withSourcesJar();
        }
        tasks.withType(JavaCompile.class)
            .configureEach(
                jc -> {
                    jc.getOptions()
                        .setEncoding(StandardCharsets.UTF_8.name());
                });
        if (gtnh.configuration.enableModernJavaSyntax) {
            project.getRepositories()
                .exclusiveContent(ecr -> {
                    ecr.forRepositories(
                        project.getRepositories()
                            .mavenCentral(mar -> { mar.setName("mavenCentral_java8Unsupported"); }));
                    ecr.filter(cfg -> { cfg.includeGroup("me.eigenraven.java8unsupported"); });
                });
            final DependencyHandler deps = project.getDependencies();
            deps.add(
                JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                "com.github.bsideup.jabel:jabel-javac-plugin:1.0.1");
            ((ModuleDependency) deps
                .add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, "com.github.bsideup.jabel:jabel-javac-plugin:1.0.1"))
                    .setTransitive(false);
            // Workaround for https://github.com/bsideup/jabel/issues/174
            deps.add(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME, "net.java.dev.jna:jna-platform:5.13.0");
            // Allow using jdk.unsupported classes like sun.misc.Unsafe in the compiled code, working around
            // JDK-8206937.
            deps.add(
                MCPTasks.PATCHED_MINECRAFT_CONFIGURATION_NAME,
                "me.eigenraven.java8unsupported:java-8-unsupported-shim:1.0.0");

            final Set<String> doNotUpgrade = ImmutableSet.of("compileMcLauncherJava", "compilePatchedMcJava");
            final Provider<JvmVendorSpec> vendor = java.getToolchain()
                .getVendor();
            final Provider<JavaCompiler> jabelCompiler = getToolchainService().compilerFor(jts -> {
                jts.getLanguageVersion()
                    .set(JavaLanguageVersion.of(17));
                jts.getVendor()
                    .set(vendor);
            });
            tasks.withType(JavaCompile.class)
                .configureEach(jc -> {
                    if (doNotUpgrade.contains(jc.getName())) {
                        return;
                    }
                    jc.setSourceCompatibility("17");
                    jc.getOptions()
                        .getRelease()
                        .set(8);
                    jc.getJavaCompiler()
                        .set(jabelCompiler);
                });
        }

        // Set up Scala
        tasks.withType(ScalaCompile.class)
            .configureEach(
                sc -> {
                    sc.getOptions()
                        .setEncoding(StandardCharsets.UTF_8.name());
                });

        // Set up Kotlin if enabled
        project.getPlugins()
            .withId("org.jetbrains.kotlin.jvm", plugin -> {
                final KotlinTopLevelExtension kotlin = (KotlinTopLevelExtension) project.getExtensions()
                    .getByName("kotlin");
                kotlin.jvmToolchain(8);
                final Set<String> disabledKotlinTasks = ImmutableSet.of(
                    "kaptGenerateStubsMcLauncherKotlin",
                    "kaptGenerateStubsPatchedMcKotlin",
                    "kaptGenerateStubsInjectedTagsKotlin",
                    "compileMcLauncherKotlin",
                    "compilePatchedMcKotlin",
                    "compileInjectedTagsKotlin",
                    "kaptMcLauncherKotlin",
                    "kaptPatchedMcKotlin",
                    "kaptInjectedTagsKotlin",
                    "kspMcLauncherKotlin",
                    "kspPatchedMcKotlin",
                    "kspInjectedTagsKotlin");
                tasks.configureEach(t -> {
                    if (disabledKotlinTasks.contains(t.getName())) {
                        t.setEnabled(false);
                    }
                });
            });
    }
}
