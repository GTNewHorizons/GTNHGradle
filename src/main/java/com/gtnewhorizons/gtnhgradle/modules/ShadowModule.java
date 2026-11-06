package com.gtnewhorizons.gtnhgradle.modules;

import com.github.jengelman.gradle.plugins.shadow.ShadowExtension;
import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin;
import com.github.jengelman.gradle.plugins.shadow.tasks.MinimizeTool;
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import com.gtnewhorizons.retrofuturagradle.shadow.com.google.common.collect.ImmutableList;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.gtnhgradle.tasks.ArchiveFastPath;
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.CRC32;

/** Shadowed dependency support */
public class ShadowModule implements GTNHModule {

    private static final String MANIFEST_IN_META_INF = "MANIFEST.MF";

    /** Shadow's built-in exclusions */
    static final Set<String> DEFAULT_EXCLUDES = Set.of(
        "META-INF/INDEX.LIST",
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/versions/**/module-info.class",
        "module-info.class");

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.usesShadowedDependencies;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        project.getPlugins()
            .apply(ShadowPlugin.class);

        final ShadowExtension shadowExt = project.getExtensions()
            .getByType(ShadowExtension.class);
        shadowExt.getAddShadowVariantIntoJavaComponent()
            .set(false);
        shadowExt.getAddTargetJvmVersionAttribute()
            .set(false);

        final ConfigurationContainer cfgs = project.getConfigurations();
        final TaskContainer tasks = project.getTasks();

        final Configuration shadowRuntimeElements = cfgs.getByName("shadowRuntimeElements");
        final Configuration shadowImplementation = cfgs.maybeCreate("shadowImplementation");
        final Configuration shadeCompile = cfgs.maybeCreate("shadeCompile");
        final Configuration shadowCompile = cfgs.maybeCreate("shadowCompile");
        for (final Configuration shadowConf : ImmutableList.of(shadowImplementation, shadeCompile, shadowCompile)) {
            shadowConf.setCanBeConsumed(false);
            shadowConf.setCanBeResolved(true);
        }

        for (final String config : ImmutableList
            .of("compileClasspath", "runtimeClasspath", "testCompileClasspath", "testRuntimeClasspath")) {
            cfgs.getByName(config)
                .extendsFrom(shadowImplementation, shadeCompile, shadowCompile);
        }

        final TaskProvider<ShadowJar> shadowJar = tasks.named("shadowJar", ShadowJar.class);
        shadowJar.configure(sj -> {
            if (gtnh.configuration.minimizeShadowedDependencies) {
                sj.minimize();
            }
            sj.getConfigurations()
                .set(ImmutableList.of(shadowImplementation, shadeCompile, shadowCompile));
            // Default classifier - JVMDowngraderModule will override to "predowngrade" if needed
            sj.getArchiveClassifier()
                .set("dev");
            if (gtnh.configuration.relocateShadowedDependencies) {
                sj.getRelocationPrefix()
                    .set(gtnh.configuration.modGroup + ".shadow");
                sj.getEnableAutoRelocation()
                    .set(true);
            }

        });
        // When nothing is shaded, copy the plain jar's entries instead of packing every class and resource again
        final TaskProvider<Jar> jarTask = tasks.named("jar", Jar.class);
        final Provider<RegularFile> plainJar = jarTask.flatMap(AbstractArchiveTask::getArchiveFile);
        // Through providers: a task held by an action can't go into the configuration cache
        final Provider<Boolean> sameJarSettings = project.provider(
            () -> cfgs.getByName("shadow")
                .isEmpty() && archiveOptions(shadowJar.get()).equals(archiveOptions(jarTask.get())));
        shadowJar.configure(sj -> {
            final Set<String> defaultCopyActions = ArchiveFastPath.copyActions(sj);
            final String defaultSignature = archiveSignature(sj);
            // Resolved with the task graph, as whether copying is possible depends on the build scripts
            sj.getInputs()
                .files(
                    project.files(
                        (Callable<Object>) () -> packsLikeThePlainJar(shadowJar.get(), defaultSignature)
                            && sameJarSettings.get() ? plainJar : Collections.emptyList()))
                .withPropertyName("plainJarIfNothingShadowed")
                .withPathSensitivity(PathSensitivity.NONE);
            ArchiveFastPath.replacePackaging(sj, t -> {
                if (!packsLikeThePlainJar((ShadowJar) t, defaultSignature) || !sameJarSettings.get()) {
                    return false;
                }
                final Path source = plainJar.get()
                    .getAsFile()
                    .toPath();
                if (!Files.isRegularFile(source)) {
                    // Not built, as the task graph ruled out copying
                    return false;
                }
                final Path target = ((ShadowJar) t).getArchiveFile()
                    .get()
                    .getAsFile()
                    .toPath();
                try {
                    final Set<String> entries = findEntriesToCopy((ShadowJar) t, source, defaultCopyActions);
                    if (entries == null) {
                        return false;
                    }
                    t.getLogger()
                        .info("Nothing to shadow, copying the plain jar to {}", target);
                    ArchiveFastPath.copyEntries(source, target, entries);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return true;
            });
        });
        // jar is intermediate when shadow is enabled - shadowJar consumes it
        tasks.named("jar", Jar.class)
            .configure(
                j -> {
                    j.getArchiveClassifier()
                        .set("dev-preshadow");
                });

        for (final String outgoingConfig : ImmutableList.of("runtimeElements", "apiElements")) {
            final Configuration outgoing = cfgs.getByName(outgoingConfig);
            outgoing.getOutgoing()
                .getArtifacts()
                .clear();
            outgoing.getOutgoing()
                .artifact(shadowJar);
        }
        tasks.named("reobfJar", ReobfuscatedJar.class)
            .configure(j -> {
                j.getInputJar()
                    .set(shadowJar.flatMap(AbstractArchiveTask::getArchiveFile));
            });

        project.getExtensions()
            .getExtraProperties()
            .set("publishableDevJar", shadowJar);
    }

    /** @return The destinations, patterns and copy actions of the copy spec tree, as text */
    private static String archiveSignature(Jar task) {
        final StringBuilder signature = new StringBuilder();
        if (task.getRootSpec()
            .hasCustomActions()) {
            signature.append("custom actions;");
        }
        task.getRootSpec()
            .walk(spec -> {
                signature.append(
                    spec.getDestPath()
                        .getPathString())
                    .append('|')
                    .append(spec.getAllIncludes())
                    .append('|')
                    .append(spec.getAllExcludes())
                    .append('|')
                    .append(
                        spec.getAllIncludeSpecs()
                            .size())
                    .append('|')
                    .append(
                        spec.getAllExcludeSpecs()
                            .size())
                    .append('|');
                spec.getAllCopyActions()
                    .forEach(
                        action -> signature.append(
                            action.getClass()
                                .getName())
                            .append(','));
                signature.append(";;");
            });
        return signature.toString();
    }

    /** @return The archive settings a copy of the plain jar would not apply */
    private static List<Object> archiveOptions(Jar task) {
        return List.of(task.getEntryCompression(), task.isPreserveFileTimestamps(), task.isReproducibleFileOrder());
    }

    /** @return Whether shadowJar only packs the source set outputs, so no main class or R8 either */
    private static boolean packsLikeThePlainJar(ShadowJar sj, String defaultSignature) {
        return shadowsNothing(sj) && defaultSignature.equals(archiveSignature(sj))
            && !sj.getMainClass()
                .isPresent()
            && !(sj.getMinimizeJar()
                .get()
                && sj.getMinimizeSpec()
                    .getTool()
                    .get() == MinimizeTool.R8);
    }

    /**
     * @param project A project with this module applied
     * @return Whether there is nothing to shade, relocate or transform. Only known once dependencies are declared.
     */
    public static Provider<Boolean> shadowsNothing(Project project) {
        final TaskProvider<ShadowJar> shadowJar = project.getTasks()
            .named("shadowJar", ShadowJar.class);
        return project.provider(() -> shadowsNothing(shadowJar.get()));
    }

    /**
     * The plain jar can hold extra entries, like RFG's tags class, and other content, from jar filters or a jar task
     * that did not run. The built jar is read as a reused configuration cache entry lacks a skipped task's sources.
     *
     * @return The plain jar entries making up what shadowJar would write, or null if it can't supply them
     */
    private static @Nullable Set<String> findEntriesToCopy(ShadowJar sj, Path plainJar, Set<String> defaultCopyActions)
        throws IOException {
        final Map<String, File> packed = new LinkedHashMap<>();
        final Set<String> entries = new HashSet<>();
        final boolean[] customized = { false };
        sj.getRootSpec()
            .walk(spec -> {
                // Renames and filters run after getSource(), so the paths below don't show them
                customized[0] |= !spec.getAllIncludes()
                    .isEmpty()
                    || !spec.getAllIncludeSpecs()
                        .isEmpty()
                    || !spec.getAllExcludeSpecs()
                        .isEmpty()
                    || !DEFAULT_EXCLUDES.containsAll(spec.getAllExcludes())
                    || spec.getAllCopyActions()
                        .stream()
                        .anyMatch(
                            action -> !defaultCopyActions.contains(
                                action.getClass()
                                    .getName()));
                spec.getSource()
                    .visit(details -> {
                        final String name = spec.getDestPath()
                            .append(details.getRelativePath())
                            .getPathString();
                        if (!details.isDirectory()) {
                            packed.putIfAbsent(name, details.getFile());
                        } else if (sj.getIncludeEmptyDirs()) {
                            entries.add(name + "/");
                        }
                    });
            });
        if (customized[0]) {
            sj.getLogger()
                .info("Not copying the plain jar: custom copy rules, shadowing normally");
            return null;
        }

        final File shadowManifest = packed.remove("META-INF/" + MANIFEST_IN_META_INF);
        final Map<File, JarEntry> jarEntries = new HashMap<>();
        try (JarFile jar = new JarFile(plainJar.toFile())) {
            for (final Map.Entry<String, File> file : packed.entrySet()) {
                final JarEntry entry = jar.getJarEntry(file.getKey());
                if (entry == null) {
                    sj.getLogger()
                        .info("Not copying the plain jar: it has no {}, shadowing normally", file.getKey());
                    return null;
                }
                jarEntries.put(file.getValue(), entry);
            }
            // Differs if a doFirst action changed the manifest, or the jar task did not run
            if (shadowManifest != null) {
                final Manifest expected;
                try (InputStream in = Files.newInputStream(shadowManifest.toPath())) {
                    expected = new Manifest(in);
                }
                if (!expected.equals(jar.getManifest())) {
                    sj.getLogger()
                        .info("Not copying the plain jar: the manifest differs, shadowing normally");
                    return null;
                }
            }
        }
        final boolean sameContent = jarEntries.entrySet()
            .parallelStream()
            .allMatch(file -> hasContentOf(file.getValue(), file.getKey()));
        if (!sameContent) {
            sj.getLogger()
                .info("Not copying the plain jar: its content differs from the sources, shadowing normally");
            return null;
        }

        entries.add(JarFile.MANIFEST_NAME);
        entries.add("META-INF/");
        for (final String name : packed.keySet()) {
            entries.add(name);
            for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
                entries.add(name.substring(0, slash + 1));
            }
        }
        return entries;
    }

    private static boolean hasContentOf(JarEntry entry, File file) {
        if (entry.getSize() != file.length()) {
            return false;
        }
        try {
            final CRC32 crc = new CRC32();
            crc.update(Files.readAllBytes(file.toPath()));
            return crc.getValue() == entry.getCrc();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean shadowsNothing(ShadowJar sj) {
        // Not typed as Configuration: a reused configuration cache entry holds resolved file collections instead
        for (final Object shadowed : sj.getConfigurations()
            .get()) {
            if (shadowed instanceof FileCollection files && !files.isEmpty()) {
                return false;
            }
        }
        return sj.getRelocators()
            .get()
            .isEmpty()
            && sj.getTransformers()
                .get()
                .isEmpty();
    }
}
