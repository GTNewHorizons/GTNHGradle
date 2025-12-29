package com.gtnewhorizons.gtnhgradle.modules;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.JvmDowngraderStubsProvider;
import com.gtnewhorizons.gtnhgradle.UpdateableConstants;
import com.gtnewhorizons.gtnhgradle.tasks.ValidateLombokVersionTask;
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar;
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.wagyourtail.jvmdg.gradle.JVMDowngraderExtension;
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar;
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar;
import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** JVM Downgrader module. Must be applied after {@link ShadowModule} when shadow is used. */
public class JVMDowngraderModule implements GTNHModule {

    private static final String SHADOW_JAR_TASK = "shadowJar";
    private static final String DOWNGRADE_JAR_TASK = "downgradeJar";
    private static final String SHADE_DOWNGRADED_API_TASK = "shadeDowngradedApi";
    private static final String REOBF_JAR_TASK = "reobfJar";

    @Override
    public boolean isEnabled(GTNHGradlePlugin.@NotNull GTNHExtension gtnh) {
        return gtnh.getModernJavaSyntaxMode()
            .get()
            .usesJvmDowngrader();
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final int downgradeTarget = gtnh.getDowngradeTargetVersion()
            .get();
        final JavaVersion targetVersion = JavaVersion.toVersion(downgradeTarget);
        final JvmDowngraderStubsProvider stubsProvider = gtnh.getJvmDowngraderStubsProvider()
            .get();
        final boolean shadeStubs = stubsProvider.shouldShadeStubs();

        if (gtnh.configuration.jvmDowngraderStubsProvider.isEmpty()) {
            project.getLogger()
                .warn(
                    "jvmDowngraderStubsProvider is not set. Defaulting to 'gtnhlib'. "
                        + "Set explicitly to 'gtnhlib', 'shade', or 'external' to suppress this warning.");
        }

        ensureJvmdgSnapshotRepo(project);
        applyJvmdgPlugin(project, gtnh, targetVersion);
        configureCompileDependencies(project, gtnh, downgradeTarget, stubsProvider);
        final DowngradeTasks downgradeTasks = registerDowngradeTasks(project, gtnh, targetVersion);
        configureTestTask(project, gtnh, downgradeTasks);
        final TaskProvider<? extends AbstractArchiveTask> publishableDevJar = configureJarTaskChain(
            gtnh,
            project,
            downgradeTarget,
            targetVersion,
            shadeStubs);
        configureRunTasks(gtnh, project, downgradeTasks, publishableDevJar);
        validateLombokVersion(gtnh, project);
    }

    private void applyJvmdgPlugin(Project project, GTNHGradlePlugin.GTNHExtension gtnh, JavaVersion targetVersion) {
        project.getPluginManager()
            .apply("xyz.wagyourtail.jvmdowngrader");

        final JVMDowngraderExtension jvmdgExt = project.getExtensions()
            .getByType(JVMDowngraderExtension.class);
        jvmdgExt.getDowngradeTo()
            .set(targetVersion);

        configureMultiReleaseJar(project, jvmdgExt, gtnh);
    }

    private void configureMultiReleaseJar(Project project, JVMDowngraderExtension jvmdgExt,
        GTNHGradlePlugin.GTNHExtension gtnh) {
        final int toolchainVersion = gtnh.getEffectiveToolchainVersion()
            .get();
        final Set<Integer> multiReleaseVersions = gtnh.getJvmDowngraderMultiReleaseVersions()
            .get();

        if (multiReleaseVersions.isEmpty()) {
            return;
        }

        final Set<JavaVersion> intermediateVersions = new HashSet<>();
        for (int version : multiReleaseVersions) {
            if (version == toolchainVersion) {
                jvmdgExt.getMultiReleaseOriginal()
                    .set(true);
            } else if (version < toolchainVersion) {
                intermediateVersions.add(JavaVersion.toVersion(version));
            } else {
                project.getLogger()
                    .warn(
                        "jvmDowngraderMultiReleaseVersions contains {} which exceeds toolchain version {}. "
                            + "Ignoring - cannot generate bytecode for a higher Java version than the compiler.",
                        version,
                        toolchainVersion);
            }
        }

        if (!intermediateVersions.isEmpty()) {
            jvmdgExt.getMultiReleaseVersions()
                .set(intermediateVersions);
        }
    }

    private void configureCompileDependencies(Project project, GTNHGradlePlugin.GTNHExtension gtnh, int downgradeTarget,
        JvmDowngraderStubsProvider stubsProvider) {
        final DependencyHandler deps = project.getDependencies();
        final String downgradedApiDep = UpdateableConstants.jvmdgApiDependency(downgradeTarget);
        deps.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, downgradedApiDep);
        deps.add(JavaPlugin.TEST_RUNTIME_ONLY_CONFIGURATION_NAME, downgradedApiDep);

        addJabelStub(project, deps);

        if (!stubsProvider.shouldShadeStubs() && !stubsProvider.isExternal()) {
            deps.getConstraints()
                .add(
                    JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                    UpdateableConstants.MIN_GTNHLIB_FOR_JVMDG_STUBS,
                    constraint -> constraint.because("Required for JVM Downgrader stubs when not shading them"));
        }
    }

    private void ensureJvmdgSnapshotRepo(Project project) {
        if (UpdateableConstants.JVMDG_VERSION.contains("-SNAPSHOT")) {
            project.getRepositories()
                .maven(repo -> {
                    repo.setName("WagYourTail Maven Snapshots");
                    repo.setUrl(UpdateableConstants.JVMDG_SNAPSHOT_REPO);
                    repo.mavenContent(content -> content.snapshotsOnly());
                    repo.content(desc -> desc.includeGroup(UpdateableConstants.JVMDG_GROUP));
                });
        }
    }

    private Configuration createDowngradedApiConfiguration(Project project, ConfigurationContainer cfgs,
        int downgradeTarget) {
        final String configName = "jvmdgApiForJava" + downgradeTarget;
        return cfgs.create(configName, config -> {
            config.setDescription("JVM Downgrader API jar for Java " + downgradeTarget);
            config.setCanBeConsumed(false);
            config.setCanBeResolved(true);
            config.setVisible(false);
            config.setTransitive(false);
            config.getDependencies()
                .add(
                    project.getDependencies()
                        .create(UpdateableConstants.jvmdgApiDependency(downgradeTarget)));
        });
    }

    /** Adds Jabel stub jar for @Desugar annotation compatibility. */
    private void addJabelStub(Project project, DependencyHandler deps) {
        final TaskProvider<?> extractStub = project.getTasks()
            .register("extractJabelStub", task -> {
                final var outputFile = project.getObjects()
                    .fileProperty();
                outputFile.set(
                    project.getLayout()
                        .getBuildDirectory()
                        .file("gtnhgradle/jabel-stub/jabel-stub.jar"));
                task.getOutputs()
                    .file(outputFile);
                task.doLast(t -> {
                    try (InputStream stubStream = getClass().getResourceAsStream("/jabel-stub/jabel-stub.jar")) {
                        if (stubStream == null) {
                            throw new GradleException("Jabel stub jar not found in GTNHGradle resources");
                        }
                        final File stubJar = outputFile.get()
                            .getAsFile();
                        stubJar.getParentFile()
                            .mkdirs();
                        Files.copy(stubStream, stubJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new GradleException("Failed to extract Jabel stub jar", e);
                    }
                });
            });

        deps.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, project.files(extractStub));
    }

    private static class DowngradeTasks {

        final TaskProvider<DowngradeFiles> main;
        final @Nullable TaskProvider<DowngradeFiles> mixin;
        final TaskProvider<DowngradeFiles> test;
        final SourceSet mainSourceSet;
        final @Nullable SourceSet mixinSourceSet;
        final SourceSet testSourceSet;

        DowngradeTasks(TaskProvider<DowngradeFiles> main, @Nullable TaskProvider<DowngradeFiles> mixin,
            TaskProvider<DowngradeFiles> test, SourceSet mainSourceSet, @Nullable SourceSet mixinSourceSet,
            SourceSet testSourceSet) {
            this.main = main;
            this.mixin = mixin;
            this.test = test;
            this.mainSourceSet = mainSourceSet;
            this.mixinSourceSet = mixinSourceSet;
            this.testSourceSet = testSourceSet;
        }
    }

    private DowngradeTasks registerDowngradeTasks(Project project, GTNHGradlePlugin.GTNHExtension gtnh,
        JavaVersion targetVersion) {

        final TaskContainer tasks = project.getTasks();
        final JavaPluginExtension java = project.getExtensions()
            .getByType(JavaPluginExtension.class);
        final SourceSetContainer sourceSets = java.getSourceSets();

        final SourceSet mainSourceSet = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        final SourceSet testSourceSet = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
        final String mixinSourceSetName = gtnh.configuration.separateMixinSourceSet;
        final SourceSet mixinSourceSet = sourceSets.findByName(mixinSourceSetName);

        final TaskProvider<DowngradeFiles> downgradeMainClasses = registerDowngradeTask(
            project,
            "downgradeMainClasses",
            mainSourceSet,
            targetVersion,
            tasks.named("classes"));

        TaskProvider<DowngradeFiles> downgradeMixinClasses = null;
        if (mixinSourceSet != null) {
            downgradeMixinClasses = registerDowngradeTask(
                project,
                "downgradeMixinClasses",
                mixinSourceSet,
                targetVersion,
                tasks.named(mixinSourceSetName + "Classes"));
        }

        final TaskProvider<DowngradeFiles> downgradeTestClasses = tasks
            .register("downgradeTestClasses", DowngradeFiles.class, task -> {
                task.setInputCollection(
                    testSourceSet.getOutput()
                        .getClassesDirs());
                task.setClasspath(testSourceSet.getCompileClasspath());
                task.getDowngradeTo()
                    .set(targetVersion);
                task.getMultiReleaseOriginal()
                    .set(false);
                task.getMultiReleaseVersions()
                    .set(Collections.emptySet());
                task.dependsOn(tasks.named("testClasses"));
            });

        return new DowngradeTasks(
            downgradeMainClasses,
            downgradeMixinClasses,
            downgradeTestClasses,
            mainSourceSet,
            mixinSourceSet,
            testSourceSet);
    }

    private TaskProvider<DowngradeFiles> registerDowngradeTask(Project project, String taskName, SourceSet sourceSet,
        JavaVersion targetVersion, TaskProvider<?> dependsOn) {

        return project.getTasks()
            .register(taskName, DowngradeFiles.class, task -> {
                task.setInputCollection(
                    sourceSet.getOutput()
                        .getClassesDirs());
                task.setClasspath(sourceSet.getCompileClasspath());
                task.getDowngradeTo()
                    .set(targetVersion);
                task.dependsOn(dependsOn);
            });
    }

    private void configureTestTask(Project project, GTNHGradlePlugin.GTNHExtension gtnh,
        DowngradeTasks downgradeTasks) {
        project.getTasks()
            .named(JavaPlugin.TEST_TASK_NAME, Test.class)
            .configure(test -> {
                test.dependsOn(downgradeTasks.test, downgradeTasks.main);
                if (downgradeTasks.mixin != null) {
                    test.dependsOn(downgradeTasks.mixin);
                }

                final FileCollection mainClassesDirs = downgradeTasks.mainSourceSet.getOutput()
                    .getClassesDirs();
                final FileCollection testClassesDirs = downgradeTasks.testSourceSet.getOutput()
                    .getClassesDirs();
                FileCollection excludedDirs = mainClassesDirs.plus(testClassesDirs);
                if (downgradeTasks.mixinSourceSet != null) {
                    excludedDirs = excludedDirs.plus(
                        downgradeTasks.mixinSourceSet.getOutput()
                            .getClassesDirs());
                }

                final FileCollection finalExcludedDirs = excludedDirs;
                final FileCollection testOutput = project
                    .files(downgradeTasks.test.map(DowngradeFiles::getOutputCollection));
                final FileCollection mainOutput = project
                    .files(downgradeTasks.main.map(DowngradeFiles::getOutputCollection));

                test.setTestClassesDirs(testOutput);

                FileCollection newClasspath = testOutput.plus(mainOutput);
                if (downgradeTasks.mixin != null) {
                    newClasspath = newClasspath
                        .plus(project.files(downgradeTasks.mixin.map(DowngradeFiles::getOutputCollection)));
                }

                final FileCollection downgradedClasspath = newClasspath;
                test.setClasspath(
                    downgradedClasspath.plus(
                        test.getClasspath()
                            .minus(finalExcludedDirs)));
            });
    }

    private TaskProvider<? extends AbstractArchiveTask> configureJarTaskChain(GTNHGradlePlugin.GTNHExtension gtnh,
        Project project, int downgradeTarget, JavaVersion targetVersion, boolean shadeStubs) {
        final TaskContainer tasks = project.getTasks();
        final ConfigurationContainer cfgs = project.getConfigurations();
        final boolean usesShadow = gtnh.configuration.usesShadowedDependencies;

        if (usesShadow) {
            verifyShadowModuleApplied(project);
        }

        final TaskProvider<? extends AbstractArchiveTask> inputTask = configureInputTask(tasks, usesShadow);
        final TaskProvider<DowngradeJar> downgradeJar = tasks.named(DOWNGRADE_JAR_TASK, DowngradeJar.class);
        downgradeJar.configure(task -> {
            task.dependsOn(inputTask);
            task.getInputFile()
                .set(inputTask.flatMap(AbstractArchiveTask::getArchiveFile));
            task.getDowngradeTo()
                .set(targetVersion);
            task.getArchiveClassifier()
                .set(shadeStubs ? "downgraded" : "dev");
        });

        final TaskProvider<? extends AbstractArchiveTask> publishableDevJar;
        if (shadeStubs) {
            publishableDevJar = configureStubShading(
                project,
                tasks,
                cfgs,
                downgradeJar,
                downgradeTarget,
                targetVersion);
        } else {
            tasks.named(REOBF_JAR_TASK, ReobfuscatedJar.class)
                .configure(reobf -> {
                    reobf.getInputJar()
                        .set(downgradeJar.flatMap(DowngradeJar::getArchiveFile));
                });
            publishableDevJar = downgradeJar;
        }

        updateOutgoingArtifacts(project, cfgs, publishableDevJar);

        return publishableDevJar;
    }

    private void verifyShadowModuleApplied(Project project) {
        if (!project.getPlugins()
            .hasPlugin("com.github.johnrengelman.shadow")) {
            throw new GradleException(
                "JVMDowngraderModule requires ShadowModule when usesShadowedDependencies is true. "
                    + "Ensure ShadowModule is applied before JVMDowngraderModule.");
        }
    }

    private TaskProvider<? extends AbstractArchiveTask> configureInputTask(TaskContainer tasks, boolean usesShadow) {
        if (usesShadow) {
            final TaskProvider<ShadowJar> shadowJar = tasks.named(SHADOW_JAR_TASK, ShadowJar.class);
            shadowJar.configure(
                sj -> sj.getArchiveClassifier()
                    .set("predowngrade"));
            return shadowJar;
        } else {
            final TaskProvider<Jar> jarTask = tasks.named(JavaPlugin.JAR_TASK_NAME, Jar.class);
            jarTask.configure(
                jar -> jar.getArchiveClassifier()
                    .set("predowngrade"));
            return jarTask;
        }
    }

    private TaskProvider<ShadeJar> configureStubShading(Project project, TaskContainer tasks,
        ConfigurationContainer cfgs, TaskProvider<DowngradeJar> downgradeJar, int downgradeTarget,
        JavaVersion targetVersion) {

        final FileCollection jvmdgApiFiles = createDowngradedApiConfiguration(project, cfgs, downgradeTarget);

        final TaskProvider<ShadeJar> shadeDowngradedApi = tasks.named(SHADE_DOWNGRADED_API_TASK, ShadeJar.class);
        shadeDowngradedApi.configure(task -> {
            task.getInputFile()
                .set(downgradeJar.flatMap(DowngradeJar::getArchiveFile));
            task.getDowngradeTo()
                .set(targetVersion);
            task.getArchiveClassifier()
                .set("dev");
            task.getApiJar()
                .set(project.provider(() -> new ArrayList<>(jvmdgApiFiles.getFiles())));
        });

        tasks.named(REOBF_JAR_TASK, ReobfuscatedJar.class)
            .configure(reobf -> {
                reobf.getInputJar()
                    .set(shadeDowngradedApi.flatMap(ShadeJar::getArchiveFile));
            });

        return shadeDowngradedApi;
    }

    private void updateOutgoingArtifacts(Project project, ConfigurationContainer cfgs,
        TaskProvider<? extends AbstractArchiveTask> publishableDevJar) {
        for (final String outgoingConfig : List.of("runtimeElements", "apiElements")) {
            final Configuration outgoing = cfgs.findByName(outgoingConfig);
            if (outgoing != null) {
                outgoing.getOutgoing()
                    .getArtifacts()
                    .clear();
                outgoing.getOutgoing()
                    .artifact(publishableDevJar);
            }
        }

        project.getExtensions()
            .getExtraProperties()
            .set("publishableDevJar", publishableDevJar);
    }

    private void configureRunTasks(GTNHGradlePlugin.GTNHExtension gtnh, Project project, DowngradeTasks downgradeTasks,
        TaskProvider<? extends AbstractArchiveTask> publishableDevJar) {
        final TaskContainer tasks = project.getTasks();
        final ConfigurationContainer cfgs = project.getConfigurations();
        final boolean usesShadow = gtnh.configuration.usesShadowedDependencies;

        final FileCollection mainClassesDirs = downgradeTasks.mainSourceSet.getOutput()
            .getClassesDirs();
        final File resourcesDir = downgradeTasks.mainSourceSet.getOutput()
            .getResourcesDir();
        final FileCollection mainResources = resourcesDir != null ? project.files(resourcesDir) : project.files();
        FileCollection excludedFiles = mainClassesDirs.plus(mainResources);

        if (downgradeTasks.mixinSourceSet != null) {
            excludedFiles = excludedFiles.plus(
                downgradeTasks.mixinSourceSet.getOutput()
                    .getClassesDirs());
        }

        final FileCollection finalExcludedFiles = excludedFiles;

        for (final String taskName : List.of("runClient", "runServer")) {
            tasks.named(taskName, RunMinecraftTask.class)
                .configure(task -> {
                    task.classpath(publishableDevJar);
                    FileCollection filtered = task.getClasspath()
                        .minus(finalExcludedFiles)
                        .filter(f -> !isIntermediateJar(f));

                    if (usesShadow) {
                        filtered = excludeShadowConfigurations(filtered, cfgs);
                    }

                    task.setClasspath(filtered);
                });
        }
    }

    private static boolean isIntermediateJar(File f) {
        final String name = f.getName();
        return name.contains("-dev-preshadow.") || name.contains("-predowngrade.") || name.contains("-downgraded.");
    }

    private static final String VALIDATE_LOMBOK_TASK = "validateLombokForJava25";

    private void validateLombokVersion(GTNHGradlePlugin.GTNHExtension gtnh, Project project) {
        final String mixinSourceSetName = gtnh.configuration.separateMixinSourceSet;
        final Configuration annotationProcessor = project.getConfigurations()
            .getByName("annotationProcessor");
        final Configuration mixinAP = mixinSourceSetName.isEmpty() ? null
            : project.getConfigurations()
                .findByName(mixinSourceSetName + "AnnotationProcessor");

        final int toolchain = gtnh.getEffectiveToolchainVersion()
            .get();
        final Set<Integer> mrVersions = gtnh.getJvmDowngraderMultiReleaseVersions()
            .get();
        final boolean targetsJava25Plus = toolchain >= 25 || mrVersions.stream()
            .anyMatch(v -> v >= 25);

        final TaskProvider<ValidateLombokVersionTask> validateTask = project.getTasks()
            .register(VALIDATE_LOMBOK_TASK, ValidateLombokVersionTask.class, task -> {
                task.setDescription("Validates Lombok version compatibility with Java 25+");
                task.setGroup("verification");

                task.getLombokVersions()
                    .set(project.provider(() -> {
                        final List<String> versions = new ArrayList<>(extractLombokVersions(annotationProcessor));
                        if (mixinAP != null) {
                            versions.addAll(extractLombokVersions(mixinAP));
                        }
                        return versions;
                    }));
                task.getMinimumVersion()
                    .set(UpdateableConstants.MIN_LOMBOK_FOR_JAVA_25);

                task.onlyIf(
                    t -> !task.getLombokVersions()
                        .get()
                        .isEmpty() && targetsJava25Plus);
            });

        project.getTasks()
            .named("compileJava")
            .configure(task -> task.dependsOn(validateTask));

        if (!mixinSourceSetName.isEmpty()) {
            final String mixinCompileTask = "compile" + Character.toUpperCase(mixinSourceSetName.charAt(0))
                + mixinSourceSetName.substring(1)
                + "Java";
            project.getTasks()
                .matching(
                    t -> t.getName()
                        .equals(mixinCompileTask))
                .configureEach(task -> task.dependsOn(validateTask));
        }
    }

    private static List<String> extractLombokVersions(Configuration config) {
        final List<String> versions = new ArrayList<>();

        for (Dependency dep : config.getAllDependencies()) {
            if (!"lombok".equals(dep.getName())) {
                continue;
            }
            final String group = dep.getGroup();
            if (group != null && !"org.projectlombok".equals(group)) {
                continue;
            }

            final String version = dep.getVersion();
            if (version != null && !version.isEmpty()) {
                versions.add(version);
            }
        }

        return versions;
    }

    /** Excludes shadow dependency configurations from a FileCollection. */
    public static FileCollection excludeShadowConfigurations(FileCollection files, ConfigurationContainer cfgs) {
        FileCollection filtered = files;

        for (final String configName : List.of("shadowImplementation", "shadeCompile", "shadowCompile")) {
            final Configuration config = cfgs.findByName(configName);
            if (config != null) {
                filtered = filtered.minus(config);
            }
        }

        return filtered;
    }
}
