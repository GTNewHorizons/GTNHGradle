package com.gtnewhorizons.gtnhgradle.tasks;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.retrofuturagradle.MinecraftExtension;
import com.gtnewhorizons.retrofuturagradle.mcp.MCPTasks;
import com.gtnewhorizons.retrofuturagradle.minecraft.MinecraftTasks;
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask;
import com.gtnewhorizons.retrofuturagradle.util.Distribution;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.options.Option;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Helper for running Minecraft with modern Java */
public abstract class RunHotswappableMinecraftTask extends RunMinecraftTask {

    /** The distribution this task runs */
    public final Distribution side;
    /** The task name this task inherits the classpath from */
    public final String superTask;

    /** @return Enables HotSwapAgent for enhanced class reloading under a debugger */
    @Input
    @Option(option = "hotswap", description = "Enables HotSwapAgent for enhanced class reloading under a debugger")
    public abstract Property<Boolean> getEnableHotswap();

    /**
     * For dependency injection
     *
     * @param side      Distribution to launch
     * @param superTask Task to inherit the classpath from
     * @param gradle    The Gradle instance, auto-injected
     */
    @Inject
    public RunHotswappableMinecraftTask(Distribution side, String superTask, Gradle gradle) {
        super(side, gradle);

        this.side = side;
        this.superTask = superTask;
        setGroup("Modded Minecraft");
        setDescription(
            "Runs the modded " + side.name()
                .toLowerCase(Locale.ROOT) + " using modern Java, lwjgl3ify and Hodgepodge");
        // IntelliJ doesn't seem to allow pre-set commandline arguments, so we also support an env variable
        getEnableHotswap().convention(Boolean.parseBoolean(System.getenv("HOTSWAP")));

        this.getLwjglVersion()
            .set(3);
    }

    /**
     * Sets up the task for the given project.
     *
     * @param project The project object
     * @param gtnh    Configuration to use
     */
    @SuppressWarnings("unchecked")
    public void setup(Project project, GTNHGradlePlugin.GTNHExtension gtnh) {
        final MinecraftExtension minecraft = project.getExtensions()
            .getByType(MinecraftExtension.class);
        final MCPTasks mcpTasks = project.getExtensions()
            .getByType(MCPTasks.class);
        final MinecraftTasks mcTasks = project.getExtensions()
            .getByType(MinecraftTasks.class);

        this.getExtraJvmArgs()
            .addAll((List<String>) Objects.requireNonNull(project.property("java17JvmArgs")));
        this.getExtraJvmArgs()
            .addAll(
                getEnableHotswap().map(
                    enable -> enable ? (List<String>) project.property("hotswapJvmArgs") : Collections.emptyList()));

        this.classpath(project.property("java17PatchDependenciesCfg"));
        this.classpath(mcpTasks.getTaskPackageMcLauncher());
        this.classpath(mcpTasks.getTaskPackagePatchedMc());
        this.classpath(mcpTasks.getPatchedConfiguration());
        this.classpath(
            project.getTasks()
                .named("jar"));
        this.classpath(project.property("java17DependenciesCfg"));

        super.setup(project);

        dependsOn(
            mcpTasks.getLauncherSources()
                .getClassesTaskName(),
            mcTasks.getTaskDownloadVanillaAssets(),
            mcpTasks.getTaskPackagePatchedMc(),
            "jar");
        getMainClass().set((side == Distribution.CLIENT) ? "GradleStart" : "GradleStartServer");
        getMcVersion().set(gtnh.configuration.minecraftVersion);
        getUsername().set(minecraft.getUsername());
        getUserUUID().set(minecraft.getUserUUID());
        if (side == Distribution.DEDICATED_SERVER) {
            getExtraArgs().add("nogui");
        }

        // Use RFB alternate main class
        systemProperty("gradlestart.bouncerClient", "com.gtnewhorizons.retrofuturabootstrap.Main");
        systemProperty("gradlestart.bouncerServer", "com.gtnewhorizons.retrofuturabootstrap.Main");

        if (gtnh.configuration.usesMixins) {
            final String mixinSpec = gtnh.minecraftVersion.mixinProviderSpec;
            this.getExtraJvmArgs()
                .addAll(project.provider(() -> {
                    final Configuration mixinCfg = project.getConfigurations()
                        .detachedConfiguration(
                            project.getDependencies()
                                .create(mixinSpec));
                    mixinCfg.setCanBeConsumed(false);
                    mixinCfg.setCanBeResolved(true);
                    mixinCfg.setTransitive(false);
                    return getEnableHotswap().get() ? Collections.singletonList(
                        "-javaagent:" + mixinCfg.getSingleFile()
                            .getAbsolutePath())
                        : Collections.emptyList();
                }));
        }
    }
}
