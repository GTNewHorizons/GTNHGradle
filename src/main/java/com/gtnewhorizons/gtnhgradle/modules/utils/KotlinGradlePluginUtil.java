package com.gtnewhorizons.gtnhgradle.modules.utils;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.jspecify.annotations.NonNull;

import java.lang.reflect.Method;
import java.util.Set;

/// Call KGP in separated class to prevent [ClassNotFoundException] and [NoClassDefFoundError]
public class KotlinGradlePluginUtil {

    // FIXME: Kotlin Gradle Plugin is loaded in child classloader, so that calling it will result in
    // ClassNotFoundException.
    // Separating GTNHG into settings plugin and project plugin would likely fix this issue.
    @SuppressWarnings("SameParameterValue")
    private static void jvmToolchain(Object kotlin, int i) {
        try {
            // jvmToolchain(int) is public in parent classes, so we use getMethod
            Method jvmToolchain = kotlin.getClass()
                .getMethod("jvmToolchain", int.class);
            jvmToolchain.invoke(kotlin, i);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to invoke jvmToolchain", e);
        }
    }

    public static void configureKotlinGradlePlugin(@NonNull Project project, TaskContainer tasks) {
        Object kotlin = project.getExtensions()
            .getByName("kotlin");

        jvmToolchain(kotlin, 8);
        final Set<String> disabledKotlinTasks = Set.of(
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
    }
}
