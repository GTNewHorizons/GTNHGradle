package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.modules.ideintegration.IdeaMiscXmlUpdater;
import com.gtnewhorizons.retrofuturagradle.shadow.com.google.common.collect.ImmutableList;
import com.gtnewhorizons.retrofuturagradle.shadow.com.google.common.collect.ImmutableMap;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask;
import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseJdt;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.IdeaProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.gradle.ext.ActionDelegationConfig;
import org.jetbrains.gradle.ext.Application;
import org.jetbrains.gradle.ext.Gradle;
import org.jetbrains.gradle.ext.IdeaCompilerConfiguration;
import org.jetbrains.gradle.ext.IdeaExtPlugin;
import org.jetbrains.gradle.ext.ProjectSettings;
import org.jetbrains.gradle.ext.RunConfigurationContainer;

import java.io.File;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Provides better integration for IntelliJ and Eclipse */
public class IdeIntegrationModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleIdeIntegration;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {

        project.getPluginManager()
            .apply(IdeaExtPlugin.class);
        project.getPluginManager()
            .apply(EclipsePlugin.class);

        final TaskContainer tasks = project.getTasks();
        final EclipseModel eclipse = project.getExtensions()
            .getByType(EclipseModel.class);
        eclipse.getClasspath()
            .setDownloadSources(true);
        eclipse.getClasspath()
            .setDownloadJavadoc(true);
        final EclipseJdt ejdt = eclipse.getJdt();
        ejdt.setTargetCompatibility(JavaVersion.VERSION_1_8);
        ejdt.setJavaRuntimeName("JavaSE-1.8");
        if (gtnh.configuration.forceToolchainVersion > 8) {
            ejdt.setSourceCompatibility(JavaVersion.toVersion(gtnh.configuration.forceToolchainVersion));
        } else if (gtnh.configuration.enableModernJavaSyntax) {
            ejdt.setSourceCompatibility(JavaVersion.VERSION_17);
        } else {
            ejdt.setSourceCompatibility(JavaVersion.VERSION_1_8);
        }

        final IdeaModel idea = project.getExtensions()
            .getByType(IdeaModel.class);
        idea.getModule()
            .setDownloadSources(true);
        idea.getModule()
            .setDownloadJavadoc(true);
        idea.getModule()
            .setInheritOutputDirs(true);
        final IdeaProject ideaProject = idea.getProject();
        final ProjectSettings ideaExt = getExt(ideaProject, ProjectSettings.class);
        final ActionDelegationConfig delegationConfig = getExt(ideaExt, ActionDelegationConfig.class);
        final IdeaCompilerConfiguration compiler = getExt(ideaExt, IdeaCompilerConfiguration.class);
        final RunConfigurationContainer runs = getExt(ideaExt, RunConfigurationContainer.class);

        if (!gtnh.configuration.ideaOverrideBuildType.isEmpty()) {
            switch (gtnh.configuration.ideaOverrideBuildType.toLowerCase(Locale.ROOT)) {
                case "gradle":
                    delegationConfig.setDelegateBuildRunToGradle(true);
                    delegationConfig.setTestRunner(ActionDelegationConfig.TestRunner.GRADLE);
                    break;
                case "idea":
                    delegationConfig.setDelegateBuildRunToGradle(false);
                    delegationConfig.setTestRunner(ActionDelegationConfig.TestRunner.PLATFORM);
                    break;
                default:
                    throw new GradleException("Accepted value for ideaOverrideBuildType is one of gradle or idea.");
            }
        }

        compiler.getJavac()
            .setJavacAdditionalOptions("-encoding utf8");
        compiler.getJavac()
            .setModuleJavacAdditionalOptions(
                ImmutableMap.of(
                    project.getName() + ".main",
                    quotedJoin(
                        tasks.named("compileJava", JavaCompile.class)
                            .get()
                            .getOptions()
                            .getAllCompilerArgs())));

        runs.register("0. Build and Test", Gradle.class, run -> { run.setTaskNames(ImmutableList.of("build")); });
        runs.register("1. Run Client", Gradle.class, run -> { run.setTaskNames(ImmutableList.of("runClient")); });
        runs.register("2. Run Server", Gradle.class, run -> { run.setTaskNames(ImmutableList.of("runServer")); });
        if (gtnh.configuration.moduleModernJava) {
            char suffix = 'a';
            for (final int javaVer : ImmutableList.of(17, 21, 25)) {
                final char mySuffix = suffix;
                final char myHsSuffix = (char) (suffix + 1);
                suffix += 2;
                runs.register("1" + mySuffix + ". Run Client (Java " + javaVer + ")", Gradle.class, run -> {
                    run.setTaskNames(ImmutableList.of("runClient" + javaVer));
                });
                runs.register("2" + mySuffix + ". Run Server (Java " + javaVer + ")", Gradle.class, run -> {
                    run.setTaskNames(ImmutableList.of("runServer" + javaVer));
                });
                runs.register("1" + myHsSuffix + ". Run Client (Java " + javaVer + ", Hotswap)", Gradle.class, run -> {
                    run.setTaskNames(ImmutableList.of("runClient" + javaVer));
                    run.setEnvs(ImmutableMap.of("HOTSWAP", "true"));
                });
                runs.register("2" + myHsSuffix + ". Run Server (Java " + javaVer + ", Hotswap)", Gradle.class, run -> {
                    run.setTaskNames(ImmutableList.of("runServer" + javaVer));
                    run.setEnvs(ImmutableMap.of("HOTSWAP", "true"));
                });
            }
        }
        runs.register(
            "3. Run Obfuscated Client",
            Gradle.class,
            run -> { run.setTaskNames(ImmutableList.of("runObfClient")); });
        runs.register(
            "4. Run Obfuscated Server",
            Gradle.class,
            run -> { run.setTaskNames(ImmutableList.of("runObfServer")); });
        if (!gtnh.configuration.disableSpotless) {
            runs.register(
                "5. Apply spotless",
                Gradle.class,
                run -> { run.setTaskNames(ImmutableList.of("spotlessApply")); });
        }

        final var ijClientRun = runs.register("Run Client (IJ Native)", Application.class, run -> {
            run.setMainClass("GradleStart");
            run.setModuleName(project.getName() + ".ideVirtualMain");
        });
        project.afterEvaluate(_p -> {
            final RunMinecraftTask runClient = tasks.named("runClient", RunMinecraftTask.class)
                .get();
            final var run = ijClientRun.get();
            run.setWorkingDirectory(
                runClient.getWorkingDir()
                    .getAbsolutePath());
            run.setProgramParameters(quotedJoin(runClient.calculateArgs()));
            run.setJvmArgs(
                quotedJoin(runClient.calculateJvmArgs()) + ' ' + quotedPropJoin(runClient.getSystemProperties()));
        });

        final var ijServerRun = runs.register("Run Server (IJ Native)", Application.class, run -> {
            run.setMainClass("GradleStartServer");
            run.setModuleName(project.getName() + ".ideVirtualMain");
        });
        project.afterEvaluate(_p -> {
            final RunMinecraftTask runServer = tasks.named("runServer", RunMinecraftTask.class)
                .get();
            final var run = ijServerRun.get();
            run.setWorkingDirectory(
                runServer.getWorkingDir()
                    .getAbsolutePath());
            run.setProgramParameters(quotedJoin(runServer.calculateArgs()));
            run.setJvmArgs(
                quotedJoin(runServer.calculateJvmArgs()) + ' ' + quotedPropJoin(runServer.getSystemProperties()));
        });

        ideaExt.withIDEADir(ideaDir -> {
            try {
                if (!ideaDir.getPath()
                    .contains(".idea")) {
                    // If an .ipr file exists, the project root directory is passed here instead of the .idea
                    // subdirectory
                    ideaDir = new File(ideaDir, ".idea");
                }
                if (ideaDir.isDirectory()) {
                    IdeaMiscXmlUpdater.mergeOrCreate(
                        ideaDir.toPath()
                            .resolve("misc.xml"));
                }
            } catch (Throwable e) {
                if (e instanceof RuntimeException ex) {
                    throw ex;
                } else {
                    throw new RuntimeException(e);
                }
            }
        });

        tasks.named("processIdeaSettings")
            .configure(t -> { t.dependsOn("injectTags", "setupDecompWorkspace"); });

        tasks.named("ideVirtualMainClasses")
            .configure(t -> {
                // Make IntelliJ "Build project" build the mod jars
                t.dependsOn("jar", "reobfJar");
                if (gtnh.configuration.moduleCodeStyle && !gtnh.configuration.disableSpotless
                    && gtnh.configuration.ideaCheckSpotlessOnBuild) {
                    t.dependsOn("spotlessCheck");
                }
            });
    }

    private String quotedJoin(Collection<String> collection) {
        return collection.stream()
            .map(a -> '"' + a + '"')
            .collect(Collectors.joining(" "));
    }

    private String quotedPropJoin(Map<String, ?> collection) {
        return collection.entrySet()
            .stream()
            .map(a -> "\"-D" + a.getKey() + '=' + a.getValue() + '"')
            .collect(Collectors.joining(" "));
    }

    private <T> T getExt(Object o, Class<T> type) {
        return ((ExtensionAware) o).getExtensions()
            .getByType(type);
    }
}
