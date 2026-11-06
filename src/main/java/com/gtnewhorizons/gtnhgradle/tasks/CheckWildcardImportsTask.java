package com.gtnewhorizons.gtnhgradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileType;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.ChangeType;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fails on non-static wildcard imports like {@code import java.util.*;}, which IDEs tend to create on their own.
 * Static wildcard imports are allowed, and imports between {@code CHECKSTYLE:OFF} and {@code CHECKSTYLE:ON} comments
 * are skipped, like with the Checkstyle setup this replaces. Only the files changed since the last run are read again.
 */
@DisableCachingByDefault(because = "Only reads the start of the changed source files, cheaper than a cache lookup")
public abstract class CheckWildcardImportsTask extends DefaultTask {

    private static final Pattern WILDCARD_IMPORT = Pattern
        .compile("\\bimport\\s+(?!static\\b)([\\w$]+(?:\\s*\\.\\s*[\\w$]+)*)\\s*\\.\\s*\\*\\s*;");
    private static final String SUPPRESSION_OFF = "CHECKSTYLE:OFF";
    private static final String SUPPRESSION_ON = "CHECKSTYLE:ON";

    /** @return The Java source files to check, incremental through {@link SkipWhenEmpty} */
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getSource();

    /** @return Wildcard imports found by the previous runs per source file, so unchanged files are not read again */
    @OutputFile
    public abstract RegularFileProperty getResultsFile();

    /**
     * Checks the source files changed since the last run, then fails if any source file has a wildcard import.
     *
     * @param inputChanges The changed source files
     * @throws IOException If a source file or the results file can't be read or written
     */
    @TaskAction
    public void checkImports(InputChanges inputChanges) throws IOException {
        final Path resultsFile = getResultsFile().get()
            .getAsFile()
            .toPath();
        final Map<String, List<String>> results = inputChanges.isIncremental() ? readResults(resultsFile)
            : new TreeMap<>();
        for (final FileChange change : inputChanges.getFileChanges(getSource())) {
            if (change.getFileType() == FileType.DIRECTORY) {
                continue;
            }
            results.remove(change.getNormalizedPath());
            if (change.getChangeType() != ChangeType.REMOVED) {
                final List<String> found = findWildcardImports(change.getFile());
                if (!found.isEmpty()) {
                    results.put(change.getNormalizedPath(), found);
                }
            }
        }
        writeResults(resultsFile, results);

        if (!results.isEmpty()) {
            final StringBuilder message = new StringBuilder(
                "Wildcard imports found. Replace them with explicit imports, or put them between"
                    + " // CHECKSTYLE:OFF and // CHECKSTYLE:ON comments (static wildcard imports are allowed):");
            for (final List<String> found : results.values()) {
                for (final String wildcardImport : found) {
                    message.append("\n  ")
                        .append(wildcardImport);
                }
            }
            throw new GradleException(message.toString());
        }
    }

    /**
     * Reads complete package and import statements, including statements split across lines. Stops at the first type
     * declaration without scanning class bodies.
     *
     * @param file The Java source file
     * @return One "file:line: import" entry per non-static wildcard import
     * @throws IOException If the file can't be read
     */
    static List<String> findWildcardImports(File file) throws IOException {
        final HeaderScanner scanner = new HeaderScanner(file);
        // Never fails to decode, and the keywords and import names looked for are ASCII
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.ISO_8859_1)) {
            String line = reader.readLine();
            while (line != null && scanner.scanLine(line)) {
                line = reader.readLine();
            }
        }
        return scanner.found;
    }

    /** Walks the package and import statements of a source file, keeping code and comments in file order */
    private static final class HeaderScanner {

        private final File file;
        private final List<String> found = new ArrayList<>();
        private final StringBuilder statement = new StringBuilder();
        private boolean inBlockComment;
        private boolean suppressed;
        private boolean seenPackageOrImport;
        private int annotationParens;
        private int statementLine;
        private int lineNumber;

        private HeaderScanner(File file) {
            this.file = file;
        }

        /** @return false once the header is over */
        private boolean scanLine(String line) {
            lineNumber++;
            int i = 0;
            while (i < line.length()) {
                if (inBlockComment) {
                    final int end = line.indexOf("*/", i);
                    readComment(line.substring(i, end < 0 ? line.length() : end));
                    if (end < 0) {
                        return true;
                    }
                    inBlockComment = false;
                    i = end + 2;
                    continue;
                }
                final int lineComment = line.indexOf("//", i);
                final int blockComment = line.indexOf("/*", i);
                final boolean lineCommentFirst = lineComment >= 0 && (blockComment < 0 || lineComment < blockComment);
                final int codeEnd = lineCommentFirst ? lineComment : blockComment < 0 ? line.length() : blockComment;
                if (!scanCode(line.substring(i, codeEnd))) {
                    return false;
                }
                if (lineCommentFirst) {
                    readComment(line.substring(lineComment));
                    return true;
                }
                if (blockComment < 0) {
                    return true;
                }
                inBlockComment = true;
                i = blockComment + 2;
            }
            return true;
        }

        /** Like Checkstyle's SuppressionCommentFilter, a suppression comment applies from its position onwards */
        private void readComment(String comment) {
            final int off = comment.lastIndexOf(SUPPRESSION_OFF);
            final int on = comment.lastIndexOf(SUPPRESSION_ON);
            if (off > on) {
                suppressed = true;
            } else if (on > off) {
                suppressed = false;
            }
        }

        /** @return false once the header is over */
        private boolean scanCode(String text) {
            String code = text.trim();
            while (!code.isEmpty()) {
                if (statement.isEmpty()) {
                    if (code.charAt(0) == ';') {
                        code = code.substring(1)
                            .trim();
                        continue;
                    }
                    if (!seenPackageOrImport && (annotationParens > 0 || code.charAt(0) == '@')) {
                        code = skipAnnotation(code);
                        continue;
                    }
                    if (!startsWithKeyword(code, "package") && !startsWithKeyword(code, "import")) {
                        return false;
                    }
                    seenPackageOrImport = true;
                    statementLine = lineNumber;
                }
                final int end = code.indexOf(';');
                if (end < 0) {
                    statement.append(code)
                        .append(' ');
                    return true;
                }
                statement.append(code, 0, end + 1);
                final Matcher matcher = WILDCARD_IMPORT.matcher(statement);
                while (!suppressed && matcher.find()) {
                    final String name = matcher.group(1)
                        .replaceAll("\\s+", "");
                    found.add(file + ":" + statementLine + ": import " + name + ".*;");
                }
                statement.setLength(0);
                code = code.substring(end + 1)
                    .trim();
            }
            return true;
        }

        /** @return What follows the annotation on this line, annotationParens carries the rest to the next ones */
        private String skipAnnotation(String code) {
            String rest = code;
            if (annotationParens == 0) {
                int nameEnd = 1;
                while (nameEnd < rest.length()
                    && (Character.isJavaIdentifierPart(rest.charAt(nameEnd)) || rest.charAt(nameEnd) == '.')) {
                    nameEnd++;
                }
                rest = rest.substring(nameEnd)
                    .trim();
                if (!rest.startsWith("(")) {
                    return rest;
                }
            }
            char quote = 0;
            for (int i = 0; i < rest.length(); i++) {
                final char c = rest.charAt(i);
                if (quote != 0) {
                    if (c == '\\') {
                        i++;
                    } else if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '"' || c == '\'') {
                    quote = c;
                } else if (c == '(') {
                    annotationParens++;
                } else if (c == ')' && --annotationParens == 0) {
                    return rest.substring(i + 1)
                        .trim();
                }
            }
            return "";
        }
    }

    private static boolean startsWithKeyword(String code, String keyword) {
        return code.startsWith(keyword)
            && (code.length() == keyword.length() || !Character.isJavaIdentifierPart(code.charAt(keyword.length())));
    }

    private static Map<String, List<String>> readResults(Path resultsFile) throws IOException {
        final Map<String, List<String>> results = new TreeMap<>();
        if (Files.isRegularFile(resultsFile)) {
            for (final String line : Files.readAllLines(resultsFile, StandardCharsets.UTF_8)) {
                final int tab = line.indexOf('\t');
                if (tab > 0) {
                    results.computeIfAbsent(line.substring(0, tab), k -> new ArrayList<>())
                        .add(line.substring(tab + 1));
                }
            }
        }
        return results;
    }

    private static void writeResults(Path resultsFile, Map<String, List<String>> results) throws IOException {
        final List<String> lines = new ArrayList<>();
        results.forEach((path, found) -> found.forEach(wildcardImport -> lines.add(path + "\t" + wildcardImport)));
        Files.createDirectories(resultsFile.getParent());
        Files.write(resultsFile, lines, StandardCharsets.UTF_8);
    }
}
