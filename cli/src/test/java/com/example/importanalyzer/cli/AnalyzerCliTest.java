package com.example.importanalyzer.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.*;

class AnalyzerCliTest {
    @Test
    void displaysUsage() {
        CommandLine cmd = new CommandLine(new AnalyzerCli());
        assertEquals(0, cmd.execute());
    }

    @Test
    void jsonOutputContainsActionableGuidance() {
        CommandLine cmd = new CommandLine(new AnalyzerCli());
        var buffer = new java.io.ByteArrayOutputStream();
        var originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer));
        try {
            int exit = cmd.execute("json", "--project", "example", "--with-deps=false", "--pretty");
            assertEquals(0, exit);
        } finally {
            System.setOut(originalOut);
        }

        String output = buffer.toString();
        assertTrue(output.contains("Add import"), "Expected guidance to add missing imports: " + output);
        assertTrue(output.contains("Remove unused import"), "Expected guidance to remove unused imports: " + output);
    }
}
