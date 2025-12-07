package com.example.importanalyzer.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AsyncImportAnalyzerServiceTest {

    @Test
    void returnsAddActionWhenSingleCandidateExists() throws Exception {
        Path project = Files.createTempDirectory("import-analyzer-service");
        Path src = project.resolve("src/main/java");
        Path util = src.resolve("demo/util");
        Files.createDirectories(util);
        Path helper = util.resolve("Helper.java");
        Files.writeString(helper, "package demo.util; public class Helper {}");
        Path usageDir = src.resolve("demo");
        Files.createDirectories(usageDir);
        Path usage = usageDir.resolve("UsesHelper.java");
        Files.writeString(usage, "package demo; public class UsesHelper { Helper h; }");

        ImportAnalyzerConfig config = new ImportAnalyzerBuilder()
                .projectRoot(project)
                .sourceRoot(src.getParent())
                .threads(2)
                .includeDependencies(false)
                .buildConfig();

        AsyncImportAnalyzerService service = new AsyncImportAnalyzerService(config);
        ScanResult result;
        do {
            result = service.scan(usage).join();
            if (result.inProgress()) {
                Thread.sleep(10);
            }
        } while (result.inProgress());

        System.out.println("Scan result: " + result);

        assertEquals(ImportAction.ADD, result.action());
        assertTrue(result.candidates().contains("demo.util.Helper"));
        assertEquals(usage, result.file());
    }

    @Test
    void returnsDeleteForUnresolvedImport() throws IOException, InterruptedException {
        Path project = Files.createTempDirectory("import-analyzer-service2");
        Path src = project.resolve("src/main/java/sample");
        Files.createDirectories(src);
        Path usage = src.resolve("Broken.java");
        Files.writeString(usage, "package sample; import no.such.Type; public class Broken {}");

        ImportAnalyzerConfig config = new ImportAnalyzerBuilder()
                .projectRoot(project)
                .sourceRoot(src.getParent())
                .threads(2)
                .includeDependencies(false)
                .buildConfig();

        AsyncImportAnalyzerService service = new AsyncImportAnalyzerService(config);
        ScanResult result;
        do {
            result = service.scan(usage).join();
            if (result.inProgress()) {
                Thread.sleep(10);
            }
        } while (result.inProgress());

        assertEquals(ImportAction.DELETE, result.action());
        assertEquals(usage, result.file());
        assertTrue(result.line() >= 1);
    }
}
