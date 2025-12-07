package com.example.importanalyzer.report;

import com.example.importanalyzer.core.MissingImportIssue;
import com.example.importanalyzer.core.ImportIssue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonReportGeneratorTest {
    @Test
    void rendersJson() {
        ImportIssue issue = new MissingImportIssue(Path.of("Example.java"), 1, "List", "No matching type found");
        String json = new JsonReportGenerator(true).toJson(List.of(issue));
        assertTrue(json.contains("Example.java"));
        assertTrue(json.contains("List"));
    }
}
