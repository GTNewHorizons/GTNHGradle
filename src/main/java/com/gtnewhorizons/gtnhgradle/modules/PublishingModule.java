package com.gtnewhorizons.gtnhgradle.modules;

import com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.gtnhgradle.GTNHConstants;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.MinecraftExtension;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.lang3.ObjectUtils;
import com.matthewprenger.cursegradle.CurseArtifact;
import com.matthewprenger.cursegradle.CurseExtension;
import com.matthewprenger.cursegradle.CurseGradlePlugin;
import com.matthewprenger.cursegradle.CurseProject;
import com.matthewprenger.cursegradle.CurseRelation;
import com.matthewprenger.cursegradle.Options;
import com.modrinth.minotaur.Minotaur;
import com.modrinth.minotaur.ModrinthExtension;
import com.modrinth.minotaur.dependencies.Dependency;
import com.modrinth.minotaur.dependencies.ModDependency;
import com.modrinth.minotaur.dependencies.VersionDependency;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Provides publications for the project */
public class PublishingModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.modulePublishing;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) throws Throwable {
        final PublishingExtension publishing = project.getExtensions()
            .getByType(PublishingExtension.class);
        final MinecraftExtension mc = project.getExtensions()
            .getByType(MinecraftExtension.class);

        final ExtraPropertiesExtension ext = project.getExtensions()
            .getExtraProperties();
        final String modVersion = Objects.requireNonNull(ext.get(GTNHConstants.MOD_VERSION_PROPERTY))
            .toString();

        // Maven
        publishing.getPublications()
            .register("maven", MavenPublication.class, mvn -> {
                mvn.from(
                    project.getComponents()
                        .findByName("java"));
                if (!gtnh.configuration.apiPackage.isEmpty()) {
                    mvn.artifact(
                        project.getTasks()
                            .findByName("apiJar"));
                }
                mvn.setGroupId(
                    ObjectUtils.firstNonNull(
                        System.getenv("ARTIFACT_GROUP_ID"),
                        project.getGroup()
                            .toString()));
                mvn.setArtifactId(ObjectUtils.firstNonNull(System.getenv("ARTIFACT_ID"), project.getName()));
                mvn.setVersion(ObjectUtils.firstNonNull(System.getenv("RELEASE_VERSION"), modVersion));
            });
        final String mavenUser = System.getenv("MAVEN_USER");
        final String mavenPass = System.getenv("MAVEN_PASSWORD");
        if (gtnh.configuration.usesMavenPublishing && mavenUser != null) {
            publishing.getRepositories()
                .maven(mvn -> {
                    mvn.setName("main");
                    mvn.setUrl(gtnh.configuration.mavenPublishUrl);
                    mvn.setAllowInsecureProtocol(gtnh.configuration.mavenPublishUrl.startsWith("http://"));
                    mvn.getCredentials()
                        .setUsername(ObjectUtils.firstNonNull(mavenUser, "NONE"));
                    mvn.getCredentials()
                        .setPassword(ObjectUtils.firstNonNull(mavenPass, "NONE"));
                });
        }

        final File changelogFile = new File(ObjectUtils.firstNonNull(System.getenv("CHANGELOG_FILE"), "CHANGELOG.md"));

        // Modrinth
        final String mrToken = System.getenv("MODRINTH_TOKEN");
        if (!gtnh.configuration.modrinthProjectId.isEmpty() && mrToken != null) {
            project.getPlugins()
                .apply(Minotaur.class);
            final ModrinthExtension mr = project.getExtensions()
                .getByType(ModrinthExtension.class);
            mr.getToken()
                .set(
                    project.getProviders()
                        .environmentVariable("MODRINTH_TOKEN"));
            mr.getProjectId()
                .set(gtnh.configuration.modrinthProjectId);
            mr.getVersionNumber()
                .set(modVersion);
            mr.getVersionType()
                .set(modVersion.endsWith("-pre") ? "beta" : "release");
            if (changelogFile.exists()) {
                final String contents = new String(Files.readAllBytes(changelogFile.toPath()), StandardCharsets.UTF_8);
                mr.getChangelog()
                    .set(contents);
            }
            mr.getUploadFile()
                .set(project.provider(() -> project.property("publishableObfJar")));
            mr.getAdditionalFiles()
                .set(project.provider(() -> getSecondaryArtifacts(project, gtnh)));
            mr.getGameVersions()
                .add(gtnh.configuration.minecraftVersion);
            mr.getLoaders()
                .add("forge");
            mr.getDebugMode()
                .set(false);

            if (!gtnh.configuration.modrinthRelations.isEmpty()) {
                final String[] deps = gtnh.configuration.modrinthRelations.split(";");
                for (String dep : deps) {
                    dep = dep.trim();
                    if (dep.isEmpty()) {
                        continue;
                    }
                    final String[] parts = dep.split(":");
                    final String[] qual = parts[0].split("-");
                    addModrinthDep(project, qual[0], qual[1], parts[1]);
                }
            }
            if (gtnh.configuration.usesMixins) {
                addModrinthDep(project, "required", "project", "unimixins");
            }
            project.getTasks()
                .named("modrinth")
                .configure(t -> t.dependsOn("build"));
            project.getTasks()
                .named("publish")
                .configure(t -> t.dependsOn("modrinth"));
        }

        // Curseforge
        final String cfToken = System.getenv("CURSEFORGE_TOKEN");
        if (!gtnh.configuration.curseForgeProjectId.isEmpty() && cfToken != null) {
            project.getPlugins()
                .apply(CurseGradlePlugin.class);
            final CurseExtension curse = project.getExtensions()
                .getByType(CurseExtension.class);
            curse.setApiKey(cfToken);
            final Options opts = curse.getCurseGradleOptions();
            opts.setJavaIntegration(false);
            opts.setForgeGradleIntegration(false);
            opts.setDebug(false);

            final CurseProject prj = new CurseProject();
            prj.setApiKey(cfToken);
            prj.setId(gtnh.configuration.curseForgeProjectId);
            if (changelogFile.exists()) {
                prj.setChangelogType("markdown");
                prj.setChangelog(changelogFile);
            }
            prj.setReleaseType(modVersion.endsWith("-pre") ? "beta" : "release");
            prj.addGameVersion(gtnh.configuration.minecraftVersion);
            prj.addGameVersion("Forge");
            @SuppressWarnings("unchecked")
            final File obfFile = ((TaskProvider<Jar>) Objects.requireNonNull(ext.get("publishableObfJar"))).get()
                .getArchiveFile()
                .get()
                .getAsFile();
            prj.mainArtifact(obfFile);
            for (final Object secondary : getSecondaryArtifacts(project, gtnh)) {
                @SuppressWarnings("unchecked")
                final File secondaryFile = ((TaskProvider<Jar>) secondary).get()
                    .getArchiveFile()
                    .get()
                    .getAsFile();
                prj.addArtifact(secondaryFile);
            }
            curse.getCurseProjects()
                .add(prj);

            if (!gtnh.configuration.curseForgeRelations.isEmpty()) {
                final String[] deps = gtnh.configuration.curseForgeRelations.split(";");
                for (String dep : deps) {
                    dep = dep.trim();
                    if (dep.isEmpty()) {
                        continue;
                    }
                    final String[] parts = dep.split(":");
                    addCurseForgeRelation(project, parts[0], parts[1]);
                }
            }
            if (gtnh.configuration.usesMixins) {
                addCurseForgeRelation(project, "requiredDependency", "unimixins");
            }
            project.getTasks()
                .named("curseforge")
                .configure(t -> t.dependsOn("build"));
            project.getTasks()
                .named("publish")
                .configure(t -> t.dependsOn("curseforge"));
        }
    }

    private static final Set<String> VALID_MODRINTH_SCOPES = ImmutableSet
        .of("required", "optional", "incompatible", "embedded");

    private static void addModrinthDep(Project project, String scope, String type, String name) {
        final Dependency dep;
        if (!VALID_MODRINTH_SCOPES.contains(scope)) {
            throw new IllegalArgumentException("Invalid modrinthh dependency scope: " + scope);
        }
        switch (type) {
            case "project":
                dep = new ModDependency(name, scope);
                break;
            case "version":
                dep = new VersionDependency(name, scope);
                break;
            default:
                throw new IllegalArgumentException("Invalid modrinth dependency type: " + type);
        }
        final ModrinthExtension mr = project.getExtensions()
            .getByType(ModrinthExtension.class);
        mr.getDependencies()
            .add(dep);
    }

    private static final Set<String> VALID_CF_RELATIONS = ImmutableSet
        .of("requiredDependency", "embeddedLibrary", "optionalDependency", "tool", "incompatible");

    private static void addCurseForgeRelation(Project project, String type, String name) {
        if (!VALID_CF_RELATIONS.contains(type)) {
            throw new IllegalArgumentException("Invalid CurseForge relation type: " + type);
        }
        final CurseExtension curse = project.getExtensions()
            .getByType(CurseExtension.class);
        final CurseArtifact artifact = curse.getCurseProjects()
            .iterator()
            .next()
            .getMainArtifact();
        CurseRelation rel = artifact.getCurseRelations();
        if (rel == null) {
            rel = new CurseRelation();
            artifact.setCurseRelations(rel);
        }
        rel.invokeMethod(type, new Object[] { name });
    }

    private static List<Object> getSecondaryArtifacts(Project project, GTNHGradlePlugin.GTNHExtension gtnh) {
        final List<Object> out = new ArrayList<>();
        final ExtraPropertiesExtension ext = project.getExtensions()
            .getExtraProperties();
        out.add(ext.get("publishableDevJar"));
        if (!gtnh.configuration.noPublishedSources) {
            out.add(
                project.getTasks()
                    .named("sourcesJar"));
        }
        if (!gtnh.configuration.apiPackage.isEmpty()) {
            out.add(
                project.getTasks()
                    .named("apiJar"));
        }
        return out;
    }
}
