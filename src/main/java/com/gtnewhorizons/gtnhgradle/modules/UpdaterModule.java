package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.BuildConfig;
import com.gtnewhorizons.gtnhgradle.GTNHConstants;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.gtnhgradle.UpdateableConstants;
import com.gtnewhorizons.gtnhgradle.tasks.UpdateBuildscriptTask;
import com.gtnewhorizons.gtnhgradle.tasks.UpdateDependenciesTask;
import com.gtnewhorizons.gtnhgradle.tasks.V2UpgradeTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Allows automatic buildscript updates */
public class UpdaterModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleUpdater;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {

        // Do nothing for nested projects for safety
        if (project.getRootProject() != project) {
            return;
        }

        // Use the buildscript configuration to rely on the pluginManagement block
        final Configuration latestBuildscriptConfig = project.getBuildscript()
            .getConfigurations()
            .detachedConfiguration(
                project.getDependencies()
                    .create(UpdateableConstants.NEWEST_GTNHGRADLE_SPEC));
        latestBuildscriptConfig.setDescription("Buildscript updater resolver");
        latestBuildscriptConfig.setCanBeConsumed(false);
        latestBuildscriptConfig.setCanBeResolved(true);
        latestBuildscriptConfig.setTransitive(false);
        latestBuildscriptConfig.getResolutionStrategy()
            .cacheDynamicVersionsFor(10, TimeUnit.SECONDS);

        final Provider<ResolvedConfiguration> latestResolver = project.getProviders()
            .provider(latestBuildscriptConfig::getResolvedConfiguration);
        final Provider<ResolvedArtifact> latestPluginArtifact = latestResolver.map(
            r -> r.getResolvedArtifacts()
                .iterator()
                .next());
        final Provider<String> latestPluginVersion = latestPluginArtifact.map(
            a -> a.getModuleVersion()
                .getId()
                .getVersion());

        final boolean isOffline = project.getGradle()
            .getStartParameter()
            .isOffline();
        final boolean disableCheck = Boolean.getBoolean("DISABLE_BUILDSCRIPT_UPDATE_CHECK");

        // Run update checker after the buildscript might want to mess with it.
        project.afterEvaluate(_p -> {
            if (!isOffline && !disableCheck) {
                // Check for updates automatically
                final ResolvedConfiguration rc = latestResolver.get();
                final String latestUpdate = latestPluginVersion.get();
                if (!latestUpdate.equals(BuildConfig.VERSION)) {
                    gtnh.logger.warn(
                        "Build script update from {} to {} available! Run ./gradlew updateBuildScript",
                        BuildConfig.VERSION,
                        latestUpdate);
                }
            }
        });

        if (!isOffline) {
            gtnh.logger.warn(
                "Build script major version upgrade from {} to 2.x available! Run ./gradlew upgradeBuildScriptMajor",
                BuildConfig.VERSION);
        }

        final TaskContainer tasks = project.getTasks();
        tasks.named("wrapper", Wrapper.class)
            .configure(t -> {
                t.doFirst(inner -> {
                    final Wrapper tInner = (Wrapper) inner;
                    final String version = getGradleVersionFromPlugin(latestPluginArtifact.get());
                    inner.getLogger()
                        .lifecycle("[GTNH] Setting wrapper's Gradle version to {}", version);
                    // Get latest Gradle version from the future jar
                    tInner.setGradleVersion(version);
                });
            });

        final File settingsGradle = ((File) Objects.requireNonNull(
            project.getExtensions()
                .getExtraProperties()
                .get(GTNHConstants.SETTINGS_GRADLE_FILE_PROPERTY))).getAbsoluteFile();
        final File rootDir = settingsGradle.getParentFile();
        final File propertiesGradle = new File(rootDir, "gradle.properties");

        if (!propertiesGradle.exists()) {
            gtnh.logger
                .error("Could not register buildscript updater, gradle.properties not found at {}", propertiesGradle);
            return;
        }

        tasks.register("updateBuildScript", UpdateBuildscriptTask.class, t -> {
            t.getSettingsGradle()
                .set(
                    project.getLayout()
                        .file(project.provider(() -> settingsGradle)));
            t.getPropertiesGradle()
                .set(
                    project.getLayout()
                        .file(project.provider(() -> propertiesGradle)));
            t.getNewestVersion()
                .set(latestPluginVersion);
            t.getNewestVersionJar()
                .set(
                    project.getLayout()
                        .file(latestPluginArtifact.map(ResolvedArtifact::getFile)));
        });

        final File dependenciesGradle = new File(rootDir, "dependencies.gradle");

        tasks.register(
            "updateDependencies",
            UpdateDependenciesTask.class,
            t -> t.getDependenciesGradle()
                .set(
                    project.getLayout()
                        .file(project.provider(() -> dependenciesGradle))));

        final TaskProvider<Wrapper> v2WrapperTask = tasks.register("buildScriptV2Wrapper", Wrapper.class, t -> {
            t.setGroup("GTNH Buildscript Internal");
            t.setGradleVersion("9.2.1");
            t.getValidateDistributionUrl()
                .set(true);
            t.getNetworkTimeout()
                .set(30_000);
        });

        @SuppressWarnings("UnstableApiUsage")
        final TaskProvider<UpdateDaemonJvm> v2DaemonJvmTask = tasks
            .register("buildScriptV2DaemonJvm", UpdateDaemonJvm.class, t -> {
                @SuppressWarnings("UnstableApiUsage")
                final UpdateDaemonJvm mainDaemonJvmTask = tasks.named("updateDaemonJvm", UpdateDaemonJvm.class)
                    .get();
                t.setGroup("GTNH Buildscript Internal");
                t.getPropertiesFile()
                    .set(
                        project.getLayout()
                            .getProjectDirectory()
                            .file("gradle/gradle-daemon-jvm.properties"));
                t.getLanguageVersion()
                    .set(JavaLanguageVersion.of(25));
                t.getNativeImageCapable()
                    .set(false);
                t.getToolchainPlatforms()
                    .set(mainDaemonJvmTask.getToolchainPlatforms());
                t.getToolchainDownloadUrls()
                    .set(mainDaemonJvmTask.getToolchainDownloadUrls());
            });

        tasks.register("upgradeBuildScriptMajor", V2UpgradeTask.class, t -> {
            t.setGroup("GTNH Buildscript");
            t.finalizedBy(v2WrapperTask, v2DaemonJvmTask);
            t.getSettingsGradle()
                .set(
                    project.getLayout()
                        .file(project.provider(() -> settingsGradle)));
            t.getPropertiesGradle()
                .set(
                    project.getLayout()
                        .file(project.provider(() -> propertiesGradle)));
        });
    }

    private static String getGradleVersionFromPlugin(final ResolvedArtifact artifact) {
        final File jar = artifact.getFile();
        final URL jarUrl;
        try {
            jarUrl = jar.toURI()
                .toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        try (final URLClassLoader jarLoader = new URLClassLoader(new URL[] { jarUrl }, null)) {
            final Class<?> latestConstants = Class
                .forName("com.gtnewhorizons.gtnhgradle.UpdateableConstants", true, jarLoader);
            final Field latestGradle = latestConstants.getField("NEWEST_GRADLE_VERSION");
            return latestGradle.get(null)
                .toString();
        } catch (IOException | ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
