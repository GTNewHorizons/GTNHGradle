package com.gtnewhorizons.gtnhgradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper to generate a mixins.modid.json file in src/main/resources, used by
 * {@link com.gtnewhorizons.gtnhgradle.modules.MixinModule}.
 */
public abstract class GenerateMixinAssetsTask extends DefaultTask {

    /** @return If the "usesMixins" property is true */
    @Input
    public abstract Property<Boolean> getMixinsEnabled();

    /** @return The corresponding configuration property */
    @Input
    public abstract Property<String> getModGroup();

    /** @return The corresponding configuration property */
    @Input
    public abstract Property<String> getMixinsPackage();

    /** @return The corresponding configuration property */
    @Input
    @Optional
    public abstract Property<String> getMixinPlugin();

    /** @return The name of the mixin refmap output */
    @Input
    public abstract Property<String> getMixinConfigRefMap();

    /** @return An output JSON file path */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /** For dependency injection */
    @Inject
    public GenerateMixinAssetsTask() {
        onlyIf(
            "Run only if mixins are enabled",
            t -> ((GenerateMixinAssetsTask) t).getMixinsEnabled()
                .get());
        onlyIf(
            "Run only if the file doesn't already exist",
            t -> !((GenerateMixinAssetsTask) t).getOutputFile()
                .getAsFile()
                .get()
                .exists());
    }

    /**
     * Executes the action
     *
     * @throws IOException Filesystem errors
     */
    @TaskAction
    public void generate() throws IOException {
        final Path output = getOutputFile().getAsFile()
            .get()
            .toPath();
        if (Files.exists(output)) {
            return;
        }

        final String modGroup = getModGroup().get();
        final String mixinsPackage = getMixinsPackage().get();
        final String mixinPlugin = getMixinPlugin().getOrElse("");
        final String mixinConfigRefMap = getMixinConfigRefMap().get();

        final StringBuilder outBuilder = new StringBuilder();
        outBuilder.append("""
            {
              "required": true,
              "minVersion": "0.8.5-GTNH",
              "package": \"""");
        outBuilder.append(modGroup);
        outBuilder.append('.');
        outBuilder.append(mixinsPackage);
        outBuilder.append("\",\n");
        if (!mixinPlugin.isEmpty()) {
            outBuilder.append("  \"plugin\": \"");
            outBuilder.append(modGroup);
            outBuilder.append('.');
            outBuilder.append(mixinPlugin);
            outBuilder.append("\",\n");
        }
        outBuilder.append("  \"refmap\": \"");
        outBuilder.append(mixinConfigRefMap);
        outBuilder.append("\",\n");
        outBuilder.append("""
              "target": "@env(DEFAULT)",
              "compatibilityLevel": "JAVA_8",
              "mixins": [],
              "client": [],
              "server": []
            }
            """);

        final String outString = outBuilder.toString();
        // noinspection ReadWriteStringCanBeUsed
        Files.write(output, outString.getBytes(StandardCharsets.UTF_8));
    }
}
