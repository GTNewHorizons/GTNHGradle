package com.gtnewhorizons.gtnhgradle.tasks;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.UpdateableConstants;
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
            .addAll((String) project.property("java17JvmArgs"));
        this.getExtraJvmArgs()
            .addAll(
                getEnableHotswap().map(
                    enable -> enable ? (List<String>) project.property("hotswapJvmArgs") : Collections.emptyList()));

        this.classpath(project.property("java17PatchDependenciesCfg"));
        if (side == Distribution.CLIENT) {
            this.classpath(mcTasks.getLwjgl3Configuration());
        }
        // Use a raw provider instead of map to not create a dependency on the task
        this.classpath(
            project.provider(
                () -> project.getTasks()
                    .named(superTask, RunMinecraftTask.class)
                    .get()
                    .getClasspath()));
        this.classpath(project.property("java17DependenciesCfg"));

        super.setup(project);

        this.setClasspath(
            this.getClasspath()
                .filter(
                    file -> !file.getPath()
                        .contains("2.9.4-nightly-20150209")));

        dependsOn(
            mcpTasks.getLauncherSources()
                .getClassesTaskName(),
            mcTasks.getTaskDownloadVanillaAssets(),
            mcpTasks.getTaskPackagePatchedMc(),
            "jar");
        getMainClass().set((side == Distribution.CLIENT) ? "GradleStart" : "GradleStartServer");
        getUsername().set(minecraft.getUsername());
        getUserUUID().set(minecraft.getUserUUID());
        if (side == Distribution.DEDICATED_SERVER) {
            getExtraArgs().add("nogui");
        }

        if (gtnh.configuration.usesMixins) {
            this.getExtraJvmArgs()
                .addAll(project.provider(() -> {
                    final Configuration mixinCfg = project.getConfigurations()
                        .detachedConfiguration(
                            project.getDependencies()
                                .create(UpdateableConstants.NEWEST_UNIMIXINS));
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
