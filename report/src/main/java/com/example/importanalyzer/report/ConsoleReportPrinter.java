package com.example.importanalyzer.report;

import com.example.importanalyzer.core.ImportIssue;
import com.example.importanalyzer.core.IssueType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConsoleReportPrinter {
    public String render(List<ImportIssue> issues) {
        StringBuilder sb = new StringBuilder();
        Map<String, List<ImportIssue>> byFile = issues.stream().collect(Collectors.groupingBy(issue -> issue.file().toString()));
        byFile.keySet().stream().sorted().forEach(file -> {
            sb.append("\u001B[36m").append(file).append("\u001B[0m\n");
            Map<IssueType, List<ImportIssue>> byType = byFile.get(file).stream().collect(Collectors.groupingBy(ImportIssue::type));
            byType.forEach((type, list) -> {
                sb.append("  ").append(type).append("\n");
                list.stream().sorted(Comparator.comparingInt(ImportIssue::line)).forEach(issue -> {
                    sb.append("    line ").append(issue.line()).append(": ").append(issue.message()).append(" [").append(issue.symbol()).append("]\n");
                });
            });
        });
        if (issues.isEmpty()) {
            sb.append("No issues found\n");
        }
        return sb.toString();
    }
}
