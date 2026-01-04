package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.retrofuturagradle.shadow.com.google.common.collect.ImmutableList;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.gtnhgradle.UpdateableConstants;
import com.gtnewhorizons.gtnhgradle.tasks.RunHotswappableMinecraftTask;
import com.gtnewhorizons.gtnhgradle.tasks.SetupHotswapAgentTask;
import com.gtnewhorizons.retrofuturagradle.shadow.com.google.common.collect.ImmutableMap;
import com.gtnewhorizons.retrofuturagradle.util.Distribution;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Support module for modern Java runs via lwjgl3ify */
public abstract class ModernJavaModule implements GTNHModule {

    /** Default Java 17 JVM arguments */
    public final String[] JAVA_17_ARGS = new String[] { "-Dfile.encoding=UTF-8",
        "-Djava.system.class.loader=com.gtnewhorizons.retrofuturabootstrap.RfbSystemClassLoader",
        "--enable-native-access", "ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED", "--add-opens",
        "java.base/java.lang.invoke=ALL-UNNAMED", "--add-opens", "java.base/java.lang.ref=ALL-UNNAMED", "--add-opens",
        "java.base/java.lang.reflect=ALL-UNNAMED", "--add-opens", "java.base/java.lang=ALL-UNNAMED", "--add-opens",
        "java.base/java.net.spi=ALL-UNNAMED", "--add-opens", "java.base/java.net=ALL-UNNAMED", "--add-opens",
        "java.base/java.nio.channels=ALL-UNNAMED", "--add-opens", "java.base/java.nio.charset=ALL-UNNAMED",
        "--add-opens", "java.base/java.nio.file=ALL-UNNAMED", "--add-opens", "java.base/java.nio=ALL-UNNAMED",
        "--add-opens", "java.base/java.text=ALL-UNNAMED", "--add-opens", "java.base/java.time.chrono=ALL-UNNAMED",
        "--add-opens", "java.base/java.time.format=ALL-UNNAMED", "--add-opens",
        "java.base/java.time.temporal=ALL-UNNAMED", "--add-opens", "java.base/java.time.zone=ALL-UNNAMED",
        "--add-opens", "java.base/java.time=ALL-UNNAMED", "--add-opens",
        "java.base/java.util.concurrent.atomic=ALL-UNNAMED", "--add-opens",
        "java.base/java.util.concurrent.locks=ALL-UNNAMED", "--add-opens", "java.base/java.util.jar=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.zip=ALL-UNNAMED", "--add-opens", "java.base/java.util=ALL-UNNAMED",
        "--add-opens", "java.base/jdk.internal.loader=ALL-UNNAMED", "--add-opens",
        "java.base/jdk.internal.misc=ALL-UNNAMED", "--add-opens", "java.base/jdk.internal.ref=ALL-UNNAMED",
        "--add-opens", "java.base/jdk.internal.reflect=ALL-UNNAMED", "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.desktop/com.sun.imageio.plugins.png=ALL-UNNAMED", "--add-opens",
        "java.desktop/sun.awt.image=ALL-UNNAMED", "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED", "--add-opens",
        "java.desktop/sun.lwawt.macosx=ALL-UNNAMED", "--add-opens",
        "java.sql.rowset/javax.sql.rowset.serial=ALL-UNNAMED", "--add-opens",
        "jdk.dynalink/jdk.dynalink.beans=ALL-UNNAMED", "--add-opens",
        "jdk.naming.dns/com.sun.jndi.dns=ALL-UNNAMED,java.naming", };
    /** Default Java HotSwapAgent JVM arguments */
    public final String[] HOTSWAP_JVM_ARGS = new String[] {
        // DCEVM advanced hot reload
        "-XX:+AllowEnhancedClassRedefinition", "-XX:HotswapAgent=fatjar" };

    /** @return Gradle-provided */
    @Inject
    public abstract JavaToolchainService getToolchainService();

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleModernJava;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final String minGtnhLib = UpdateableConstants.NEWEST_GTNH_LIB;
        final ConfigurationContainer cfgs = project.getConfigurations();
        final DependencyHandler deps = project.getDependencies();
        final TaskContainer tasks = project.getTasks();
        final ExtraPropertiesExtension ext = project.getExtensions()
            .getExtraProperties();

        for (final String configuration : ImmutableList
            .of("implementation", "runtimeOnly", "devOnlyNonPublishable", "runtimeOnlyNonPublishable")) {
            deps.getConstraints()
                .add(configuration, minGtnhLib)
                .because("fixes duplicate mod errors in java 17 configurations using old gtnhlib");
        }

        final Action<JavaToolchainSpec> java17Toolchain = (spec) -> {
            spec.getLanguageVersion()
                .set(JavaLanguageVersion.of(17));
            spec.getVendor()
                .set(JvmVendorSpec.JETBRAINS); // for enhanced HotSwap
        };
        ext.set("java17Toolchain", java17Toolchain);
        final Action<JavaToolchainSpec> java21Toolchain = (spec) -> {
            spec.getLanguageVersion()
                .set(JavaLanguageVersion.of(21));
            spec.getVendor()
                .set(JvmVendorSpec.JETBRAINS); // for enhanced HotSwap
        };
        ext.set("java21Toolchain", java21Toolchain);
        final Action<JavaToolchainSpec> java25Toolchain = (spec) -> {
            spec.getLanguageVersion()
                .set(JavaLanguageVersion.of(25));
            spec.getVendor()
                .set(JvmVendorSpec.JETBRAINS); // for enhanced HotSwap
        };
        ext.set("java25Toolchain", java25Toolchain);
        final Configuration java17DependenciesCfg = cfgs.create("java17Dependencies", c -> {
            c.extendsFrom(cfgs.getByName("runtimeClasspath"));
            c.setCanBeResolved(true);
            c.setCanBeConsumed(false);
        });
        ext.set("java17DependenciesCfg", java17DependenciesCfg);
        final Configuration java17PatchDependenciesCfg = cfgs.create("java17PatchDependencies", c -> {
            c.setCanBeResolved(true);
            c.setCanBeConsumed(false);
        });
        ext.set("java17PatchDependenciesCfg", java17PatchDependenciesCfg);

        if (!gtnh.configuration.modId.equals("lwjgl3ify")) {
            final ModuleDependency lwjgl3ify = (ModuleDependency) deps
                .add(java17DependenciesCfg.getName(), UpdateableConstants.NEWEST_LWJGL3IFY);
            ((ModuleDependency) deps
                .add(java17PatchDependenciesCfg.getName(), UpdateableConstants.NEWEST_LWJGL3IFY + ":forgePatches"))
                .setTransitive(false);
            if (gtnh.configuration.modId.equals("gtnhlib")) {
                lwjgl3ify.exclude(ImmutableMap.of("module", "GTNHLib"));
            }
        }
        if (!gtnh.configuration.modId.equals("hodgepodge")) {
            final ModuleDependency hodgepodge = (ModuleDependency) deps
                .add(java17DependenciesCfg.getName(), UpdateableConstants.NEWEST_HODGEPODGE);
            if (gtnh.configuration.modId.equals("gtnhlib")) {
                hodgepodge.exclude(ImmutableMap.of("module", "GTNHLib"));
            }
        }
        deps.getConstraints()
            .add(java17DependenciesCfg.getName(), UpdateableConstants.NEWEST_UNIMIXINS)
            .because("Use latest UniMixins known to GTNHGradle.");

        final List<String> java17JvmArgs = new ArrayList<>(Arrays.asList(JAVA_17_ARGS));
        final List<String> hotswapJvmArgs = new ArrayList<>(Arrays.asList(HOTSWAP_JVM_ARGS));
        ext.set("modernJvmArgs", java17JvmArgs);
        ext.set("hotswapJvmArgs", hotswapJvmArgs);

        final TaskProvider<SetupHotswapAgentTask> setupHotswapAgent17 = tasks
            .register("setupHotswapAgent17", SetupHotswapAgentTask.class, t -> {
                t.setTargetForToolchain(java17Toolchain);
            });
        ext.set("setupHotswapAgentTask", setupHotswapAgent17);

        final TaskProvider<SetupHotswapAgentTask> setupHotswapAgent21 = tasks
            .register("setupHotswapAgent21", SetupHotswapAgentTask.class, t -> {
                t.setTargetForToolchain(java21Toolchain);
            });

        final TaskProvider<SetupHotswapAgentTask> setupHotswapAgent25 = tasks
            .register("setupHotswapAgent25", SetupHotswapAgentTask.class, t -> {
                t.setTargetForToolchain(java25Toolchain);
            });

        final TaskProvider<RunHotswappableMinecraftTask> runClient17Task = tasks
            .register("runClient17", RunHotswappableMinecraftTask.class, Distribution.CLIENT, "runClient");
        runClient17Task.configure(t -> {
            t.dependsOn(setupHotswapAgent17);
            t.setup(project, gtnh);
            t.getJavaLauncher()
                .set(getToolchainService().launcherFor(java17Toolchain));
            t.setWorkingDir(gtnh.configuration.runClientDirectory);
        });
        final TaskProvider<RunHotswappableMinecraftTask> runServer17Task = tasks
            .register("runServer17", RunHotswappableMinecraftTask.class, Distribution.DEDICATED_SERVER, "runServer");
        runServer17Task.configure(t -> {
            t.dependsOn(setupHotswapAgent17);
            t.setup(project, gtnh);
            t.getJavaLauncher()
                .set(getToolchainService().launcherFor(java17Toolchain));
            t.setWorkingDir(gtnh.configuration.runServerDirectory);
        });

        final TaskProvider<RunHotswappableMinecraftTask> runClient21Task = tasks
            .register("runClient21", RunHotswappableMinecraftTask.class, Distribution.CLIENT, "runClient");
        runClient21Task.configure(t -> {
            t.dependsOn(setupHotswapAgent21);
            t.setup(project, gtnh);
            t.getJavaLauncher()
                .set(getToolchainService().launcherFor(java21Toolchain));
            t.setWorkingDir(gtnh.configuration.runClientDirectory);
        });
        final TaskProvider<RunHotswappableMinecraftTask> runServer21Task = tasks
            .register("runServer21", RunHotswappableMinecraftTask.class, Distribution.DEDICATED_SERVER, "runServer");
        runServer21Task.configure(t -> {
            t.dependsOn(setupHotswapAgent21);
            t.setup(project, gtnh);
            t.getJavaLauncher()
                .set(getToolchainService().launcherFor(java21Toolchain));
            t.setWorkingDir(gtnh.configuration.runServerDirectory);
        });

        final TaskProvider<RunHotswappableMinecraftTask> runClient25Task = tasks
            .register("runClient25", RunHotswappableMinecraftTask.class, Distribution.CLIENT, "runClient");
        runClient25Task.configure(t -> {
            t.dependsOn(setupHotswapAgent25);
            t.setup(project, gtnh);
            t.getJavaLauncher()
                .set(getToolchainService().launcherFor(java25Toolchain));
            t.setWorkingDir(gtnh.configuration.runClientDirectory);
        });
        final TaskProvider<RunHotswappableMinecraftTask> runServer25Task = tasks
            .register("runServer25", RunHotswappableMinecraftTask.class, Distribution.DEDICATED_SERVER, "runServer");
        runServer25Task.configure(t -> {
            t.dependsOn(setupHotswapAgent25);
            t.setup(project, gtnh);
            t.getJavaLauncher()
                .set(getToolchainService().launcherFor(java25Toolchain));
            t.setWorkingDir(gtnh.configuration.runServerDirectory);
        });

        final int forcedToolchain = gtnh.configuration.forceToolchainVersion;
        if (forcedToolchain != -1) {
            final List<String> modernJvmArgs = java17JvmArgs;
            ext.set("modernJvmArgs", modernJvmArgs);

            final Action<JavaToolchainSpec> javaXToolchain = (spec) -> {
                spec.getLanguageVersion()
                    .set(JavaLanguageVersion.of(forcedToolchain));
                spec.getVendor()
                    .set(JvmVendorSpec.JETBRAINS); // for enhanced HotSwap
            };
            ext.set("javaXToolchain", javaXToolchain);

            final TaskProvider<SetupHotswapAgentTask> setupHotswapAgentX = tasks.register(
                "setupHotswapAgentX",
                SetupHotswapAgentTask.class,
                t -> t.setTargetForToolchain(javaXToolchain));

            final TaskProvider<RunHotswappableMinecraftTask> runClientXTask = tasks
                .register("runClientX", RunHotswappableMinecraftTask.class, Distribution.CLIENT, "runClient");
            runClientXTask.configure(t -> {
                t.dependsOn(setupHotswapAgentX);
                t.setup(project, gtnh);
                t.getJavaLauncher()
                    .set(getToolchainService().launcherFor(javaXToolchain));
                t.setWorkingDir(gtnh.configuration.runClientDirectory);
            });
            final TaskProvider<RunHotswappableMinecraftTask> runServerXTask = tasks
                .register("runServerX", RunHotswappableMinecraftTask.class, Distribution.DEDICATED_SERVER, "runServer");
            runServerXTask.configure(t -> {
                t.dependsOn(setupHotswapAgentX);
                t.setup(project, gtnh);
                t.getJavaLauncher()
                    .set(getToolchainService().launcherFor(javaXToolchain));
                t.setWorkingDir(gtnh.configuration.runServerDirectory);
            });
        }
    }
}
