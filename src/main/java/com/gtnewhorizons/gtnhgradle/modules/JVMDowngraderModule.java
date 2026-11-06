package com.gtnewhorizons.gtnhgradle.modules;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.JvmDowngraderStubsProvider;
import com.gtnewhorizons.gtnhgradle.ModernJavaSyntaxMode;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.lang3.StringUtils;
import com.gtnewhorizons.gtnhgradle.UpdateableConstants;
import com.gtnewhorizons.gtnhgradle.tasks.ArchiveFastPath;
import com.gtnewhorizons.gtnhgradle.tasks.ValidateLombokVersionTask;
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar;
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.compress.archivers.zip.ZipExtraField;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.compress.archivers.zip.ZipFile;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.archive.ZipEntryConstants;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.ZipEntryCompression;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.wagyourtail.jvmdg.ClassDowngrader;
import xyz.wagyourtail.jvmdg.cli.Flags;
import xyz.wagyourtail.jvmdg.compile.PathDowngrader;
import xyz.wagyourtail.jvmdg.gradle.JVMDowngraderExtension;
import xyz.wagyourtail.jvmdg.gradle.flags.ToFlagsKt;
import xyz.wagyourtail.jvmdg.gradle.task.DowngradeJar;
import xyz.wagyourtail.jvmdg.gradle.task.ShadeJar;
import xyz.wagyourtail.jvmdg.gradle.task.files.DowngradeFiles;
import xyz.wagyourtail.jvmdg.util.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarFile;

/** JVM Downgrader module. Must be applied after {@link ShadowModule} when shadow is used. */
public class JVMDowngraderModule implements GTNHModule {

    private static final String SHADOW_JAR_TASK = "shadowJar";
    private static final String DOWNGRADE_JAR_TASK = "downgradeJar";
    private static final String SHADE_DOWNGRADED_API_TASK = "shadeDowngradedApi";
    private static final String REOBF_JAR_TASK = "reobfJar";

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return ModernJavaSyntaxMode.fromString(configuration.enableModernJavaSyntax)
            .usesJvmDowngrader();
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        // Parse configuration values
        final ModernJavaSyntaxMode mode = ModernJavaSyntaxMode.fromString(gtnh.configuration.enableModernJavaSyntax);
        final int downgradeTarget = gtnh.configuration.downgradeTargetVersion;
        final JavaVersion targetVersion = JavaVersion.toVersion(downgradeTarget);
        final JvmDowngraderStubsProvider stubsProvider = JvmDowngraderStubsProvider
            .fromString(gtnh.configuration.jvmDowngraderStubsProvider);
        final boolean shadeStubs = stubsProvider.shouldShadeStubs();
        final Set<Integer> multiReleaseVersions = parseMultiReleaseVersions(
            gtnh.configuration.jvmDowngraderMultiReleaseVersions);
        final int effectiveToolchainVersion = computeEffectiveToolchainVersion(
            mode,
            gtnh.configuration.forceToolchainVersion,
            multiReleaseVersions);

        if (gtnh.configuration.jvmDowngraderStubsProvider.isEmpty()) {
            project.getLogger()
                .warn(
                    "jvmDowngraderStubsProvider is not set. Defaulting to 'gtnhlib'. "
                        + "Set explicitly to 'gtnhlib', 'shade', or 'external' to suppress this warning.");
        }

        ensureJvmdgSnapshotRepo(project);
        applyJvmdgPlugin(project, gtnh, targetVersion, effectiveToolchainVersion, multiReleaseVersions);
        configureCompileDependencies(project, gtnh, downgradeTarget, stubsProvider);
        final DowngradeTasks downgradeTasks = registerDowngradeTasks(project, gtnh, targetVersion);
        configureTestTask(project, gtnh, downgradeTasks);
        final TaskProvider<? extends AbstractArchiveTask> publishableDevJar = configureJarTaskChain(
            gtnh,
            project,
            downgradeTarget,
            targetVersion,
            shadeStubs);
        configureApiJarDowngrade(gtnh, project, targetVersion);
        configureRunTasks(gtnh, project, downgradeTasks, publishableDevJar);
        validateLombokVersion(gtnh, project, effectiveToolchainVersion, multiReleaseVersions);
    }

    private static Set<Integer> parseMultiReleaseVersions(String mrVersionsStr) {
        if (mrVersionsStr == null || mrVersionsStr.isEmpty()) {
            return Set.of(21, 25); // Default multi-release versions
        }
        final Set<Integer> versions = new LinkedHashSet<>();
        for (String versionStr : mrVersionsStr.split(",")) {
            try {
                int version = Integer.parseInt(versionStr.trim());
                if (version < 9) {
                    throw new IllegalArgumentException(
                        "Invalid jvmDowngraderMultiReleaseVersions entry: " + version + ". Must be >= 9.");
                }
                versions.add(version);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                    "Invalid jvmDowngraderMultiReleaseVersions: '" + mrVersionsStr
                        + "'. Must be comma-separated integers (e.g., '21' or '21,25').");
            }
        }
        return Set.copyOf(versions);
    }

    /**
     * Computes the effective toolchain version based on mode, forced version, and multi-release versions.
     */
    private static int computeEffectiveToolchainVersion(ModernJavaSyntaxMode mode, int forcedVersion,
        Set<Integer> mrVersions) {
        // Find max multi-release version
        int maxMultiReleaseVersion = 0;
        if (mode.usesJvmDowngrader()) {
            for (int version : mrVersions) {
                maxMultiReleaseVersion = Math.max(maxMultiReleaseVersion, version);
            }
        }

        // Compute mode default version
        final int modeDefaultVersion = switch (mode) {
            case FALSE -> 8;
            case JABEL -> 17;
            case JVM_DOWNGRADER, MODERN -> 25;
        };

        if (forcedVersion != -1) {
            // Validate forced version against jvmDowngraderMultiReleaseVersions
            if (maxMultiReleaseVersion > forcedVersion) {
                throw new IllegalArgumentException(
                    "forceToolchainVersion=" + forcedVersion
                        + " is lower than max jvmDowngraderMultiReleaseVersions="
                        + maxMultiReleaseVersion
                        + ". Cannot create Java "
                        + maxMultiReleaseVersion
                        + " bytecode with a Java "
                        + forcedVersion
                        + " toolchain. Either increase forceToolchainVersion or reduce jvmDowngraderMultiReleaseVersions.");
            }
            return forcedVersion;
        } else {
            // Auto toolchain version - use max of mode default and jvmDowngraderMultiReleaseVersions
            return Math.max(modeDefaultVersion, maxMultiReleaseVersion);
        }
    }

    private void applyJvmdgPlugin(Project project, GTNHGradlePlugin.GTNHExtension gtnh, JavaVersion targetVersion,
        int effectiveToolchainVersion, Set<Integer> multiReleaseVersions) {
        project.getPluginManager()
            .apply(xyz.wagyourtail.jvmdg.gradle.JVMDowngraderPlugin.class);

        final JVMDowngraderExtension jvmdgExt = project.getExtensions()
            .getByType(JVMDowngraderExtension.class);
        jvmdgExt.getDowngradeTo()
            .set(targetVersion);

        configureMultiReleaseJar(jvmdgExt, effectiveToolchainVersion, multiReleaseVersions);
    }

    private void configureMultiReleaseJar(JVMDowngraderExtension jvmdgExt, int toolchainVersion,
        Set<Integer> multiReleaseVersions) {
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
                throw new GradleException(
                    "jvmDowngraderMultiReleaseVersions contains " + version
                        + " which exceeds toolchain version "
                        + toolchainVersion
                        + ". Cannot generate bytecode for a higher Java version than the compiler.");
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
            project.getConfigurations()
                .all(c -> {
                    if (c.isCanBeDeclared()) {
                        deps.getConstraints()
                            .add(
                                c.getName(),
                                UpdateableConstants.MIN_GTNHLIB_FOR_JVMDG_STUBS,
                                constraint -> constraint
                                    .because("Required for JVM Downgrader stubs when not shading them"));
                    }
                });
        }
    }

    private void ensureJvmdgSnapshotRepo(Project project) {
        if (UpdateableConstants.JVMDG_VERSION.contains("-SNAPSHOT")) {
            project.getRepositories()
                .maven(repo -> {
                    repo.setName("WagYourTail Maven Snapshots");
                    repo.setUrl(UpdateableConstants.JVMDG_SNAPSHOT_REPO);
                    repo.mavenContent(MavenRepositoryContentDescriptor::snapshotsOnly);
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
            config.setTransitive(false);
            config.getDependencies()
                .add(
                    project.getDependencies()
                        .create(UpdateableConstants.jvmdgApiDependency(downgradeTarget)));
        });
    }

    /** Adds Jabel stub jar for @Desugar annotation compatibility. */
    private void addJabelStub(Project project, DependencyHandler deps) {
        deps.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, UpdateableConstants.JABEL_STUBS);
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
                declareOutputsOfMissingInputs(
                    task,
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
                declareOutputsOfMissingInputs(
                    task,
                    sourceSet.getOutput()
                        .getClassesDirs());
                task.setClasspath(sourceSet.getCompileClasspath());
                task.getDowngradeTo()
                    .set(targetVersion);
                task.dependsOn(dependsOn);
            });
    }

    /**
     * DowngradeFiles only declares outputs for the inputs that exist when it is configured. On a clean tree the classes
     * are not compiled yet, so the task had no outputs and the tests using them silently ran nothing.
     */
    private static void declareOutputsOfMissingInputs(DowngradeFiles task, FileCollection inputs) {
        for (final File input : inputs.getFiles()) {
            if (!input.exists()) {
                task.getOutputs()
                    .dir(new File(task.getTemporaryDir(), input.getName()));
            }
        }
    }

    /**
     * Turns multi-release handling off in the test JVM, which the tests need to load the downgraded classes out of the
     * jar instead of the copies for the JVM's own Java version. Only when they do read the jar: the flag applies to
     * every jar on the test classpath, and the tests keep using the separately downgraded classes when something is
     * shaded.
     */
    private static class MultiReleaseFlag implements CommandLineArgumentProvider {

        private final Provider<Boolean> testsUseDowngradedJar;

        MultiReleaseFlag(Provider<Boolean> testsUseDowngradedJar) {
            this.testsUseDowngradedJar = testsUseDowngradedJar;
        }

        /** @return Whether the tests read the downgraded jar */
        @Input
        public Provider<Boolean> getTestsUseDowngradedJar() {
            return testsUseDowngradedJar;
        }

        @Override
        public Iterable<String> asArguments() {
            return testsUseDowngradedJar.get() ? List.of("-Djdk.util.jar.enableMultiRelease=false") : List.of();
        }
    }

    private void configureTestTask(Project project, GTNHGradlePlugin.GTNHExtension gtnh,
        DowngradeTasks downgradeTasks) {
        // The main and mixin classes are already downgraded in the dev jar, so the tests use it instead of downgrading
        // them a second time. Shaded dependencies are relocated in that jar while the test code uses their original
        // names, and copy rules can leave classes out of it, so in those cases the separately downgraded classes are
        // used like before.
        final boolean usesShadow = gtnh.configuration.usesShadowedDependencies;
        final TaskProvider<Jar> inputJar = project.getTasks()
            .named(usesShadow ? SHADOW_JAR_TASK : JavaPlugin.JAR_TASK_NAME, Jar.class);
        final TaskProvider<DowngradeJar> downgradeJar = project.getTasks()
            .named(DOWNGRADE_JAR_TASK, DowngradeJar.class);
        final Provider<Boolean> jarHasAllClasses = project.provider(
            () -> !ArchiveFastPath.filtersEntries(inputJar.get(), ShadowModule.DEFAULT_EXCLUDES)
                && !ArchiveFastPath.filtersEntries(downgradeJar.get(), Set.of()));
        final Provider<Boolean> testsUseDowngradedJar = usesShadow ? ShadowModule.shadowsNothing(project)
            .zip(jarHasAllClasses, (shadowsNothing, hasAllClasses) -> shadowsNothing && hasAllClasses)
            : jarHasAllClasses;
        final Provider<RegularFile> downgradedJar = downgradeJar.flatMap(DowngradeJar::getArchiveFile);
        final List<TaskProvider<DowngradeFiles>> classesDowngrades = new ArrayList<>();
        classesDowngrades.add(downgradeTasks.main);
        if (downgradeTasks.mixin != null) {
            classesDowngrades.add(downgradeTasks.mixin);
        }

        project.getTasks()
            .named(JavaPlugin.TEST_TASK_NAME, Test.class)
            .configure(test -> {
                test.dependsOn(downgradeTasks.test);
                test.dependsOn(testsUseDowngradedJar.map(useJar -> useJar ? List.of() : classesDowngrades));
                test.getJvmArgumentProviders()
                    .add(new MultiReleaseFlag(testsUseDowngradedJar));

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
                final FileCollection mainOutput = project.files(
                    (Callable<Object>) () -> testsUseDowngradedJar.get() ? downgradedJar
                        : classesDowngrades.stream()
                            .map(task -> task.map(DowngradeFiles::getOutputCollection))
                            .toList());

                test.setTestClassesDirs(testOutput);
                test.setClasspath(
                    testOutput.plus(mainOutput)
                        .plus(
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
            downgradeDirectlyIntoArchive(task);
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

        updateOutgoingArtifacts(project, cfgs, targetVersion, publishableDevJar);

        return publishableDevJar;
    }

    private void verifyShadowModuleApplied(Project project) {
        if (!project.getPlugins()
            .hasPlugin("com.github.johnrengelman.shadow")) {
            throw new GradleException(
                "JVMDowngraderModule requires ShadowModule when usesShadowedDependencies is true.");
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

    private void updateOutgoingArtifacts(Project project, ConfigurationContainer cfgs, JavaVersion targetVersion,
        TaskProvider<? extends AbstractArchiveTask> publishableDevJar) {
        for (final String outgoingConfig : List.of("runtimeElements", "apiElements")) {
            final Configuration outgoing = cfgs.findByName(outgoingConfig);
            if (outgoing != null) {
                outgoing.getOutgoing()
                    .getArtifacts()
                    .clear();
                outgoing.getOutgoing()
                    .artifact(publishableDevJar);
                outgoing.getAttributes()
                    .attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, targetVersion.ordinal() + 1);
            }
        }

        project.getExtensions()
            .getExtraProperties()
            .set("publishableDevJar", publishableDevJar);
    }

    private void configureApiJarDowngrade(GTNHGradlePlugin.GTNHExtension gtnh, Project project,
        JavaVersion targetVersion) {
        if (gtnh.configuration.apiPackage.isEmpty()) {
            return;
        }

        final TaskContainer tasks = project.getTasks();

        tasks.named("apiJar", Jar.class)
            .configure(
                jar -> jar.getArchiveClassifier()
                    .set("api-predowngrade"));

        final TaskProvider<DowngradeJar> downgradeApiJar = tasks
            .register("downgradeApiJar", DowngradeJar.class, task -> {
                task.dependsOn(tasks.named("apiJar"));
                task.getInputFile()
                    .set(
                        tasks.named("apiJar", Jar.class)
                            .flatMap(AbstractArchiveTask::getArchiveFile));
                task.getDowngradeTo()
                    .set(targetVersion);
                task.getArchiveClassifier()
                    .set("api");
                downgradeDirectlyIntoArchive(task);
            });

        tasks.named("assemble")
            .configure(t -> t.dependsOn(downgradeApiJar));

        project.getExtensions()
            .getExtraProperties()
            .set("publishableApiJar", downgradeApiJar);
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

    /**
     * DowngradeJar writes the downgraded jar, then unpacks it and packs it again as a regular Jar task, compressing
     * every entry a second time. This downgrades into a temporary jar and copies the compressed entries into the
     * archive as they are instead.
     */
    private static void downgradeDirectlyIntoArchive(DowngradeJar task) {
        final Set<String> defaultCopyActions = ArchiveFastPath.copyActions(task);
        ArchiveFastPath.replacePackaging(task, t -> {
            final DowngradeJar downgradeJar = (DowngradeJar) t;
            if (hasArchiveCustomizations(downgradeJar, defaultCopyActions)) {
                t.getLogger()
                    .info("Custom archive rules, downgrading with the normal Jar action");
                return false;
            }
            try {
                t.getLogger()
                    .info(
                        "Downgrading directly into {}",
                        downgradeJar.getArchiveFile()
                            .get());
                final Set<URL> classpath = new HashSet<>();
                for (final File file : downgradeJar.getClasspath()
                    .getFiles()) {
                    classpath.add(
                        file.toURI()
                            .toURL());
                }
                writeDowngradedJar(
                    ToFlagsKt.toFlags(downgradeJar),
                    downgradeJar.getInputFile()
                        .get()
                        .getAsFile()
                        .toPath(),
                    classpath,
                    downgradeJar.getArchiveFile()
                        .get()
                        .getAsFile()
                        .toPath(),
                    downgradeJar.getTemporaryDir()
                        .toPath(),
                    downgradeJar.isPreserveFileTimestamps(),
                    downgradeJar.isReproducibleFileOrder());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return true;
        });
    }

    private static boolean hasArchiveCustomizations(DowngradeJar task, Set<String> defaultCopyActions) {
        // The downgraded entries are copied over with the compression they already have
        if (task.getEntryCompression() != ZipEntryCompression.DEFLATED) {
            return true;
        }
        final org.gradle.api.java.archives.Manifest manifest = task.getManifest()
            .getEffectiveManifest();
        if (!manifest.getAttributes()
            .equals(Map.of("Manifest-Version", "1.0"))
            || !manifest.getSections()
                .isEmpty()) {
            return true;
        }
        final File generatedManifest = new File(task.getTemporaryDir(), "MANIFEST.MF");
        final boolean[] customized = { false };
        task.getRootSpec()
            .walk(spec -> {
                customized[0] |= !spec.getAllIncludes()
                    .isEmpty()
                    || !spec.getAllExcludes()
                        .isEmpty()
                    || !spec.getAllIncludeSpecs()
                        .isEmpty()
                    || !spec.getAllExcludeSpecs()
                        .isEmpty()
                    || spec.getAllCopyActions()
                        .stream()
                        .anyMatch(
                            action -> !defaultCopyActions.contains(
                                action.getClass()
                                    .getName()))
                    || (!spec.getAllCopyActions()
                        .isEmpty()
                        && !spec.getDestPath()
                            .getPathString()
                            .isEmpty());
                spec.getSource()
                    .visit(details -> {
                        if (!details.isDirectory()) {
                            customized[0] |= !details.getFile()
                                .equals(generatedManifest)
                                || !spec.getDestPath()
                                    .append(details.getRelativePath())
                                    .getPathString()
                                    .equals(JarFile.MANIFEST_NAME);
                        }
                    });
            });
        return customized[0];
    }

    private static void writeDowngradedJar(Flags flags, Path input, Set<URL> classpath, Path output, Path tempDir,
        boolean preserveFileTimestamps, boolean reproducibleFileOrder) throws IOException {
        Files.createDirectories(output.getParent());
        Files.createDirectories(tempDir);
        final Path downgraded = tempDir.resolve("downgraded.jar");
        final Path repacked = tempDir.resolve("repacked.jar");
        Files.deleteIfExists(downgraded);
        Files.deleteIfExists(repacked);
        try {
            try (ClassDowngrader downgrader = ClassDowngrader.downgradeTo(flags);
                FileSystem in = Utils.openZipFileSystem(input, false);
                FileSystem out = Utils.openZipFileSystem(downgraded, true)) {
                // The manifest goes first, as tools reading jars as a stream expect. Downgrading then updates it in
                // place, the zip file system keeps the order entries were added in.
                final Path manifest = in.getPath(JarFile.MANIFEST_NAME);
                if (Files.exists(manifest)) {
                    Files.createDirectories(out.getPath("META-INF"));
                    Files.copy(manifest, out.getPath(JarFile.MANIFEST_NAME));
                }
                PathDowngrader.downgradePaths(
                    downgrader,
                    Collections.singletonList(in.getPath("/")),
                    Collections.singletonList(out.getPath("/")),
                    classpath);
            }
            repackWithoutExtraFields(downgraded, repacked, preserveFileTimestamps, reproducibleFileOrder);
            Files.move(repacked, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(downgraded);
            Files.deleteIfExists(repacked);
        }
    }

    /**
     * Copies a jar entry by entry, keeping the compressed data as it is and dropping the per-entry extra fields. The
     * zip file system writes a 36 byte NTFS timestamp field per entry on Windows, which nothing reading a mod jar
     * uses and which added over 2 MB to a GT5U sized jar.
     */
    static void repackWithoutExtraFields(Path source, Path target, boolean preserveFileTimestamps,
        boolean reproducibleFileOrder) throws IOException {
        try (ZipFile in = new ZipFile(source); ZipArchiveOutputStream out = new ZipArchiveOutputStream(target)) {
            final List<ZipArchiveEntry> entries = Collections.list(in.getEntries());
            if (reproducibleFileOrder) {
                entries.sort(
                    Comparator.comparing(
                        (ZipArchiveEntry entry) -> !entry.getName()
                            .equals(JarFile.MANIFEST_NAME))
                        .thenComparing(ZipArchiveEntry::getName));
            }
            for (final ZipArchiveEntry entry : entries) {
                final ZipArchiveEntry copy = new ZipArchiveEntry(entry);
                if (!preserveFileTimestamps) {
                    copy.setTime(ZipEntryConstants.CONSTANT_TIME_FOR_ZIP_ENTRIES);
                }
                copy.setExtraFields(new ZipExtraField[0]);
                if (entry.isDirectory()) {
                    out.putArchiveEntry(copy);
                    out.closeArchiveEntry();
                } else {
                    try (InputStream raw = in.getRawInputStream(entry)) {
                        out.addRawArchiveEntry(copy, raw);
                    }
                }
            }
        }
    }

    private static boolean isIntermediateJar(File f) {
        final String name = f.getName();
        return name.contains("-dev-preshadow.") || name.contains("-predowngrade.") || name.contains("-downgraded.");
    }

    private static final String VALIDATE_LOMBOK_TASK = "validateLombokForJava25";

    private void validateLombokVersion(GTNHGradlePlugin.GTNHExtension gtnh, Project project,
        int effectiveToolchainVersion, Set<Integer> multiReleaseVersions) {
        final String mixinSourceSetName = gtnh.configuration.separateMixinSourceSet;
        final Configuration annotationProcessor = project.getConfigurations()
            .getByName("annotationProcessor");
        final Configuration mixinAP = mixinSourceSetName.isEmpty() ? null
            : project.getConfigurations()
                .findByName(mixinSourceSetName + "AnnotationProcessor");

        final boolean targetsJava25Plus = effectiveToolchainVersion >= 25 || multiReleaseVersions.stream()
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
            final String mixinCompileTask = "compile" + StringUtils.capitalize(mixinSourceSetName) + "Java";
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
