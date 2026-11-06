package com.gtnewhorizons.gtnhgradle.modules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the jar repacking of {@link JVMDowngraderModule} */
class JVMDowngraderModuleTest {

    @TempDir
    Path dir;

    private static final Map<String, byte[]> CONTENTS = Map.of(
        "META-INF/MANIFEST.MF",
        "Manifest-Version: 1.0\r\nMulti-Release: true\r\n\r\n".getBytes(StandardCharsets.UTF_8),
        "pkg/Compressible.class",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes(StandardCharsets.UTF_8),
        "pkg/Tiny.class",
        new byte[] { 1 },
        "assets/ünïcode.lang",
        "key=value\n".getBytes(StandardCharsets.UTF_8),
        "META-INF/versions/17/pkg/Compressible.class",
        "versioned".getBytes(StandardCharsets.UTF_8));

    /** Tools reading a jar as a stream expect the manifest first */
    private static final List<String> ORDER = List.of(
        "META-INF/MANIFEST.MF",
        "pkg/Compressible.class",
        "pkg/Tiny.class",
        "assets/ünïcode.lang",
        "META-INF/versions/17/pkg/Compressible.class");

    /** Writes the jar the same way the downgrade does, through a zip file system */
    private Path writeWithZipFileSystem() throws IOException {
        return writeWithZipFileSystem("downgraded.jar", ORDER, Instant.parse("2024-01-01T12:00:00Z"));
    }

    private Path writeWithZipFileSystem(String name, List<String> order, Instant timestamp) throws IOException {
        final Path zip = dir.resolve(name);
        try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream empty = new ZipOutputStream(os)) {
            // An empty zip container for the file system to open
        }
        final Map<String, String> env = new HashMap<>();
        env.put("create", "true");
        try (FileSystem fs = FileSystems.newFileSystem(zip, env)) {
            for (final String entry : order) {
                final Path path = fs.getPath(entry);
                if (path.getParent() != null) {
                    Files.createDirectories(path.getParent());
                }
                Files.write(path, CONTENTS.get(entry));
            }
            try (Stream<Path> paths = Files.walk(fs.getPath("/"))) {
                for (final Path path : paths.filter(p -> p.getParent() != null)
                    .toList()) {
                    Files.setLastModifiedTime(path, FileTime.from(timestamp));
                }
            }
        }
        return zip;
    }

    private static List<String> entryNames(ZipFile zip) {
        final List<String> names = new ArrayList<>();
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            names.add(
                entries.nextElement()
                    .getName());
        }
        return names;
    }

    @Test
    void repackKeepsEveryEntryAndDropsTheExtraFields() throws IOException {
        final Path source = writeWithZipFileSystem();
        final Path target = dir.resolve("repacked.jar");

        JVMDowngraderModule.repackWithoutExtraFields(source, target, true, false);

        try (ZipFile before = new ZipFile(source.toFile()); ZipFile after = new ZipFile(target.toFile())) {
            assertEquals(entryNames(before), entryNames(after), "entries and their order should be kept");
            assertEquals(
                "META-INF/MANIFEST.MF",
                entryNames(after).stream()
                    .filter(name -> !name.endsWith("/"))
                    .findFirst()
                    .orElseThrow(),
                "the manifest should stay the first file entry");

            for (final String name : entryNames(after)) {
                final ZipEntry oldEntry = before.getEntry(name);
                final ZipEntry newEntry = after.getEntry(name);
                assertEquals(oldEntry.getCrc(), newEntry.getCrc(), name + " CRC");
                assertEquals(oldEntry.getSize(), newEntry.getSize(), name + " size");
                assertEquals(oldEntry.getMethod(), newEntry.getMethod(), name + " compression method");
                assertEquals(oldEntry.getTimeLocal(), newEntry.getTimeLocal(), name + " timestamp");
                assertEquals(
                    oldEntry.getCompressedSize(),
                    newEntry.getCompressedSize(),
                    name + " should not be compressed again");
                assertNull(newEntry.getExtra(), name + " should have no extra field");
                if (!name.endsWith("/")) {
                    assertArrayEquals(
                        CONTENTS.get(name),
                        after.getInputStream(newEntry)
                            .readAllBytes(),
                        name + " contents");
                }
            }
        }

        assertTrue(
            Files.size(target) < Files.size(source),
            "dropping the extra fields should make the jar smaller, was " + Files.size(source)
                + " now "
                + Files.size(target));
    }

    @Test
    void repackHandlesAnEmptyJar() throws IOException {
        final Path source = dir.resolve("empty.jar");
        try (OutputStream os = Files.newOutputStream(source); ZipOutputStream empty = new ZipOutputStream(os)) {
            // Nothing to write
        }
        final Path target = dir.resolve("empty-repacked.jar");

        JVMDowngraderModule.repackWithoutExtraFields(source, target, false, true);

        try (ZipFile after = new ZipFile(target.toFile())) {
            assertEquals(List.of(), entryNames(after));
        }
    }

    @Test
    void reproducibleRepackIgnoresInputTimesAndOrder() throws IOException {
        final Path first = writeWithZipFileSystem();
        final List<String> reversed = new ArrayList<>(ORDER);
        Collections.reverse(reversed);
        final Path second = writeWithZipFileSystem("second.jar", reversed, Instant.parse("2026-03-01T12:00:00Z"));
        final Path firstOutput = dir.resolve("first-output.jar");
        final Path secondOutput = dir.resolve("second-output.jar");

        JVMDowngraderModule.repackWithoutExtraFields(first, firstOutput, false, true);
        JVMDowngraderModule.repackWithoutExtraFields(second, secondOutput, false, true);

        assertArrayEquals(Files.readAllBytes(firstOutput), Files.readAllBytes(secondOutput));
        try (ZipFile zip = new ZipFile(firstOutput.toFile())) {
            assertEquals("META-INF/MANIFEST.MF", entryNames(zip).getFirst());
            for (final String name : entryNames(zip)) {
                assertEquals(
                    LocalDateTime.of(1980, 2, 1, 0, 0),
                    zip.getEntry(name)
                        .getTimeLocal(),
                    name);
            }
        }
    }
}
