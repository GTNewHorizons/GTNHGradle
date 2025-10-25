package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask;
import com.gtnewhorizons.retrofuturagradle.util.Distribution;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

public class RunTaskWorkDirModule implements GTNHModule {

    public static final String CLIENT_RUN_TASK_WORK_DIR = "run/client";
    public static final String SERVER_RUN_TASK_WORK_DIR = "run/server";

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleRunTaskWorkDir;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        project.getTasks()
            .withType(RunMinecraftTask.class)
            .configureEach(
                rm -> rm.setWorkingDir(
                    (rm.getSide() == Distribution.CLIENT) ? CLIENT_RUN_TASK_WORK_DIR : SERVER_RUN_TASK_WORK_DIR));

    }
}
