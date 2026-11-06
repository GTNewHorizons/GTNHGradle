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

    /** Where Gradle puts the manifest it generated inside a jar task's own sources */
    private static final String MANIFEST_IN_META_INF = "MANIFEST.MF";

    /** Shadow's built-in exclusions; any additional rule needs its normal copy action. */
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
        // Mods often enable shadowing without shading anything. The shadow jar is then only the plain jar packaged
        // again, a full extra pass over every class and resource, so shadowJar copies the plain jar's entries instead.
        final TaskProvider<Jar> jarTask = tasks.named("jar", Jar.class);
        final Provider<RegularFile> plainJar = jarTask.flatMap(AbstractArchiveTask::getArchiveFile);
        // The task objects are reached through providers: a task an action holds on to cannot go into the
        // configuration cache.
        final Provider<Boolean> sameJarSettings = project.provider(
            () -> cfgs.getByName("shadow")
                .isEmpty() && archiveOptions(shadowJar.get()).equals(archiveOptions(jarTask.get())));
        shadowJar.configure(sj -> {
            final Set<String> defaultCopyActions = ArchiveFastPath.copyActions(sj);
            final String defaultSignature = archiveSignature(sj);
            // Only depend on the plain jar while copying it is still on the table. That needs the build scripts to
            // have configured shadowJar, so it is left to the moment the task graph is worked out.
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
                    // The task graph decided the plain jar was not worth building, so there is nothing to copy
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

    /**
     * @param task A jar task
     * @return How the copy spec tree would pack, as text: destinations, patterns and copy actions. Stays the same
     *         from configuration to execution, unlike the files themselves, which upstream tasks produce in between.
     */
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

    /** @return The settings that make two jars with the same files differ, which a copy would not pick up */
    private static List<Object> archiveOptions(Jar task) {
        return List.of(task.getEntryCompression(), task.isPreserveFileTimestamps(), task.isReproducibleFileOrder());
    }

    /**
     * Whether shadowJar has nothing to do but pack the source set outputs. Besides relocating and transforming, which
     * {@link #shadowsNothing(ShadowJar)} covers, it adds a main class to the manifest and can run R8.
     */
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
     * Whether shadowJar relocates anything, which is what decides if its output can stand in for the source set
     * outputs. Says nothing about files a build script added to the task by hand, see {@code apply}.
     *
     * @param project A project with this module applied
     * @return true if there is nothing to shade, relocate or transform. Only known once the build scripts declared
     *         their dependencies.
     */
    public static Provider<Boolean> shadowsNothing(Project project) {
        final TaskProvider<ShadowJar> shadowJar = project.getTasks()
            .named("shadowJar", ShadowJar.class);
        return project.provider(() -> shadowsNothing(shadowJar.get()));
    }

    /**
     * Works out which entries of the plain jar make up what shadowJar would write. The plain jar can hold more than
     * that, as the jar task can be given extra sources (RFG's tags class is one), and it can hold other content, from
     * jar task filters or from an old build when the jar task did not run. The built jar is read instead of the jar
     * task's own sources, which a reused configuration cache entry does not restore for a task it skipped.
     *
     * @param sj                 The executing shadowJar task
     * @param plainJar           The built plain jar
     * @param defaultCopyActions The copy actions of shadowJar before the build scripts configured it
     * @return The names of the entries to copy, or null if the plain jar can't supply what shadowJar would write
     * @throws IOException If the plain jar or a source file can't be read
     */
    private static @Nullable Set<String> findEntriesToCopy(ShadowJar sj, Path plainJar, Set<String> defaultCopyActions)
        throws IOException {
        final Map<String, File> packed = new LinkedHashMap<>();
        final Set<String> entries = new HashSet<>();
        final boolean[] customized = { false };
        sj.getRootSpec()
            .walk(spec -> {
                // Renames and filters run after getSource(); inspecting paths alone cannot account for them.
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
            // Shadow inherits the plain jar's manifest, but a build script can add attributes to shadowJar alone
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
        // Read as file collections, not as configurations: with the configuration cache the task holds a resolved
        // file collection in their place, which a Configuration typed loop fails to cast once the entry is reused.
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
