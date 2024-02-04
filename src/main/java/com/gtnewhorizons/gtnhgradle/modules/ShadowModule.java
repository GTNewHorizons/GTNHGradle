package com.gtnewhorizons.gtnhgradle.modules;

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import com.google.common.collect.ImmutableList;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;

/** Shadowed dependency support */
public class ShadowModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.usesShadowedDependencies;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        project.getPlugins()
            .apply(ShadowPlugin.class);

        final ConfigurationContainer cfgs = project.getConfigurations();
        final TaskContainer tasks = project.getTasks();

        final Configuration shadowRuntimeElements = cfgs.getByName("shadowRuntimeElements");
        final Configuration shadowImplementation = cfgs.getByName("shadowImplementation");
        final Configuration shadeCompile = cfgs.getByName("shadeCompile");
        final Configuration shadowCompile = cfgs.getByName("shadowCompile");

        for (final String config : ImmutableList
            .of("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")) {
            cfgs.getByName(config)
                .extendsFrom(shadowImplementation, shadeCompile, shadowCompile);
        }

        final TaskProvider<ShadowJar> shadowJar = tasks.named("shadowJar", ShadowJar.class);
        shadowJar.configure(sj -> {
            if (gtnh.configuration.minimizeShadowedDependencies) {
                sj.minimize();
            }
            sj.setConfigurations(ImmutableList.of(shadowImplementation, shadeCompile, shadowCompile));
            sj.getArchiveClassifier()
                .set("dev");
            if (gtnh.configuration.relocateShadowedDependencies) {
                sj.setRelocationPrefix(gtnh.configuration.modGroup + ".shadow");
                sj.setEnableRelocation(true);
            }
        });
        for (final String outgoingConfig : ImmutableList.of("runtimeElements", "apiElements")) {
            final Configuration outgoing = cfgs.getByName(outgoingConfig);
            outgoing.getOutgoing()
                .getArtifacts()
                .clear();
            outgoing.getOutgoing()
                .artifact(shadowJar);
        }
        tasks.named("jar", Jar.class)
            .configure(
                j -> {
                    j.getArchiveClassifier()
                        .set("dev-preshadow");
                });
        tasks.named("reobfJar", ReobfuscatedJar.class)
            .configure(
                j -> {
                    j.getInputJar()
                        .set(shadowJar.flatMap(AbstractArchiveTask::getArchiveFile));
                });

        final AdhocComponentWithVariants javaComponent = (AdhocComponentWithVariants) project.getComponents()
            .named("java")
            .get();
        javaComponent.withVariantsFromConfiguration(shadowRuntimeElements, ConfigurationVariantDetails::skip);

        project.getExtensions()
            .getExtraProperties()
            .set("publishableDevJar", shadowJar);
    }
}
