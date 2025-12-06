package com.example.importanalyzer.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ImportAnalyzerBuilder {
    private final List<Path> sourceRoots = new ArrayList<>();
    private final List<Path> testSourceRoots = new ArrayList<>();
    private boolean includeDependencies = false;
    private int threads = Runtime.getRuntime().availableProcessors();
    private Path indexCachePath = Path.of(".import-analyzer-cache.json");
    private boolean reuseIndex = false;
    private boolean cacheEnabled = true;

    public ImportAnalyzerBuilder sourceRoot(Path path) {
        this.sourceRoots.add(path);
        return this;
    }

    public ImportAnalyzerBuilder testSourceRoot(Path path) {
        this.testSourceRoots.add(path);
        return this;
    }

    public ImportAnalyzerBuilder includeDependencies(boolean include) {
        this.includeDependencies = include;
        return this;
    }

    public ImportAnalyzerBuilder threads(int threads) {
        this.threads = threads;
        return this;
    }

    public ImportAnalyzerBuilder indexCachePath(Path path) {
        this.indexCachePath = path;
        return this;
    }

    public ImportAnalyzerBuilder reuseIndex(boolean reuse) {
        this.reuseIndex = reuse;
        return this;
    }

    public ImportAnalyzerBuilder cacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
        return this;
    }

    public ImportAnalyzer build() {
        ImportAnalyzerConfig config = new ImportAnalyzerConfig(sourceRoots, testSourceRoots, includeDependencies, threads, indexCachePath, reuseIndex, cacheEnabled);
        return new ImportAnalyzer(config);
    }
}
