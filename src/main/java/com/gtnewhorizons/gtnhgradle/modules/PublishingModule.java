package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHConstants;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.shadow.com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.lang3.ObjectUtils;
import com.modrinth.minotaur.Minotaur;
import com.modrinth.minotaur.ModrinthExtension;
import com.modrinth.minotaur.dependencies.Dependency;
import com.modrinth.minotaur.dependencies.ModDependency;
import com.modrinth.minotaur.dependencies.VersionDependency;
import net.darkhax.curseforgegradle.CurseForgeGradlePlugin;
import net.darkhax.curseforgegradle.TaskPublishCurseForge;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.plugins.PublishingPlugin;
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

        final ExtraPropertiesExtension ext = project.getExtensions()
            .getExtraProperties();
        final Provider<String> modVersion = project.provider(
            () -> Objects.requireNonNull(ext.get(GTNHConstants.MOD_VERSION_PROPERTY))
                .toString());

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
                project.afterEvaluate(
                    _p -> mvn.setVersion(ObjectUtils.firstNonNull(System.getenv("RELEASE_VERSION"), modVersion.get())));
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
                .set(modVersion.map(v -> v.endsWith("-pre") ? "beta" : "release"));
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
        if (!gtnh.configuration.curseForgeProjectId.isEmpty()) {
            project.getPlugins()
                .apply(CurseForgeGradlePlugin.class);
            final TaskProvider<TaskPublishCurseForge> publishCurseforge = project.getTasks()
                .register("publishCurseforge", TaskPublishCurseForge.class, task -> {
                    task.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                    task.setDescription("Publishes the mod to Curseforge");
                    task.dependsOn("assemble");

                    @SuppressWarnings("unchecked")
                    final File obfFile = ((TaskProvider<Jar>) Objects.requireNonNull(ext.get("publishableObfJar")))
                        .get()
                        .getArchiveFile()
                        .get()
                        .getAsFile();

                    task.apiToken = cfToken;
                    task.disableVersionDetection();
                    task.upload(gtnh.configuration.curseForgeProjectId, obfFile, artifact -> {
                        if (changelogFile.exists()) {
                            artifact.changelogType = "markdown";
                            artifact.changelog = changelogFile;
                        }
                        artifact.releaseType = modVersion.map(v -> v.endsWith("-pre") ? "beta" : "release");
                        artifact.addGameVersion(gtnh.configuration.minecraftVersion, "Forge");
                        artifact.addModLoader("Forge");

                        if (!gtnh.configuration.curseForgeRelations.isEmpty()) {
                            final String[] deps = gtnh.configuration.curseForgeRelations.split(";");
                            for (String dep : deps) {
                                dep = dep.trim();
                                if (dep.isEmpty()) {
                                    continue;
                                }
                                final String[] parts = dep.split(":");
                                artifact.addRelation(parts[1], parts[0]);
                            }
                        }
                        if (gtnh.configuration.usesMixins) {
                            artifact.addRelation("unimixins", "requiredDependency");
                        }

                        for (final Object secondary : getSecondaryArtifacts(project, gtnh)) {
                            @SuppressWarnings("unchecked")
                            final File secondaryFile = ((TaskProvider<Jar>) secondary).get()
                                .getArchiveFile()
                                .get()
                                .getAsFile();
                            artifact.withAdditionalFile(secondaryFile);
                        }
                    });
                });
            if (cfToken != null) {
                project.getTasks()
                    .named(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME)
                    .configure(task -> task.dependsOn(publishCurseforge));
            }
        }
    }

    private static final Set<String> VALID_MODRINTH_SCOPES = ImmutableSet
        .of("required", "optional", "incompatible", "embedded");

    private static void addModrinthDep(Project project, String scope, String type, String name) {
        final Dependency dep;
        if (!VALID_MODRINTH_SCOPES.contains(scope)) {
            throw new IllegalArgumentException("Invalid modrinthh dependency scope: " + scope);
        }
        dep = switch (type) {
            case "project" -> new ModDependency(name, scope);
            case "version" -> new VersionDependency(name, scope);
            default -> throw new IllegalArgumentException("Invalid modrinth dependency type: " + type);
        };
        final ModrinthExtension mr = project.getExtensions()
            .getByType(ModrinthExtension.class);
        mr.getDependencies()
            .add(dep);
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
