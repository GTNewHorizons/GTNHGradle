package com.gtnewhorizons.gtnhgradle.tasks;

import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import com.gtnewhorizons.retrofuturagradle.shadow.org.apache.commons.compress.archivers.zip.ZipFile;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.jvm.tasks.Jar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Lets an archive task write its archive itself instead of packaging it entry by entry. */
public final class ArchiveFastPath {

    private ArchiveFastPath() {}

    /**
     * Puts a shortcut in front of a task's own actions, which only run when the shortcut declines. Register it before
     * the build scripts configure the task, so the shortcut runs after the doFirst actions they add and before their
     * doLast ones. Throwing {@link org.gradle.api.tasks.StopExecutionException} instead would skip those as well.
     *
     * @param task     The archive task
     * @param shortcut Writes the archive and returns true, or returns false to let the task package it as usual
     */
    public static void replacePackaging(Task task, Predicate<Task> shortcut) {
        final List<Action<? super Task>> packaging = new ArrayList<>(task.getActions());
        task.getActions()
            .clear();
        task.doFirst("Write the archive without packaging it, when that is equivalent", t -> {
            if (shortcut.test(t)) {
                return;
            }
            for (final Action<? super Task> action : packaging) {
                action.execute(t);
            }
        });
    }

    /**
     * @param task A jar task
     * @return The class names of its copy actions, empty if it has custom ones. Taken before the build scripts
     *         configure the task, this tells the actions Gradle added from the ones they did.
     */
    public static Set<String> copyActions(Jar task) {
        final Set<String> copyActions = new HashSet<>();
        if (!task.getRootSpec()
            .hasCustomActions()) {
            task.getRootSpec()
                .walk(
                    spec -> spec.getAllCopyActions()
                        .forEach(
                            action -> copyActions.add(
                                action.getClass()
                                    .getName())));
        }
        return copyActions;
    }

    /**
     * @param task            A jar task
     * @param allowedExcludes Exclude patterns that don't count
     * @return Whether its copy rules can leave files out or change them
     */
    public static boolean filtersEntries(Jar task, Set<String> allowedExcludes) {
        final boolean[] filters = { task.getRootSpec()
            .hasCustomActions() };
        task.getRootSpec()
            .walk(
                spec -> filters[0] |= !spec.getAllIncludes()
                    .isEmpty() || !allowedExcludes.containsAll(spec.getAllExcludes())
                    || !spec.getAllIncludeSpecs()
                        .isEmpty()
                    || !spec.getAllExcludeSpecs()
                        .isEmpty());
        return filters[0];
    }

    /**
     * Copies some entries of a jar, keeping their compressed data as it is.
     *
     * @param source  The jar to copy from
     * @param target  The jar to write
     * @param entries The names of the entries to keep. A plain file copy when that is all of them.
     * @throws IOException If a jar can't be read or written
     */
    public static void copyEntries(Path source, Path target, Set<String> entries) throws IOException {
        Files.createDirectories(target.getParent());
        try (ZipFile in = new ZipFile(source)) {
            final boolean keepsEverything = Collections.list(in.getEntries())
                .stream()
                .map(ZipArchiveEntry::getName)
                .allMatch(entries::contains);
            if (keepsEverything) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
            try (ZipArchiveOutputStream out = new ZipArchiveOutputStream(target)) {
                in.copyRawEntries(out, entry -> entries.contains(entry.getName()));
            }
        }
    }
}
