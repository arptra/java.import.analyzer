package com.example.importanalyzer.report;

import com.example.importanalyzer.core.MissingImportIssue;
import com.example.importanalyzer.core.ImportIssue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConsoleReportPrinterTest {
    @Test
    void rendersConsoleFriendlyOutput() {
        ImportIssue issue = new MissingImportIssue(Path.of("Example.java"), 3, "List", "No matching type found");
        String out = new ConsoleReportPrinter().render(List.of(issue));
        assertTrue(out.contains("Example.java"));
        assertTrue(out.contains("line 3"));
    }
}
