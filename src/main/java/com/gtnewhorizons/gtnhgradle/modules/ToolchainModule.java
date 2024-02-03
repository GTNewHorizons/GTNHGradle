package com.gtnewhorizons.gtnhgradle.modules;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.gtnhgradle.GTNHConstants;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.MinecraftExtension;
import com.gtnewhorizons.retrofuturagradle.ObfuscationAttribute;
import com.gtnewhorizons.retrofuturagradle.mcp.InjectTagsTask;
import com.gtnewhorizons.retrofuturagradle.mcp.MCPTasks;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

/** Configures the Java/Scala/Kotlin toolchain settings */
public abstract class ToolchainModule implements GTNHModule {

    /** @return Gradle-provided */
    @Inject
    public abstract JavaToolchainService getToolchainService();

    /** For dependency injection */
    @Inject
    public ToolchainModule() {}

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleToolchain;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final TaskContainer tasks = project.getTasks();
        final JavaPluginExtension java = project.getExtensions()
            .getByType(JavaPluginExtension.class);

        // Set up required repositories
        final RepositoryHandler repos = project.getRepositories();
        repos.maven(mvn -> {
            mvn.setName("GTNH Maven");
            mvn.setUrl("https://nexus.gtnewhorizons.com/repository/public/");
            // Links for convenience:
            // Simple HTML browsing: https://nexus.gtnewhorizons.com/service/rest/repository/browse/releases/
            // Rich web UI browsing: https://nexus.gtnewhorizons.com/#browse/browse:releases
        });
        repos.maven(mvn -> {
            mvn.setName("OvermindDL1 Forge repo mirror");
            mvn.setUrl("https://gregtech.overminddl1.com/");
        });
        repos.maven(mvn -> {
            mvn.setName("LWJGL Snapshots");
            mvn.setUrl("https://oss.sonatype.org/content/repositories/snapshots/");
            mvn.mavenContent(c -> {
                c.snapshotsOnly();
                c.includeGroup("org.lwjgl");
            });
        });

        // Provide a runtimeOnlyNonPublishable configuration
        final ConfigurationContainer cfg = project.getConfigurations();
        final Configuration runtimeOnlyNonPublishable = cfg.create("runtimeOnlyNonPublishable", c -> {
            c.setDescription("Runtime only dependencies that are not published alongside the jar");
            c.setCanBeConsumed(false);
            c.setCanBeResolved(false);
        });
        final Configuration devOnlyNonPublishable = cfg.create("devOnlyNonPublishable", c -> {
            c.setDescription(
                "Runtime and compiletime dependencies that are not published alongside the jar (compileOnly + runtimeOnlyNonPublishable)");
            c.setCanBeConsumed(false);
            c.setCanBeResolved(false);
        });
        cfg.getByName("compileOnly")
            .extendsFrom(devOnlyNonPublishable);
        runtimeOnlyNonPublishable.extendsFrom(devOnlyNonPublishable);
        cfg.getByName("runtimeClasspath")
            .extendsFrom(runtimeOnlyNonPublishable);
        cfg.getByName("testRuntimeClasspath")
            .extendsFrom(runtimeOnlyNonPublishable);

        // Set up Java
        final int javaVersion = gtnh.configuration.enableModernJavaSyntax ? 17 : 8;
        java.getToolchain()
            .getVendor()
            .set(JvmVendorSpec.AZUL);
        java.getToolchain()
            .getLanguageVersion()
            .set(JavaLanguageVersion.of(javaVersion));
        if (!gtnh.configuration.noPublishedSources) {
            java.withSourcesJar();
        }
        tasks.withType(JavaCompile.class)
            .configureEach(
                jc -> {
                    jc.getOptions()
                        .setEncoding(StandardCharsets.UTF_8.name());
                });
        if (gtnh.configuration.enableModernJavaSyntax) {
            repos.exclusiveContent(ecr -> {
                ecr.forRepositories(
                    project.getRepositories()
                        .mavenCentral(mar -> { mar.setName("mavenCentral_java8Unsupported"); }));
                ecr.filter(f -> { f.includeGroup("me.eigenraven.java8unsupported"); });
            });
            final DependencyHandler deps = project.getDependencies();
            deps.add(
                JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                "com.github.bsideup.jabel:jabel-javac-plugin:1.0.1");
            ((ModuleDependency) deps
                .add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, "com.github.bsideup.jabel:jabel-javac-plugin:1.0.1"))
                    .setTransitive(false);
            // Workaround for https://github.com/bsideup/jabel/issues/174
            deps.add(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME, "net.java.dev.jna:jna-platform:5.13.0");
            // Allow using jdk.unsupported classes like sun.misc.Unsafe in the compiled code, working around
            // JDK-8206937.
            deps.add(
                MCPTasks.PATCHED_MINECRAFT_CONFIGURATION_NAME,
                "me.eigenraven.java8unsupported:java-8-unsupported-shim:1.0.0");

            final Set<String> doNotUpgrade = ImmutableSet.of("compileMcLauncherJava", "compilePatchedMcJava");
            final Provider<JvmVendorSpec> vendor = java.getToolchain()
                .getVendor();
            final Provider<JavaCompiler> jabelCompiler = getToolchainService().compilerFor(jts -> {
                jts.getLanguageVersion()
                    .set(JavaLanguageVersion.of(17));
                jts.getVendor()
                    .set(vendor);
            });
            tasks.withType(JavaCompile.class)
                .configureEach(jc -> {
                    if (doNotUpgrade.contains(jc.getName())) {
                        return;
                    }
                    jc.setSourceCompatibility("17");
                    jc.getOptions()
                        .getRelease()
                        .set(8);
                    jc.getJavaCompiler()
                        .set(jabelCompiler);
                });
        }

        // Set up Scala
        tasks.withType(ScalaCompile.class)
            .configureEach(
                sc -> {
                    sc.getOptions()
                        .setEncoding(StandardCharsets.UTF_8.name());
                });

        // Set up Kotlin if enabled
        project.getPlugins()
            .withId("org.jetbrains.kotlin.jvm", plugin -> {
                final KotlinTopLevelExtension kotlin = (KotlinTopLevelExtension) project.getExtensions()
                    .getByName("kotlin");
                kotlin.jvmToolchain(8);
                final Set<String> disabledKotlinTasks = ImmutableSet.of(
                    "kaptGenerateStubsMcLauncherKotlin",
                    "kaptGenerateStubsPatchedMcKotlin",
                    "kaptGenerateStubsInjectedTagsKotlin",
                    "compileMcLauncherKotlin",
                    "compilePatchedMcKotlin",
                    "compileInjectedTagsKotlin",
                    "kaptMcLauncherKotlin",
                    "kaptPatchedMcKotlin",
                    "kaptInjectedTagsKotlin",
                    "kspMcLauncherKotlin",
                    "kspPatchedMcKotlin",
                    "kspInjectedTagsKotlin");
                tasks.configureEach(t -> {
                    if (disabledKotlinTasks.contains(t.getName())) {
                        t.setEnabled(false);
                    }
                });
            });

        // Set up basic project settings
        project.setGroup(
            gtnh.configuration.useModGroupForPublishing ? gtnh.configuration.modGroup : "com.github.GTNewHorizons");
        final BasePluginExtension base = project.getExtensions()
            .getByType(BasePluginExtension.class);
        if (!gtnh.configuration.customArchiveBaseName.isEmpty()) {
            base.getArchivesName()
                .set(gtnh.configuration.customArchiveBaseName);
        } else {
            base.getArchivesName()
                .set(gtnh.configuration.modId);
        }

        final MinecraftExtension minecraft = project.getExtensions()
            .getByType(MinecraftExtension.class);
        final MCPTasks mcpTasks = project.getExtensions()
            .getByType(MCPTasks.class);

        // Tag injection
        if (!gtnh.configuration.replaceGradleTokenInFile.isEmpty()) {
            for (final String f : gtnh.configuration.replaceGradleTokenInFile.split(",")) {
                minecraft.getTagReplacementFiles()
                    .add(f.trim());
            }
            gtnh.logger.warn("replaceGradleTokenInFile is deprecated! Consider using generateGradleTokenClass");
        }
        if (!gtnh.configuration.generateGradleTokenClass.isEmpty()) {
            final String klass = gtnh.configuration.generateGradleTokenClass;
            tasks.named("injectTags", InjectTagsTask.class)
                .configure(
                    t -> {
                        t.getOutputClassName()
                            .set(klass);
                    });
        }
        if (!gtnh.configuration.replaceGradleTokenInFile.isEmpty()) {
            if (!gtnh.configuration.deprecatedGradleTokenModId.isEmpty()) {
                minecraft.getInjectedTags()
                    .put(gtnh.configuration.deprecatedGradleTokenModId, gtnh.configuration.modId);
            }
            if (!gtnh.configuration.deprecatedGradleTokenModGroup.isEmpty()) {
                minecraft.getInjectedTags()
                    .put(gtnh.configuration.deprecatedGradleTokenModGroup, gtnh.configuration.modGroup);
            }
            if (!gtnh.configuration.deprecatedGradleTokenModName.isEmpty()) {
                minecraft.getInjectedTags()
                    .put(gtnh.configuration.deprecatedGradleTokenModName, gtnh.configuration.modName);
            }
        } else {
            if (!gtnh.configuration.deprecatedGradleTokenModId.isEmpty()) {
                gtnh.logger.error("gradleTokenModId is deprecated! The field will no longer be generated.");
            }
            if (!gtnh.configuration.deprecatedGradleTokenModGroup.isEmpty()) {
                gtnh.logger.error("gradleTokenModGroup is deprecated! The field will no longer be generated.");
            }
            if (!gtnh.configuration.deprecatedGradleTokenModName.isEmpty()) {
                gtnh.logger.error("gradleTokenModName is deprecated! The field will no longer be generated.");
            }
        }
        if (!gtnh.configuration.gradleTokenVersion.isEmpty()) {
            minecraft.getInjectedTags()
                .put(
                    gtnh.configuration.gradleTokenVersion,
                    Objects.requireNonNull(
                        project.getExtensions()
                            .getExtraProperties()
                            .get(GTNHConstants.MOD_VERSION_PROPERTY))
                        .toString());
        }

        // Other assorted settings
        if (gtnh.configuration.enableGenericInjection) {
            minecraft.getInjectMissingGenerics()
                .set(true);
        }
        minecraft.getUsername()
            .set(gtnh.configuration.developmentEnvironmentUserName);
        minecraft.getLwjgl3Version()
            .set("3.3.2");
        minecraft.getExtraRunJvmArguments()
            .add("-ea:" + gtnh.configuration.modGroup);

        // Blowdryer is present in some old mod builds, do not propagate it further as a dependency
        // IC2 has no reobf jars in its Maven
        minecraft.getGroupsToExcludeFromAutoReobfMapping()
            .addAll("com.diffplug", "com.diffplug.durian", "net.industrial-craft");

        // Custom reobfuscation auto-mappings
        project.getConfigurations()
            .configureEach(c -> {
                c.getDependencies()
                    .configureEach(dep -> {
                        if (dep instanceof ExternalModuleDependency mdep) {
                            if ("net.industrial-craft".equals(mdep.getGroup())
                                && "industrialcraft-2".equals(mdep.getName())) {
                                // https://www.curseforge.com/minecraft/mc-mods/industrial-craft/files/2353971
                                project.getDependencies()
                                    .add(
                                        mcpTasks.getReobfJarConfiguration()
                                            .getName(),
                                        "curse.maven:ic2-242638:2353971");
                            }
                        }
                    });
                final ObfuscationAttribute obfuscationAttr = c.getAttributes()
                    .getAttribute(ObfuscationAttribute.OBFUSCATION_ATTRIBUTE);
                if (obfuscationAttr == null || !obfuscationAttr.getName()
                    .equals(ObfuscationAttribute.SRG)) {
                    return;
                }
                c.getResolutionStrategy()
                    .eachDependency(details -> {
                        final ModuleVersionSelector requested = details.getRequested();
                        // Remap CoFH core cursemaven dev jar to the obfuscated version for runObfClient/Server
                        if ("curse.maven".equals(requested.getGroup()) && requested.getName()
                            .endsWith("-69162")
                            && requested.getVersion()
                                .equals("2388751")) {
                            details.useVersion("2388750");
                            details.because("Pick obfuscated jar");
                        }
                    });
            });
    }
}
