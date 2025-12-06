package com.example.importanalyzer.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImportAnalyzerTest {
    @Test
    void detectsMissingAndUnusedImports() throws Exception {
        Path root = Files.createTempDirectory("analyzer");
        Path src = root.resolve("src/main/java/demo");
        Files.createDirectories(src);
        Path file = src.resolve("Sample.java");
        Files.writeString(file, "package demo; import java.util.List; import java.util.Set; public class Sample { String name; List<String> names; Map map; }");

        ImportAnalyzer analyzer = new ImportAnalyzerBuilder()
                .projectRoot(root)
                .sourceRoot(root.resolve("src/main/java"))
                .includeDependencies(false)
                .threads(2)
                .cacheEnabled(false)
                .build();
        List<ImportIssue> issues = analyzer.analyze();
        assertFalse(issues.isEmpty());
    }
}
