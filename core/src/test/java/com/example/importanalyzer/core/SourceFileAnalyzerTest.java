package com.example.importanalyzer.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SourceFileAnalyzerTest {
    @Test
    void parsesBasicFile() throws IOException {
        Path temp = Files.createTempFile("Sample", ".java");
        Files.writeString(temp, "package demo; import java.util.List; public class Sample { List<String> names; }");
        SourceFileResult result = SourceFileAnalyzer.analyze(temp);
        assertEquals("demo", result.packageName());
        assertTrue(result.imports().containsKey("java.util.List"));
        assertTrue(result.usedTypes().contains("List"));
        Files.deleteIfExists(temp);
    }
}
