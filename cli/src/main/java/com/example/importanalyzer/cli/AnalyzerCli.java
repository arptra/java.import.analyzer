package com.example.importanalyzer.cli;

import com.example.importanalyzer.core.ImportAnalyzer;
import com.example.importanalyzer.core.ImportAnalyzerBuilder;
import com.example.importanalyzer.core.ImportIssue;
import com.example.importanalyzer.report.ConsoleReportPrinter;
import com.example.importanalyzer.report.JsonReportGenerator;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "import-analyzer", subcommands = {
        AnalyzerCli.AnalyzeCommand.class,
        AnalyzerCli.JsonCommand.class,
        AnalyzerCli.SummaryCommand.class,
        AnalyzerCli.DebugCommand.class
})
public class AnalyzerCli implements Runnable {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }

    private static ImportAnalyzer build(Path project, boolean deps, Integer threads, Path cache, boolean reuse, boolean cacheEnabled) {
        Path projectRoot = resolveProject(project);
        ImportAnalyzerBuilder builder = new ImportAnalyzerBuilder()
                .projectRoot(projectRoot)
                .sourceRoot(projectRoot.resolve("src/main/java"))
                .testSourceRoot(projectRoot.resolve("src/test/java"))
                .includeDependencies(deps)
                .cacheEnabled(cacheEnabled);
        if (threads != null) {
            builder.threads(threads);
        }
        if (cache != null) {
            builder.indexCachePath(cache);
        }
        builder.reuseIndex(reuse);
        return builder.build();
    }

    private static Path resolveProject(Path provided) {
        if (provided.isAbsolute() && provided.toFile().exists()) {
            return provided.normalize();
        }
        Path cwd = Path.of("").toAbsolutePath();
        Path direct = cwd.resolve(provided).normalize();
        if (direct.toFile().exists()) {
            return direct;
        }
        Path parentFallback = cwd.getParent() != null ? cwd.getParent().resolve(provided).normalize() : direct;
        return parentFallback;
    }

    @CommandLine.Command(name = "analyze", description = "Analyze imports and print console report")
    static class AnalyzeCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--project", required = true)
        Path project;
        @CommandLine.Option(names = "--with-deps", defaultValue = "true", description = "Scan project dependencies (default: true)")
        boolean deps;
        @CommandLine.Option(names = "--threads")
        Integer threads;
        @CommandLine.Option(names = "--index-cache")
        Path cache;
        @CommandLine.Option(names = "--reuse-index")
        boolean reuse;
        @CommandLine.Option(names = "--no-cache")
        boolean noCache;

        @Override
        public Integer call() {
            ImportAnalyzer analyzer = build(project, deps, threads, cache, reuse, !noCache);
            List<ImportIssue> issues = analyzer.analyze();
            String out = new ConsoleReportPrinter().render(issues);
            System.out.println(out);
            return 0;
        }
    }

    @CommandLine.Command(name = "json", description = "Render JSON report")
    static class JsonCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--project", required = true)
        Path project;
        @CommandLine.Option(names = "--with-deps", defaultValue = "true", description = "Scan project dependencies (default: true)")
        boolean deps;
        @CommandLine.Option(names = "--pretty")
        boolean pretty;

        @Override
        public Integer call() {
            ImportAnalyzer analyzer = build(project, deps, null, null, false, true);
            List<ImportIssue> issues = analyzer.analyze();
            System.out.println(new JsonReportGenerator(pretty).toJson(issues));
            return 0;
        }
    }

    @CommandLine.Command(name = "summary", description = "Print summary counts")
    static class SummaryCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--project", required = true)
        Path project;

        @Override
        public Integer call() {
            ImportAnalyzer analyzer = build(project, false, null, null, false, true);
            List<ImportIssue> issues = analyzer.analyze();
            long count = issues.size();
            System.out.printf("Total issues: %d\n", count);
            return 0;
        }
    }

    @CommandLine.Command(name = "debug", description = "Print debug info")
    static class DebugCommand implements Callable<Integer> {
        @CommandLine.Option(names = "--project", required = true)
        Path project;
        @CommandLine.Option(names = "--threads")
        Integer threads;

        @Override
        public Integer call() {
            ImportAnalyzer analyzer = build(project, true, threads, null, false, true);
            List<ImportIssue> issues = analyzer.analyze();
            System.out.printf("Analyzed project %s with %d issues detected.%n", project, issues.size());
            return 0;
        }
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new AnalyzerCli()).execute(args);
        System.exit(exit);
    }
}
