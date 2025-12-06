package com.example.importanalyzer.example;

import com.example.importanalyzer.core.ImportAnalyzerBuilder;
import com.example.importanalyzer.core.ImportIssue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DemoMainTest {
    @Test
    void detectsIssuesInExample() {
        List<ImportIssue> issues = new ImportAnalyzerBuilder()
                .sourceRoot(Path.of("src/main/java"))
                .includeDependencies(false)
                .cacheEnabled(false)
                .build()
                .analyze();
        assertFalse(issues.isEmpty());
    }
}
