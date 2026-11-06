package com.gtnewhorizons.gtnhgradle.tasks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for the source scanning of {@link CheckWildcardImportsTask} */
class CheckWildcardImportsTaskTest {

    @TempDir
    File dir;

    private List<String> scan(String source) throws IOException {
        final File file = new File(dir, "Test.java");
        Files.writeString(file.toPath(), source, StandardCharsets.UTF_8);
        return CheckWildcardImportsTask.findWildcardImports(file)
            .stream()
            .map(
                found -> found.substring(
                    file.toString()
                        .length()))
            .toList();
    }

    // The sources below have wildcard imports on purpose, which Spotless rejects
    // spotless:off
    @Test
    void findsWildcardImports() throws IOException {
        assertEquals(List.of(":3: import java.util.*;", ":4: import java.io.*;", ":4: import java.nio.*;"), scan("""
            package com.example;

            import java.util.*;
            import java.io.*; import java.nio . * ;
            import java.util.List;

            public class Test {}
            """));
    }

    @Test
    void findsMultilineImportsAndImportsAfterThem() throws IOException {
        assertEquals(List.of(":5: import java.nio.*;", ":6: import java.io.*;", ":9: import java.text.*;"), scan("""
            package com.example;

            import java.util.
                List;
            import java.nio.*;
            import
                java.io /* continued */ .
                *
                ; import/* separator */java.text.*;
            public class Test {}
            """));
    }

    @Test
    void multilineImportsKeepStaticAndSuppressionRules() throws IOException {
        assertEquals(List.of(":9: import java.nio.*;"), scan("""
            package com.example;
            import
                static java.lang.Math
                .*;
            // CHECKSTYLE:OFF
            import java.
                util.*;
            // CHECKSTYLE:ON
            import java.
                nio.*;
            public class Test {}
            """));
    }

    @Test
    void allowsStaticWildcardImports() throws IOException {
        assertEquals(List.of(), scan("""
            package com.example;

            import static org.lwjgl.opengl.GL11.*;

            public class Test {}
            """));
        // A package whose name starts with "static" is not a static import
        assertEquals(List.of(":3: import staticfoo.*;"), scan("""
            package com.example;
            import static org.lwjgl.opengl.GL11.*;
            import staticfoo.*;
            """));
    }

    @Test
    void skipsSuppressedImports() throws IOException {
        assertEquals(List.of(":7: import java.nio.*;"), scan("""
            package com.example;

            // CHECKSTYLE:OFF
            import java.util.*;
            import java.io.*; // CHECKSTYLE:ON
            /* CHECKSTYLE:ON */
            import java.nio.*;
            """));
    }

    @Test
    void ignoresComments() throws IOException {
        assertEquals(List.of(":9: import java.io.*;"), scan("""
            /*
             * License header
            import java.util.*;
             */
            package com.example;

            // import java.util.*;
            /* import java.util.*; */
            /* still a comment */ import java.io.*;
            """));
    }

    @Test
    void stopsAtTheFirstTypeDeclaration() throws IOException {
        assertEquals(List.of(), scan("""
            @ParametersAreNonnullByDefault
            package com.example;

            import java.util.List;

            @Deprecated
            public class Test {
                String code = \"""
                    import java.util.*;
                    \""";
            }
            import java.io.*;
            """));
    }

    @Test
    void suppressionAppliesFromItsPositionOnTheLine() throws IOException {
        assertEquals(List.of(":4: import java.nio.*;"), scan("""
            package com.example;
            /* CHECKSTYLE:OFF */ import java.util.*;
            // CHECKSTYLE:ON
            import java.nio.*; /* CHECKSTYLE:OFF */
            import java.io.*;
            """));
    }

    @Test
    void strayStatementSeparatorsDoNotEndTheHeader() throws IOException {
        assertEquals(List.of(":3: import java.util.*;"), scan("""
            package com.example;;
            import java.io.File;;
            import java.util.*;
            """));
    }

    @Test
    void readsPastMultilinePackageAnnotations() throws IOException {
        assertEquals(List.of(":6: import java.util.*;"), scan("""
            @Deprecated(
                since = "1)")
            @com.example.Marker(value = { ')' },
                other = "x") @Other
            package com.example;
            import java.util.*;
            """));
    }
    // spotless:on
}
