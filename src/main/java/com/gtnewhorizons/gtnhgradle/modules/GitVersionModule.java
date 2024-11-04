package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHConstants;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.lang3.ObjectUtils;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.lang3.StringUtils;
import com.palantir.gradle.gitversion.GitVersionCacheService;
import com.palantir.gradle.gitversion.GitVersionPlugin;
import com.palantir.gradle.gitversion.VersionDetails;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * First checks for a valid git repository in the project, if not found does nothing. Applies the
 * {@code com.palantir.git-version} plugin and sets the project version based on the latest git tag and commit
 * hash. Can be disabled by setting a VERSION environment variable.
 */
public class GitVersionModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleGitVersion;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) {
        final Path projectDir = gtnh.getProjectLayout()
            .getProjectDirectory()
            .getAsFile()
            .toPath();
        String versionOverride = System.getenv("VERSION");
        // In submodules, .git is a file pointing to the real git dir
        final boolean isAGitRepo = Files.exists(projectDir.resolve(".git"));

        if (!isAGitRepo && versionOverride == null) {
            gtnh.logger.error(
                "Project is not a git repository, and no VERSION override was set. Either initialize a git repo, set a VERSION environment variable override, or disable the gitVersion module.");
            throw new IllegalStateException("Not a git repo, and no VERSION set.");
        }

        // Pulls version first from the VERSION env and then git tag
        String identifiedVersion;
        boolean checkVersion = false;
        try {
            // Produce a version based on the tag, or for branches something like
            // 0.2.2-configurable-maven-and-extras.38+43090270b6-dirty
            if (versionOverride == null) {
                project.getPlugins()
                    .apply(GitVersionPlugin.class);
                final GitVersionCacheService gitService = GitVersionCacheService
                    .getSharedGitVersionCacheService(project)
                    .get();
                final VersionDetails gitDetails = gitService.getVersionDetails(project.getProjectDir(), null);
                final String gitVersion = gitService.getGitVersion(project.getProjectDir(), null);
                var isDirty = gitVersion.endsWith(".dirty"); // No public API for this, isCleanTag has a different
                                                             // meaning
                String branchName = ObjectUtils
                    .firstNonNull(gitDetails.getBranchName(), System.getenv("GIT_BRANCH"), "git");
                branchName = StringUtils.removeStart(branchName, "origin/");
                branchName = branchName.replaceAll("[^a-zA-Z0-9-]+", "-"); // sanitize branch names for semver
                identifiedVersion = ObjectUtils.firstNonNull(gitDetails.getLastTag(), gitDetails.getGitHash(), "0.0.0");
                if (gitDetails.getCommitDistance() > 0) {
                    identifiedVersion += String.format(
                        "-%s.%s+%s%s",
                        branchName,
                        gitDetails.getCommitDistance(),
                        gitDetails.getGitHash(),
                        isDirty ? "-dirty" : "");
                } else if (isDirty) {
                    identifiedVersion += String.format("-%s+%s-dirty", branchName, gitDetails.getGitHash());
                } else {
                    checkVersion = true;
                }
            } else {
                identifiedVersion = versionOverride;
            }
        } catch (Exception ignored) {
            gtnh.logger.error("""
                This mod must be version controlled by Git AND the repository must provide at least one tag,
                or the VERSION override must be set! (Do NOT download from GitHub using the ZIP option, instead
                clone the repository, see https://gtnh.miraheze.org/wiki/Development for details.
                """);
            versionOverride = "NO-GIT-TAG-SET";
            identifiedVersion = versionOverride;
        }
        project.setVersion(identifiedVersion);
        ExtraPropertiesExtension extraProps = project.getExtensions()
            .getExtraProperties();
        if (!extraProps.has(GTNHConstants.MOD_VERSION_PROPERTY)) {
            extraProps.set(GTNHConstants.MOD_VERSION_PROPERTY, identifiedVersion);
        }

        if (identifiedVersion.equals(versionOverride)) {
            gtnh.logger.warn("Version override set to {}!", identifiedVersion);
        } else if (checkVersion && !StringUtils.isBlank(gtnh.configuration.versionPattern)) {
            final String rawPattern = gtnh.configuration.versionPattern;
            final Pattern pattern = Pattern.compile(rawPattern);
            if (!pattern.matcher(identifiedVersion)
                .matches()) {
                throw new InvalidUserDataException(
                    "Invalid version '" + identifiedVersion + "' does not match version pattern '" + rawPattern + "'");
            }
        }
    }
}
