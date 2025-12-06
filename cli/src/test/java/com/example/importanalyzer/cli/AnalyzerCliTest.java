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
}
