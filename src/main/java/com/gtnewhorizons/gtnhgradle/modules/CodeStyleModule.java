package com.gtnewhorizons.gtnhgradle.modules;

import com.diffplug.blowdryer.Blowdryer;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import com.gtnewhorizons.gtnhgradle.tasks.CheckWildcardImportsTask;
import com.gtnewhorizons.retrofuturagradle.shadow.com.google.common.collect.ImmutableSet;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.lang3.StringUtils;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Enables and configures Spotless and the wildcard import check.
 */
public class CodeStyleModule implements GTNHModule {

    /** Source sets of Minecraft itself and of RFG-generated code, which are not checked */
    private static final Set<String> UNCHECKED_SOURCE_SETS = ImmutableSet
        .of("patchedMc", "mcLauncher", "ideVirtualMain", "injectedTags");

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleCodeStyle;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) {
        if (!gtnh.configuration.disableSpotless) {
            // Version dynamically configured by GTNHSettingsConventionPlugin
            project.getPluginManager()
                .apply("com.diffplug.spotless");
            project.apply(oca -> { oca.from(Blowdryer.file("spotless.gradle")); });
        }
        if (!gtnh.configuration.disableCheckstyle) {
            // Replaces Checkstyle, which was only used for this check but re-checked every file whenever one changed
            final TaskContainer tasks = project.getTasks();
            final SourceSetContainer sourceSets = project.getExtensions()
                .getByType(JavaPluginExtension.class)
                .getSourceSets();
            sourceSets.configureEach(sourceSet -> {
                if (UNCHECKED_SOURCE_SETS.contains(sourceSet.getName())) {
                    return;
                }
                final TaskProvider<CheckWildcardImportsTask> checkImports = tasks.register(
                    "checkWildcardImports" + StringUtils.capitalize(sourceSet.getName()),
                    CheckWildcardImportsTask.class,
                    t -> {
                        t.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                        t.setDescription("Checks the " + sourceSet.getName() + " Java sources for wildcard imports");
                        t.getSource()
                            .from(sourceSet.getAllJava());
                        t.getResultsFile()
                            .set(
                                project.getLayout()
                                    .getBuildDirectory()
                                    .file("wildcardImports/" + sourceSet.getName() + ".txt"));
                    });
                tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME)
                    .configure(t -> t.dependsOn(checkImports));
            });
        }
    }
}
