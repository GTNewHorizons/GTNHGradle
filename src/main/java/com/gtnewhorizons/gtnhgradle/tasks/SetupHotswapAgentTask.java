package com.gtnewhorizons.gtnhgradle.tasks;

import com.gtnewhorizons.gtnhgradle.UpdateableConstants;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.io.FileUtils;
import de.undercouch.gradle.tasks.download.DownloadAction;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

/** Installs HotSwapAgent into a JVM runtime directory */
public abstract class SetupHotswapAgentTask extends DefaultTask {

    /** @return Latest HotSwapAgent release URL */
    @Input
    public abstract Property<String> getAgentUrl();

    /** @return The target jvm-home/lib/hotswap/hotswap-agent.jar file */
    @OutputFile
    public abstract RegularFileProperty getTargetFile();

    /** @return Gradle-provided */
    @Inject
    public abstract JavaToolchainService getToolchainService();

    private final DownloadAction downloadAction;

    /**
     * Helper for setting {@link SetupHotswapAgentTask#getTargetFile()} using a toolchain spec.
     *
     * @param spec The spec to install HSA for
     */
    public void setTargetForToolchain(Action<JavaToolchainSpec> spec) {
        getTargetFile().set(
            getToolchainService().launcherFor(spec)
                .map(
                    jl -> jl.getMetadata()
                        .getInstallationPath()
                        .file("lib/hotswap/hotswap-agent.jar")));
    }

    /** For dependency injection */
    @Inject
    public SetupHotswapAgentTask() {
        setGroup("GTNH Buildscript");
        setDescription("Installs a recent version of HotSwapAgent into the Java runtime directory");
        getAgentUrl().convention(UpdateableConstants.NEWEST_HOTSWAPAGENT);
        onlyIf(
            "Run only if not already installed",
            t -> !((SetupHotswapAgentTask) t).getTargetFile()
                .getAsFile()
                .get()
                .exists());
        downloadAction = new DownloadAction(getProject(), this);
    }

    /**
     * Installs HSA.
     *
     * @throws IOException Filesystem error
     */
    @TaskAction
    public void installHSA() throws IOException, ExecutionException, InterruptedException {
        final String url = getAgentUrl().get();
        final File target = getTargetFile().getAsFile()
            .get();
        final File parent = target.getParentFile();
        FileUtils.forceMkdir(parent);

        downloadAction.src(url);
        downloadAction.dest(target);
        downloadAction.overwrite(false);
        downloadAction.tempAndMove(true);
        downloadAction.execute(true)
            .get();
    }
}
