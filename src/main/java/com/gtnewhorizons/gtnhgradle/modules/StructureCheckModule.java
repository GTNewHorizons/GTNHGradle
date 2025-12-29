package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHConstants;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

/** Checks the project structure for obvious mistakes */
public class StructureCheckModule implements GTNHModule {

    @Override
    public boolean isEnabled(GTNHGradlePlugin.@NotNull GTNHExtension gtnh) {
        return gtnh.configuration.moduleStructureCheck;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) {
        final String modGroupPath = gtnh.configuration.modGroup.replace('.', '/');
        final String apiPackagePath = gtnh.configuration.apiPackage.replace('.', '/');

        String targetPackageJava = GTNHConstants.JAVA_SOURCES_DIR + modGroupPath;
        String targetPackageScala = GTNHConstants.SCALA_SOURCES_DIR + modGroupPath;
        String targetPackageKotlin = GTNHConstants.KOTLIN_SOURCES_DIR + modGroupPath;

        if (!(project.file(targetPackageJava)
            .exists()
            || project.file(targetPackageScala)
                .exists()
            || project.file(targetPackageKotlin)
                .exists())) {
            throw new GradleException(
                "Could not resolve \"modGroup\"! Could not find " + targetPackageJava
                    + " or "
                    + targetPackageScala
                    + " or "
                    + targetPackageKotlin);
        }

        if (!gtnh.configuration.apiPackage.isEmpty()) {
            targetPackageJava = GTNHConstants.JAVA_SOURCES_DIR + modGroupPath + "/" + apiPackagePath;
            targetPackageScala = GTNHConstants.SCALA_SOURCES_DIR + modGroupPath + "/" + apiPackagePath;
            targetPackageKotlin = GTNHConstants.KOTLIN_SOURCES_DIR + modGroupPath + "/" + apiPackagePath;
            if (!(project.file(targetPackageJava)
                .exists()
                || project.file(targetPackageScala)
                    .exists()
                || project.file(targetPackageKotlin)
                    .exists())) {
                throw new GradleException(
                    "Could not resolve \"apiPackage\"! Could not find " + targetPackageJava
                        + " or "
                        + targetPackageScala
                        + " or "
                        + targetPackageKotlin);
            }
        }

        if (gtnh.configuration.usesMixins) {
            if (gtnh.configuration.mixinsPackage.isEmpty()) {
                throw new GradleException("\"usesMixins\" requires \"mixinsPackage\" to be set!");
            }
            final String mixinPackagePath = gtnh.configuration.mixinsPackage.replaceAll("\\.", "/");
            final String mixinPluginPath = gtnh.configuration.mixinPlugin.replaceAll("\\.", "/");
            String mixinSourceSet = gtnh.configuration.separateMixinSourceSet.trim();
            if (mixinSourceSet.isEmpty()) {
                mixinSourceSet = "main";
            }

            targetPackageJava = "src/" + mixinSourceSet + "/java/" + modGroupPath + "/" + mixinPackagePath;
            targetPackageScala = "src/" + mixinSourceSet + "/scala/" + modGroupPath + "/" + mixinPackagePath;
            targetPackageKotlin = "src/" + mixinSourceSet + "/kotlin/" + modGroupPath + "/" + mixinPackagePath;
            if (!(project.file(targetPackageJava)
                .exists()
                || project.file(targetPackageScala)
                    .exists()
                || project.file(targetPackageKotlin)
                    .exists())) {
                throw new GradleException(
                    "Could not resolve \"mixinsPackage\"! Could not find " + targetPackageJava
                        + " or "
                        + targetPackageScala
                        + " or "
                        + targetPackageKotlin);
            }

            if (!gtnh.configuration.mixinPlugin.isEmpty()) {
                String targetFileJava = GTNHConstants.JAVA_SOURCES_DIR + modGroupPath + "/" + mixinPluginPath + ".java";
                String targetFileScala = GTNHConstants.SCALA_SOURCES_DIR + modGroupPath
                    + "/"
                    + mixinPluginPath
                    + ".scala";
                String targetFileScalaJava = GTNHConstants.SCALA_SOURCES_DIR + modGroupPath
                    + "/"
                    + mixinPluginPath
                    + ".java";
                String targetFileKotlin = GTNHConstants.KOTLIN_SOURCES_DIR + modGroupPath
                    + "/"
                    + mixinPluginPath
                    + ".kt";
                if (!(project.file(targetFileJava)
                    .exists()
                    || project.file(targetFileScala)
                        .exists()
                    || project.file(targetFileScalaJava)
                        .exists()
                    || project.file(targetFileKotlin)
                        .exists())) {
                    throw new GradleException(
                        "Could not resolve \"mixinPlugin\"! Could not find " + targetFileJava
                            + " or "
                            + targetFileScala
                            + " or "
                            + targetFileScalaJava
                            + " or "
                            + targetFileKotlin);
                }
            }
        }

        if (!gtnh.configuration.coreModClass.isEmpty()) {
            final String coreModPath = gtnh.configuration.coreModClass.replaceAll("\\.", "/");
            String targetFileJava = GTNHConstants.JAVA_SOURCES_DIR + modGroupPath + "/" + coreModPath + ".java";
            String targetFileScala = GTNHConstants.SCALA_SOURCES_DIR + modGroupPath + "/" + coreModPath + ".scala";
            String targetFileScalaJava = GTNHConstants.SCALA_SOURCES_DIR + modGroupPath + "/" + coreModPath + ".java";
            String targetFileKotlin = GTNHConstants.KOTLIN_SOURCES_DIR + modGroupPath + "/" + coreModPath + ".kt";
            if (!(project.file(targetFileJava)
                .exists()
                || project.file(targetFileScala)
                    .exists()
                || project.file(targetFileScalaJava)
                    .exists()
                || project.file(targetFileKotlin)
                    .exists())) {
                throw new GradleException(
                    "Could not resolve \"coreModClass\"! Could not find " + targetFileJava
                        + " or "
                        + targetFileScala
                        + " or "
                        + targetFileScalaJava
                        + " or "
                        + targetFileKotlin);
            }
        }
    }
}
