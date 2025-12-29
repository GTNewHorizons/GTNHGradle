package com.gtnewhorizons.gtnhgradle.modules;

import com.github.jengelman.gradle.plugins.shadow.ShadowExtension;
import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import com.gtnewhorizons.retrofuturagradle.shadow.com.google.common.collect.ImmutableList;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;

/** Shadowed dependency support */
public class ShadowModule implements GTNHModule {

    @Override
    public boolean isEnabled(GTNHGradlePlugin.@NotNull GTNHExtension gtnh) {
        return gtnh.configuration.usesShadowedDependencies;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        project.getPlugins()
            .apply(ShadowPlugin.class);

        final ShadowExtension shadowExt = project.getExtensions()
            .getByType(ShadowExtension.class);
        shadowExt.getAddShadowVariantIntoJavaComponent()
            .set(false);
        shadowExt.getAddTargetJvmVersionAttribute()
            .set(false);

        final ConfigurationContainer cfgs = project.getConfigurations();
        final TaskContainer tasks = project.getTasks();

        final Configuration shadowRuntimeElements = cfgs.getByName("shadowRuntimeElements");
        final Configuration shadowImplementation = cfgs.maybeCreate("shadowImplementation");
        final Configuration shadeCompile = cfgs.maybeCreate("shadeCompile");
        final Configuration shadowCompile = cfgs.maybeCreate("shadowCompile");
        for (final Configuration shadowConf : ImmutableList.of(shadowImplementation, shadeCompile, shadowCompile)) {
            shadowConf.setCanBeConsumed(false);
            shadowConf.setCanBeResolved(true);
        }

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
            sj.getConfigurations()
                .set(ImmutableList.of(shadowImplementation, shadeCompile, shadowCompile));
            // Default classifier - JVMDowngraderModule will override to "predowngrade" if needed
            sj.getArchiveClassifier()
                .set("dev");
            if (gtnh.configuration.relocateShadowedDependencies) {
                sj.getRelocationPrefix()
                    .set(gtnh.configuration.modGroup + ".shadow");
                sj.getEnableAutoRelocation()
                    .set(true);
            }

        });
        // jar is intermediate when shadow is enabled - shadowJar consumes it
        tasks.named("jar", Jar.class)
            .configure(
                j -> {
                    j.getArchiveClassifier()
                        .set("dev-preshadow");
                });

        for (final String outgoingConfig : ImmutableList.of("runtimeElements", "apiElements")) {
            final Configuration outgoing = cfgs.getByName(outgoingConfig);
            outgoing.getOutgoing()
                .getArtifacts()
                .clear();
            outgoing.getOutgoing()
                .artifact(shadowJar);
        }
        tasks.named("reobfJar", ReobfuscatedJar.class)
            .configure(j -> {
                j.getInputJar()
                    .set(shadowJar.flatMap(AbstractArchiveTask::getArchiveFile));
            });
    }
}
