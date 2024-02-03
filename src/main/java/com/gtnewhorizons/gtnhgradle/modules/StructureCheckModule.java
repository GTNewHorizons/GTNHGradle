package com.gtnewhorizons.gtnhgradle.modules;

import com.gtnewhorizons.gtnhgradle.GTNHConstants;
import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin;
import com.gtnewhorizons.gtnhgradle.GTNHModule;
import com.gtnewhorizons.gtnhgradle.PropertiesConfiguration;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

/** Checks the project structure for obvious mistakes */
public class StructureCheckModule implements GTNHModule {

    @Override
    public boolean isEnabled(@NotNull PropertiesConfiguration configuration) {
        return configuration.moduleStructureCheck;
    }

    @Override
    public void apply(GTNHGradlePlugin.@NotNull GTNHExtension gtnh, @NotNull Project project) {
        final String modGroupPath = gtnh.configuration.modGroup.replace('.', '/');
        final String apiPackagePath = gtnh.configuration.apiPackage.replace('.', '/');

        String targetPackageJava = GTNHConstants.JAVA_SOURCES_DIR + modGroupPath;
        String targetPackageScala = GTNHConstants.SCALA_SOURCES_DIR + modGroupPath;
        String targetPackageKotlin = GTNHConstants.KOTLIN_SOURCES_DIR + modGroupPath;

        final Path projectRoot = project.getProjectDir()
            .toPath();

        if (!(Files.exists(projectRoot.resolve(targetPackageJava))
            || Files.exists(projectRoot.resolve(targetPackageScala))
            || Files.exists(projectRoot.resolve(targetPackageKotlin)))) {
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
            if (!(Files.exists(projectRoot.resolve(targetPackageJava))
                || Files.exists(projectRoot.resolve(targetPackageScala))
                || Files.exists(projectRoot.resolve(targetPackageKotlin)))) {
                throw new GradleException(
                    "Could not resolve \"apiPackage\"! Could not find " + targetPackageJava
                        + " or "
                        + targetPackageScala
                        + " or "
                        + targetPackageKotlin);
            }
        }
    }
}
