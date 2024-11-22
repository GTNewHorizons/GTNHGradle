package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils;
import com.gtnewhorizons.retrofuturagradle.shadow.com.google.common.collect.ImmutableMap;
import com.gtnewhorizons.retrofuturagradle.shadow.com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.gtnhgradle.GTNHConstants;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.gtnhgradle.UpdateableConstants;
import com.gtnewhorizons.retrofuturagradle.MinecraftExtension;
import com.gtnewhorizons.retrofuturagradle.ObfuscationAttribute;
import com.gtnewhorizons.retrofuturagradle.mcp.InjectTagsTask;
import com.gtnewhorizons.retrofuturagradle.mcp.MCPTasks;
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.BasePluginExtension;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Configures the Java/Scala/Kotlin toolchain settings */
public abstract class ToolchainModule implements GTNHModule {

    /** @return Gradle-provided */
    @Inject
    public abstract JavaToolchainService getToolchainService();

    /** @return mcmod.info properties expanded by the Groovy template engine */
    public abstract MapProperty<String, String> getMcmodInfoProperties();

    /** For dependency injection */
    @Inject
    public ToolchainModule() {}

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleToolchain;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        gtnh.getExtensions()
            .add(ToolchainModule.class, "toolchainModule", this);
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

        if (gtnh.minecraftVersion == GTNHGradlePlugin.MinecraftVersion.V1_12_2) {
            repos.maven(mvn -> {
                mvn.setName("Cleanroom Maven");
                mvn.setUrl("https://maven.cleanroommc.com");
            });
            repos.maven(mvn -> {
                mvn.setName("GTCEu Maven");
                mvn.setUrl("https://maven.gtceu.com");
            });
        }

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
            deps.add(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME, UpdateableConstants.NEWEST_JABEL);
            ((ModuleDependency) deps.add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, UpdateableConstants.NEWEST_JABEL))
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
        final ModUtils modUtils = project.getExtensions()
            .getByType(ModUtils.class);

        minecraft.getMcVersion()
            .set(gtnh.configuration.minecraftVersion);

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
        if (!gtnh.configuration.deprecatedGradleTokenModId.isEmpty()) {
            minecraft.getInjectedTags()
                .put(gtnh.configuration.deprecatedGradleTokenModId, gtnh.configuration.modId);
        }
        if (!gtnh.configuration.deprecatedGradleTokenGroupName.isEmpty()) {
            minecraft.getInjectedTags()
                .put(gtnh.configuration.deprecatedGradleTokenGroupName, gtnh.configuration.modGroup);
        }
        if (!gtnh.configuration.deprecatedGradleTokenModName.isEmpty()) {
            minecraft.getInjectedTags()
                .put(gtnh.configuration.deprecatedGradleTokenModName, gtnh.configuration.modName);
        }
        if (gtnh.configuration.replaceGradleTokenInFile.isEmpty()) {
            if (!gtnh.configuration.deprecatedGradleTokenModId.isEmpty()) {
                gtnh.logger.error("gradleTokenModId is deprecated! The field will no longer be generated.");
            }
            if (!gtnh.configuration.deprecatedGradleTokenGroupName.isEmpty()) {
                gtnh.logger.error("gradleTokenModGroup is deprecated! The field will no longer be generated.");
            }
            if (!gtnh.configuration.deprecatedGradleTokenModName.isEmpty()) {
                gtnh.logger.error("gradleTokenModName is deprecated! The field will no longer be generated.");
            }
        }
        if (!gtnh.configuration.gradleTokenVersion.isEmpty()) {
            ExtraPropertiesExtension extraProps = project.getExtensions()
                .getExtraProperties();
            minecraft.getInjectedTags()
                .put(
                    gtnh.configuration.gradleTokenVersion,
                    project.getProviders()
                        .provider(
                            () -> Objects.requireNonNull(extraProps.get(GTNHConstants.MOD_VERSION_PROPERTY))
                                .toString()));
        }

        // Other assorted settings
        if (gtnh.configuration.enableGenericInjection) {
            minecraft.getInjectMissingGenerics()
                .set(true);
        }
        minecraft.getUsername()
            .set(gtnh.configuration.developmentEnvironmentUserName);
        minecraft.getLwjgl3Version()
            .set(UpdateableConstants.NEWEST_LWJGL3);
        minecraft.getExtraRunJvmArguments()
            .add("-ea:" + gtnh.configuration.modGroup);

        // Blowdryer is present in some old mod builds, do not propagate it further as a dependency
        // IC2 has no reobf jars in its Maven
        minecraft.getGroupsToExcludeFromAutoReobfMapping()
            .addAll("com.diffplug", "com.diffplug.durian", "net.industrial-craft");

        // Ensure IC2 gets deobfed by RFG
        if (gtnh.configuration.useIC2FromCurseforge) {
            modUtils.deobfuscate(UpdateableConstants.NEWEST_IC2_SPEC);
        }

        // Custom reobfuscation auto-mappings
        project.getConfigurations()
            .configureEach(c -> {
                if (gtnh.configuration.useIC2FromCurseforge) {
                    final DependencySubstitutions ds = c.getResolutionStrategy()
                        .getDependencySubstitution();
                    ds.substitute(ds.module("net.industrial-craft:industrialcraft-2:2.2.828-experimental"))
                        .using(ds.module(UpdateableConstants.NEWEST_IC2_SPEC))
                        .withoutClassifier()
                        .because("Use a much more reliable Maven repository for IC2");
                } else {
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
                                            UpdateableConstants.NEWEST_IC2_SPEC);
                                }
                            }
                        });
                }
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

        // mcmod.info processing
        {
            final MapProperty<String, String> props = getMcmodInfoProperties();
            final Provider<String> modVersion = gtnh.getProviderFactory()
                .provider(
                    () -> Objects.requireNonNull(project.getVersion())
                        .toString());
            props.put(
                gtnh.minecraftVersion == GTNHGradlePlugin.MinecraftVersion.V1_7_10 ? "minecraftVersion" : "mcversion",
                minecraft.getMcVersion());
            props.put("modId", gtnh.configuration.modId);
            props.put("modName", gtnh.configuration.modName);
            props.put(
                gtnh.minecraftVersion == GTNHGradlePlugin.MinecraftVersion.V1_7_10 ? "modVersion" : "version",
                modVersion);
        }

        tasks.named("processResources", ProcessResources.class)
            .configure(t -> { t.exclude("spotless.gradle"); });
        project.afterEvaluate(p -> {
            p.getTasks()
                .named("processResources", ProcessResources.class)
                .configure(t -> {
                    final Map<String, String> expandedProperties = getMcmodInfoProperties().get();
                    t.getInputs()
                        .properties(expandedProperties);
                    t.filesMatching("mcmod.info", fcd -> { fcd.expand(expandedProperties); });
                });
        });
        // Configure the output manifest
        final TaskProvider<Jar> devJar = tasks.named("jar", Jar.class);
        final TaskProvider<ReobfuscatedJar> obfJar = tasks.named("reobfJar", ReobfuscatedJar.class);
        devJar.configure(t -> {
            final Manifest manifest = t.getManifest();
            final PropertiesConfiguration props = gtnh.configuration;
            if (!props.containsMixinsAndOrCoreModOnly && (props.usesMixins || !props.coreModClass.isEmpty())) {
                manifest.attributes(ImmutableMap.of("FMLCorePluginContainsFMLMod", true));
            }
            if (!props.accessTransformersFile.isEmpty()) {
                manifest.attributes(ImmutableMap.of("FMLAT", props.accessTransformersFile));
            }
            if (!props.coreModClass.isEmpty()) {
                manifest.attributes(ImmutableMap.of("FMLCorePlugin", props.modGroup + "." + props.coreModClass));
            }
            if (props.usesMixins) {
                if (gtnh.minecraftVersion == GTNHGradlePlugin.MinecraftVersion.V1_7_10) {
                    manifest.attributes(
                        ImmutableMap.of(
                            "TweakClass",
                            "org.spongepowered.asm.launch.MixinTweaker",
                            "MixinConfigs",
                            "mixins." + props.modId + ".json"));
                }
                manifest.attributes(ImmutableMap.of("ForceLoadAsMod", !props.containsMixinsAndOrCoreModOnly));
            }
        });
        project.getExtensions()
            .getExtraProperties()
            .set("publishableDevJar", devJar);
        project.getExtensions()
            .getExtraProperties()
            .set("publishableObfJar", obfJar);

        // API Jar
        final String modGroupPath = gtnh.configuration.modGroup.replace('.', '/');
        final String apiPackagePath = gtnh.configuration.apiPackage.replace('.', '/');
        final SourceSetContainer sourceSets = project.getExtensions()
            .getByType(JavaPluginExtension.class)
            .getSourceSets();
        tasks.register("apiJar", Jar.class, t -> {
            final SourceSet main = sourceSets.getByName("main");
            t.from(main.getAllSource(), cs -> { cs.include(modGroupPath + "/" + apiPackagePath + "/**"); });
            t.from(main.getOutput(), cs -> { cs.include(modGroupPath + "/" + apiPackagePath + "/**"); });
            t.from(
                main.getResources()
                    .getSrcDirs(),
                cs -> { cs.include("LICENSE"); });
            t.getArchiveClassifier()
                .set("api");
        });

        // Artifacts
        if (!gtnh.configuration.noPublishedSources) {
            project.getArtifacts()
                .add("archives", tasks.named("sourcesJar"));
        }
        if (!gtnh.configuration.apiPackage.isEmpty()) {
            project.getArtifacts()
                .add("archives", tasks.named("apiJar"));
        }
    }
}
