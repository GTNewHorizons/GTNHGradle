package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.io.FileUtils;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.lang3.StringUtils;
import de.undercouch.gradle.tasks.download.DownloadExtension;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.TaskContainer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Provides utility tasks and functions */
public class UtilityModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleUtility;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final TaskContainer tasks = project.getTasks();

        tasks.register("faq", t -> {
            t.setGroup("GTNH Buildscript");
            t.setDescription("Prints answers to frequently asked questions about building a project");
            t.doLast(
                inner -> {
                    inner.getLogger()
                        .lifecycle(
                            """
                                If your build fails to fetch dependencies, run './gradlew updateDependencies'.
                                Or you can manually check if the versions are still on the distributing sites -
                                the links can be found in repositories.gradle and build.gradle:repositories,
                                but not build.gradle:buildscript.repositories - those ones are for gradle plugin metadata.

                                If your build fails to recognize the syntax of new Java versions, enable Jabel in your
                                gradle.properties. See how it's done in GTNH ExampleMod/gradle.properties.
                                However, keep in mind that Jabel enables only syntax features, but not APIs that were introduced in
                                Java 9 or later.
                                """);
                });
        });

        tasks.register("deobfParams", t -> {
            t.setGroup("GTNH Buildscript");
            t.setDescription("Rename all obfuscated parameter names inherited from Minecraft classes");
            t.doLast(inner -> {
                final PropertiesConfiguration props = gtnh.configuration;
                final String mcpDir = project.getGradle()
                    .getGradleUserHomeDir() + "/caches/minecraft/de/oceanlabs/mcp/mcp_"
                    + props.channel
                    + "/"
                    + props.mappingsVersion;
                final String mcpZIP = mcpDir + "/mcp_"
                    + props.channel
                    + "-"
                    + props.mappingsVersion
                    + "-"
                    + props.minecraftVersion
                    + ".zip";
                final String paramsCSV = mcpDir + "/params.csv";

                final DownloadExtension download = project.getExtensions()
                    .getByType(DownloadExtension.class);
                download.run(ds -> {
                    try {
                        ds.src(
                            "https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_" + props.channel
                                + "/"
                                + props.mappingsVersion
                                + "-"
                                + props.minecraftVersion
                                + "/mcp_"
                                + props.channel
                                + "-"
                                + props.mappingsVersion
                                + "-"
                                + props.minecraftVersion
                                + ".zip");
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                    ds.dest(mcpZIP);
                    ds.overwrite(false);
                });

                if (!project.file(paramsCSV)
                    .exists()) {
                    inner.getLogger()
                        .lifecycle("Extracting MCP archive ...");
                    project.copy(cs -> {
                        cs.from(project.zipTree(mcpZIP));
                        cs.into(mcpDir);
                    });
                }

                inner.getLogger()
                    .lifecycle("Parsing params.csv ...");
                Map<String, String> params = new HashMap<>();
                try {
                    for (String line : Files.readAllLines(Paths.get(paramsCSV))) {
                        String[] cells = line.split(",");
                        if (cells.length > 2 && cells[0].matches("p_i?\\d+_\\d+_")) {
                            params.put(cells[0], cells[1]);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                final int changed = replaceParams(inner.getLogger(), project.file("src/main/java"), params);
                inner.getLogger()
                    .lifecycle("Modified {} files!", changed);
                inner.getLogger()
                    .lifecycle("""
                        Don't forget to verify that the code still works as before!
                        It could be broken due to duplicate variables existing now
                        or parameters taking priority over other variables.
                        """);
            });
        });

        final ExtraPropertiesExtension ext = project.getExtensions()
            .getExtraProperties();
        ext.set("deobf", new GroovyDeobf(project));
        ext.set("deobfMaven", new GroovyDeobfMaven(project));
        ext.set("deobfCurse", new GroovyDeobfCurse(project));

        gtnh.logger.lifecycle("You might want to check out './gradlew :faq' if your build fails.");
    }

    /**
     * Replace SRG param names with MCP names
     *
     * @param logger Logger to use
     * @param file   Directory to scan
     * @param params SRG to MCP mappings
     * @return Files changed count
     */
    public static int replaceParams(Logger logger, File file, Map<String, String> params) {
        int fileCount = 0;

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null) {
                return 0;
            }
            for (File f : files) {
                fileCount += replaceParams(logger, f, params);
            }
            return fileCount;
        }
        logger.info("Visiting {} ...", file);
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            int hash = content.hashCode();
            for (final Map.Entry<String, String> entry : params.entrySet()) {
                content = content.replaceAll(entry.getKey(), entry.getValue());
            }
            if (hash != content.hashCode()) {
                Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
                return 1;
            }
        } catch (Exception e) {
            logger.error("Error during param replacement in {}", file, e);
        }
        return 0;
    }

    /**
     * Deprecated URL-based deobfuscation wrapper
     *
     * @param project   Gradle project to use
     * @param sourceURL The obfuscated jar URL
     * @return A dependency specification for the deobfed jar
     */
    public static Object deobf(Project project, String sourceURL) {
        try {
            URL url = new URL(sourceURL);
            String fileName = url.getFile();

            // get rid of directories:
            int lastSlash = fileName.lastIndexOf("/");
            if (lastSlash > 0) {
                fileName = fileName.substring(lastSlash + 1);
            }
            // get rid of extension:
            fileName = StringUtils.removeEnd(fileName, ".jar");
            fileName = StringUtils.removeEnd(fileName, ".litemod");

            String hostName = url.getHost();
            if (hostName.startsWith("www.")) {
                hostName = hostName.substring(4);
            }
            List<String> parts = Arrays.asList(hostName.split("\\."));
            Collections.reverse(parts);
            hostName = String.join(".", parts);

            return deobf(project, sourceURL, hostName + '/' + fileName);
        } catch (Exception ignored) {
            return deobf(project, sourceURL, "deobf/" + sourceURL.hashCode());
        }
    }

    /**
     * Deprecated URL-based deobfuscation wrapper
     *
     * @param project     Gradle project to use
     * @param sourceURL   The obfuscated jar URL
     * @param rawFileName Name for the file to cache
     * @return A dependency specification for the deobfed jar
     */
    public static Object deobf(Project project, String sourceURL, String rawFileName) {
        final String bon2Version = "2.5.1";
        final String fileName;
        try {
            fileName = URLDecoder.decode(rawFileName, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        final File cacheDir = new File(
            project.getGradle()
                .getGradleUserHomeDir(),
            "caches");
        final File obfFile = FileUtils.getFile(cacheDir, "gtnh-deobf", fileName + ".jar");
        try {
            FileUtils.forceMkdirParent(obfFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final DownloadExtension download = project.getExtensions()
            .getByType(DownloadExtension.class);
        download.run(ds -> {
            try {
                ds.src(sourceURL);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            ds.dest(obfFile);
            ds.quiet(true);
            ds.overwrite(false);
        });
        final ModUtils modUtils = project.getExtensions()
            .getByType(ModUtils.class);
        return modUtils.deobfuscate(obfFile);
    }

    /**
     * Groovy-callable wrapper for {@link UtilityModule#deobf(Project, String)} and
     * {@link UtilityModule#deobf(Project, String, String)}
     */
    public static class GroovyDeobf {

        final Project project;

        /**
         * Constructs a closue wrapper with the given bound project
         *
         * @param project The project to bind as the first argument
         */
        public GroovyDeobf(Project project) {
            this.project = project;
        }

        /**
         * Deprecated URL-based deobfuscation wrapper
         *
         * @param sourceURL The obfuscated jar URL
         * @return A dependency specification for the deobfed jar
         */
        public Object call(String sourceURL) {
            return UtilityModule.deobf(project, sourceURL);
        }

        /**
         * Deprecated URL-based deobfuscation wrapper
         *
         * @param sourceURL   The obfuscated jar URL
         * @param rawFileName Name for the file to cache
         * @return A dependency specification for the deobfed jar
         */
        public Object call(String sourceURL, String rawFileName) {
            return UtilityModule.deobf(project, sourceURL, rawFileName);
        }

        /**
         * Deprecated URL-based deobfuscation wrapper
         *
         * @param sourceURL The obfuscated jar URL
         * @return A dependency specification for the deobfed jar
         */
        public Object invoke(String sourceURL) {
            return UtilityModule.deobf(project, sourceURL);
        }

        /**
         * Deprecated URL-based deobfuscation wrapper
         *
         * @param sourceURL   The obfuscated jar URL
         * @param rawFileName Name for the file to cache
         * @return A dependency specification for the deobfed jar
         */
        public Object invoke(String sourceURL, String rawFileName) {
            return UtilityModule.deobf(project, sourceURL, rawFileName);
        }
    }

    /**
     * Deprecated by rfg.deobf, deobfuscate a maven dependency
     *
     * @param project  Gradle project to use
     * @param repoURL  URL of the maven repository
     * @param mavenDep GAV coordinates for the jar to deobfuscate
     * @return A dependency specification for the deobfed jar
     */
    public static Object deobfMaven(Project project, String repoURL, String mavenDep) {
        if (!repoURL.endsWith("/")) {
            repoURL += "/";
        }
        String[] parts = mavenDep.split(":");
        parts[0] = parts[0].replace('.', '/');
        String jarURL = repoURL + parts[0] + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";
        return deobf(project, jarURL);
    }

    /** Groovy-callable wrapper for {@link UtilityModule#deobfMaven(Project, String, String)} */
    public static class GroovyDeobfMaven {

        final Project project;

        /**
         * Constructs a closue wrapper with the given bound project
         *
         * @param project The project to bind as the first argument
         */
        public GroovyDeobfMaven(Project project) {
            this.project = project;
        }

        /**
         * Deprecated by rfg.deobf, deobfuscate a maven dependency
         *
         * @param repoURL  URL of the maven repository
         * @param mavenDep GAV coordinates for the jar to deobfuscate
         * @return A dependency specification for the deobfed jar
         */
        public Object call(String repoURL, String mavenDep) {
            return UtilityModule.deobfMaven(project, repoURL, mavenDep);
        }

        /**
         * Deprecated by rfg.deobf, deobfuscate a maven dependency
         *
         * @param repoURL  URL of the maven repository
         * @param mavenDep GAV coordinates for the jar to deobfuscate
         * @return A dependency specification for the deobfed jar
         */
        public Object invoke(String repoURL, String mavenDep) {
            return UtilityModule.deobfMaven(project, repoURL, mavenDep);
        }
    }

    /**
     * Thin wrapper around cursemaven and rfg.deobf.
     *
     * @param project  Gradle project to use
     * @param curseDep The curse PROJECT-FILE id pair
     * @return A dependency specification for the deobfed jar
     */
    public static Object deobfCurse(Project project, String curseDep) {
        final ModUtils modUtils = project.getExtensions()
            .getByType(ModUtils.class);
        return modUtils.deobfuscate("curse.maven:" + curseDep);
    }

    /** Groovy-callable wrapper for {@link UtilityModule#deobfCurse(Project, String)} */
    public static class GroovyDeobfCurse {

        final Project project;

        /**
         * Constructs a closue wrapper with the given bound project
         *
         * @param project The project to bind as the first argument
         */
        public GroovyDeobfCurse(Project project) {
            this.project = project;
        }

        /**
         * Thin wrapper around cursemaven and rfg.deobf.
         *
         * @param curseDep The curse PROJECT-FILE id pair
         * @return A dependency specification for the deobfed jar
         */
        public Object call(String curseDep) {
            return UtilityModule.deobfCurse(project, curseDep);
        }

        /**
         * Thin wrapper around cursemaven and rfg.deobf.
         *
         * @param curseDep The curse PROJECT-FILE id pair
         * @return A dependency specification for the deobfed jar
         */
        public Object invoke(String curseDep) {
            return UtilityModule.deobfCurse(project, curseDep);
        }
    }
}
