package com.gtnewhorizons.gtnhgradle.tasks;

import com.gtnewhorizons.gtnhgradle.UpdateableConstants;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.io.FileUtils;
import de.undercouch.gradle.tasks.download.DownloadExtension;
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
import java.net.MalformedURLException;
import java.util.Objects;

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
    }

    /**
     * Installs HSA.
     *
     * @throws IOException Filesystem error
     */
    @TaskAction
    public void installHSA() throws IOException {
        final String url = getAgentUrl().get();
        final File target = getTargetFile().getAsFile()
            .get();
        final File parent = target.getParentFile();
        FileUtils.forceMkdir(parent);
        final DownloadExtension download = getProject().getExtensions()
            .findByType(DownloadExtension.class);
        Objects.requireNonNull(download);
        download.run(ds -> {
            try {
                ds.src(url);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            ds.dest(target);
            ds.overwrite(false);
            ds.tempAndMove(true);
        });
    }
}
