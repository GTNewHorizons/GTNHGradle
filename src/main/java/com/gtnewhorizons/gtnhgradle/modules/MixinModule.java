package com.gtnewhorizons.gtnhgradle.modules;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.gtnhgradle.UpdateableConstants;
import com.gtnewhorizons.gtnhgradle.tasks.GenerateMixinAssetsTask;
import com.gtnewhorizons.retrofuturagradle.MinecraftExtension;
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;

/** Easy Unimixins support. */
public class MixinModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleMixin;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final MinecraftExtension minecraft = project.getExtensions()
            .getByType(MinecraftExtension.class);
        final ModUtils modUtils = project.getExtensions()
            .getByType(ModUtils.class);

        if (gtnh.configuration.usesMixins || gtnh.configuration.forceEnableMixins) {
            if (gtnh.configuration.usesMixinDebug) {
                minecraft.getExtraRunJvmArguments()
                    .addAll(
                        "-Dmixin.debug.countInjections=true",
                        "-Dmixin.debug.verbose=true",
                        "-Dmixin.debug.export=true");
            }
        }

        final String mixinProviderSpecNoClassifer = UpdateableConstants.NEWEST_UNIMIXINS;
        final String mixinProviderSpec = mixinProviderSpecNoClassifer + ":dev";
        project.getExtensions()
            .getExtraProperties()
            .set("mixinProviderSpec", mixinProviderSpec);

        final String mixingConfigRefMap = "mixins." + gtnh.configuration.modId + ".refmap.json";
        final String mixinSourceSetName = gtnh.configuration.separateMixinSourceSet.trim();
        final SourceSetContainer sourceSets = project.getExtensions()
            .getByType(JavaPluginExtension.class)
            .getSourceSets();
        final SourceSet mixinSourceSet;
        final SourceSet mainSourceSet = sourceSets.getByName("main");

        if (!mixinSourceSetName.isEmpty()) {
            mixinSourceSet = sourceSets.create(mixinSourceSetName);
            ConfigurableFileCollection classpath = project.getObjects()
                .fileCollection();
            classpath.from(mixinSourceSet.getCompileClasspath());
            classpath.from(mainSourceSet.getCompileClasspath());
            classpath.from(mainSourceSet.getOutput());
            mixinSourceSet.setCompileClasspath(classpath);
            classpath = project.getObjects()
                .fileCollection();
            classpath.from(mixinSourceSet.getRuntimeClasspath());
            classpath.from(mainSourceSet.getRuntimeClasspath());
            mixinSourceSet.setRuntimeClasspath(classpath);
            classpath = project.getObjects()
                .fileCollection();
            classpath.from(mixinSourceSet.getAnnotationProcessorPath());
            classpath.from(mainSourceSet.getAnnotationProcessorPath());
            mixinSourceSet.setAnnotationProcessorPath(classpath);

            project.getTasks()
                .named(JavaPlugin.JAR_TASK_NAME, Jar.class)
                .configure(jar -> { jar.from(mixinSourceSet.getOutput()); });
            if (!gtnh.configuration.noPublishedSources) {
                project.getTasks()
                    .named("sourcesJar", Jar.class)
                    .configure(jar -> {
                        jar.from(mixinSourceSet.getAllSource());
                        jar.from(mixinSourceSet.getResources());
                    });
            }
            if (gtnh.configuration.usesShadowedDependencies) {
                project.getTasks()
                    .named("shadowJar", ShadowJar.class)
                    .configure(jar -> { jar.from(mixinSourceSet.getOutput()); });
            }
        } else {
            mixinSourceSet = mainSourceSet;
        }
        modUtils.mixinSourceSet.set(mixinSourceSet);

        final DependencyHandler deps = project.getDependencies();
        if (gtnh.configuration.usesMixins) {
            final String apConfiguration = mixinSourceSet.getAnnotationProcessorConfigurationName();
            deps.add(apConfiguration, "org.ow2.asm:asm-debug-all:5.0.4");
            deps.add(apConfiguration, "com.google.guava:guava:24.1.1-jre");
            deps.add(apConfiguration, "com.google.code.gson:gson:2.13.2");
            deps.add(apConfiguration, mixinProviderSpec);
            if (gtnh.configuration.usesMixinDebug) {
                deps.add("runtimeOnlyNonPublishable", "org.jetbrains:intellij-fernflower:1.2.1.16");
            }
            // Give both the mixin and main source set Mixin API access
            deps.add(
                JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                modUtils.enableMixins(mixinProviderSpec, mixingConfigRefMap));

            project.getPluginManager()
                .withPlugin("org.jetbrains.kotlin.kapt", p -> {
                    if (!mixinSourceSet.getName()
                        .equals("main")) {
                        throw new IllegalArgumentException("Non-main source sets not supported for Kotlin mixins");
                    }
                    deps.add("kapt", mixinProviderSpec);
                });
        } else if (gtnh.configuration.forceEnableMixins) {
            deps.add("runtimeOnlyNonPublishable", mixinProviderSpec);
        }

        // Replace old mixin mods with unimixins
        // https://docs.gradle.org/8.0.2/userguide/resolution_rules.html#sec:substitution_with_classifier
        project.getConfigurations()
            .all(c -> {
                final DependencySubstitutions ds = c.getResolutionStrategy()
                    .getDependencySubstitution();
                ds.substitute(ds.module("com.gtnewhorizon:gtnhmixins"))
                    .using(ds.module(mixinProviderSpecNoClassifer))
                    .withClassifier("dev")
                    .because("Unimixins replaces other mixin mods");
                ds.substitute(ds.module("com.github.GTNewHorizons:Mixingasm"))
                    .using(ds.module(mixinProviderSpecNoClassifer))
                    .withClassifier("dev")
                    .because("Unimixins replaces other mixin mods");
                ds.substitute(ds.module("com.github.GTNewHorizons:SpongePoweredMixin"))
                    .using(ds.module(mixinProviderSpecNoClassifer))
                    .withClassifier("dev")
                    .because("Unimixins replaces other mixin mods");
                ds.substitute(ds.module("com.github.GTNewHorizons:SpongeMixins"))
                    .using(ds.module(mixinProviderSpecNoClassifer))
                    .withClassifier("dev")
                    .because("Unimixins replaces other mixin mods");
                ds.substitute(ds.module("io.github.legacymoddingmc:unimixins:0.1.5"))
                    .using(ds.module(mixinProviderSpecNoClassifer))
                    .withClassifier("dev")
                    .because("Our previous unimixins upload was missing the dev classifier");
            });

        final TaskContainer tasks = project.getTasks();

        final TaskProvider<GenerateMixinAssetsTask> genTask = tasks
            .register("generateAssets", GenerateMixinAssetsTask.class, t -> {
                t.getMixinsEnabled()
                    .set(gtnh.configuration.usesMixins);
                t.getModGroup()
                    .set(gtnh.configuration.modGroup);
                t.getMixinsPackage()
                    .set(gtnh.configuration.mixinsPackage);
                t.getMixinConfigRefMap()
                    .set(mixingConfigRefMap);
                t.getMixinPlugin()
                    .set(gtnh.configuration.mixinPlugin);
                t.getOutputFile()
                    .set(
                        project.file(
                            "src/" + mixinSourceSet.getName()
                                + "/resources/mixins."
                                + gtnh.configuration.modId
                                + ".json"));
            });

        if (gtnh.configuration.usesMixins) {
            tasks.named(mixinSourceSet.getCompileJavaTaskName(), JavaCompile.class)
                .configure(jc -> {
                    // Elan: from what I understand they are just some linter configs so you get some warning on how to
                    // properly code
                    jc.getOptions()
                        .getCompilerArgs()
                        .add("-XDenableSunApiLintControl");
                    jc.getOptions()
                        .getCompilerArgs()
                        .add("-XDignore.symbol.file");
                });
        }
    }
}
