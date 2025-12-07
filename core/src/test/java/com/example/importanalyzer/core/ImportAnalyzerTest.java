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

    @Test
    void respectsImportsUsedInAnnotations() throws Exception {
        Path root = Files.createTempDirectory("analyzerAnnotations");
        Path src = root.resolve("src/main/java/demo");
        Files.createDirectories(src);
        Path file = src.resolve("SampleTest.java");
        Files.writeString(file, """
                package demo;
                import org.junit.jupiter.api.Test;
                public class SampleTest { @Test void ok() {} }
                """.stripIndent());

        ImportAnalyzer analyzer = new ImportAnalyzerBuilder()
                .projectRoot(root)
                .sourceRoot(root.resolve("src/main/java"))
                .includeDependencies(true)
                .threads(2)
                .cacheEnabled(false)
                .build();
        List<ImportIssue> issues = analyzer.analyze();
        assertTrue(issues.stream().noneMatch(issue -> issue instanceof UnusedImportIssue), "Annotation imports should be marked as used");
    }

    @Test
    void reportsMissingPackageWithoutNoisySuggestions() throws Exception {
        Path root = Files.createTempDirectory("missingPackage");
        Path src = root.resolve("src/main/java");
        Files.createDirectories(src.resolve("demo"));
        Files.createDirectories(src.resolve("other"));

        Path other = src.resolve("other/UnknownType.java");
        Files.writeString(other, "package other; public class UnknownType {}");

        Path file = src.resolve("demo/Use.java");
        Files.writeString(file, """
                package demo;
                import demo.missing.UnknownType;
                public class Use { UnknownType field; }
                """.stripIndent());

        ImportAnalyzer analyzer = new ImportAnalyzerBuilder()
                .projectRoot(root)
                .sourceRoot(src)
                .includeDependencies(false)
                .threads(2)
                .cacheEnabled(false)
                .build();

        List<ImportIssue> issues = analyzer.analyze();
        assertEquals(1, issues.size());
        ImportIssue issue = issues.get(0);
        assertTrue(issue instanceof UnresolvedImportIssue);
        assertTrue(issue.message().contains("Package demo.missing not found"));
    }

    @Test
    void favorsCandidatesProvidingCalledStaticMethods() throws Exception {
        Path root = Files.createTempDirectory("staticPref");
        Path src = root.resolve("src/main/java");
        Files.createDirectories(src.resolve("a"));
        Files.createDirectories(src.resolve("b"));
        Files.createDirectories(src.resolve("sample"));

        Path aAssertions = src.resolve("a/Assertions.java");
        Files.writeString(aAssertions, "package a; public class Assertions { public static void ok() {} }");

        Path bAssertions = src.resolve("b/Assertions.java");
        Files.writeString(bAssertions, "package b; public class Assertions { public static void assertTrue(boolean val) {} }");

        Path file = src.resolve("sample/Use.java");
        Files.writeString(file, """
                package sample;
                public class Use { void test() { Assertions.assertTrue(true); } }
                """.stripIndent());

        ImportAnalyzer analyzer = new ImportAnalyzerBuilder()
                .projectRoot(root)
                .sourceRoot(src)
                .includeDependencies(false)
                .threads(2)
                .cacheEnabled(false)
                .build();

        List<ImportIssue> issues = analyzer.analyze();
        assertTrue(issues.stream().anyMatch(issue -> issue instanceof MissingImportIssue
                && issue.message().contains("b.Assertions")), "Should prefer Assertions class that defines assertTrue");
    }
}
