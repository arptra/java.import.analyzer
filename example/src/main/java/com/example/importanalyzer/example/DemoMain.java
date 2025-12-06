package com.example.importanalyzer.example;

import com.example.importanalyzer.core.ImportAnalyzer;
import com.example.importanalyzer.core.ImportAnalyzerBuilder;
import com.example.importanalyzer.core.ImportIssue;
import com.example.importanalyzer.report.ConsoleReportPrinter;

import java.nio.file.Path;
import java.util.List;

public class DemoMain {
    public static void main(String[] args) {
        Path project = Path.of(".");
        ImportAnalyzer analyzer = new ImportAnalyzerBuilder()
                .sourceRoot(project.resolve("src/main/java"))
                .testSourceRoot(project.resolve("src/test/java"))
                .includeDependencies(false)
                .build();
        List<ImportIssue> issues = analyzer.analyze();
        System.out.println(new ConsoleReportPrinter().render(issues));
    }
}
