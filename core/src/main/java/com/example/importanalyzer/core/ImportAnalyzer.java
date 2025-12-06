package com.example.importanalyzer.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ImportAnalyzer {
    private final ImportAnalyzerConfig config;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public ImportAnalyzer(ImportAnalyzerConfig config) {
        this.config = config;
    }

    public List<ImportIssue> analyze() {
        ClassIndex index = new ClassIndex();
        ImportGraph graph = new ImportGraph();
        Map<Path, Long> timestamps = new HashMap<>();

        IndexCache cache = new IndexCache(config.indexCachePath(), mapper);
        if (config.cacheEnabled() && config.reuseIndex() && Files.exists(config.indexCachePath())) {
            IndexCache.SerializedIndex serialized = cache.load();
            if (serialized != null) {
                serialized.entries().forEach(index::addEntry);
                serialized.graph().forEach((file, types) -> types.forEach(t -> graph.recordUsage(Path.of(file), t)));
                serialized.fileTimestamps().forEach((file, ts) -> timestamps.put(Path.of(file), ts));
            }
        }

        Set<Path> files = collectJavaFiles(config.sourceRoots());
        files.addAll(collectJavaFiles(config.testSourceRoots()));

        var executor = Executors.newFixedThreadPool(config.threads());
        try {
            List<Callable<SourceFileResult>> tasks = files.stream().map(path -> (Callable<SourceFileResult>) () -> SourceFileAnalyzer.analyze(path)).toList();
            List<Future<SourceFileResult>> futures = executor.invokeAll(tasks);
            for (Future<SourceFileResult> future : futures) {
                try {
                    SourceFileResult result = future.get();
                    timestamps.put(result.file(), Files.getLastModifiedTime(result.file()).toMillis());
                    registerDeclarations(index, result, config.sourceRoots(), config.testSourceRoots());
                    result.usedTypes().forEach(type -> graph.recordUsage(result.file(), type));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to analyze source", e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }

        if (config.includeDependencies()) {
            scanDependencies(index);
        }

        seedJdk(index);

        List<ImportIssue> issues = new ArrayList<>();
        for (Path file : files) {
            try {
                SourceFileResult result = SourceFileAnalyzer.analyze(file);
                issues.addAll(evaluateFile(result, index));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (config.cacheEnabled()) {
            Map<String, Set<String>> graphSnapshot = new HashMap<>();
            graph.viewFileToTypes().forEach((path, types) -> graphSnapshot.put(path.toString(), new HashSet<>(types)));
            Map<String, Long> tsSnapshot = new HashMap<>();
            timestamps.forEach((path, ts) -> tsSnapshot.put(path.toString(), ts));
            cache.save(new IndexCache.SerializedIndex(new ArrayList<>(index.asFqnMap().values()), graphSnapshot, tsSnapshot));
        }

        return issues;
    }

    private void registerDeclarations(ClassIndex index, SourceFileResult result, List<Path> mainRoots, List<Path> testRoots) {
        ClassOrigin origin = originForFile(result.file(), mainRoots, testRoots);
        for (String simple : result.declaredTypes()) {
            String fqn = result.packageName().isEmpty() ? simple : result.packageName() + "." + simple;
            index.addEntry(new ClassIndexEntry(fqn, simple, origin, result.file()));
        }
    }

    private ClassOrigin originForFile(Path file, List<Path> mainRoots, List<Path> testRoots) {
        for (Path root : testRoots) {
            if (file.startsWith(root)) {
                return ClassOrigin.PROJECT_TEST;
            }
        }
        return ClassOrigin.PROJECT_MAIN;
    }

    private Set<Path> collectJavaFiles(List<Path> roots) {
        Set<Path> files = new HashSet<>();
        for (Path root : roots) {
            if (!Files.exists(root)) continue;
            try {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.toString().endsWith(".java")) {
                            files.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return files;
    }

    private List<ImportIssue> evaluateFile(SourceFileResult result, ClassIndex index) {
        List<ImportIssue> issues = new ArrayList<>();

        Map<String, Integer> usedCounts = new HashMap<>();
        result.usedTypes().forEach(t -> usedCounts.merge(t, 1, Integer::sum));

        // wildcard issues
        result.wildcardImports().forEach((pkg, line) -> {
            List<ClassIndexEntry> candidates = index.byPackage(pkg);
            boolean used = candidates.stream().anyMatch(entry -> result.usedTypes().contains(entry.simpleName()));
            if (candidates.isEmpty()) {
                issues.add(new WildcardIssue(result.file(), line, pkg + ".*", "Remove wildcard import; package not found"));
            } else if (!used) {
                issues.add(new WildcardIssue(result.file(), line, pkg + ".*", "Remove unused wildcard import"));
            }
        });

        result.staticWildcardImports().forEach((pkg, line) -> {
            List<ClassIndexEntry> candidates = index.byPackage(pkg);
            if (candidates.isEmpty()) {
                issues.add(new WildcardIssue(result.file(), line, pkg + ".*", "Remove wildcard import; package not found"));
            }
        });

        // import checks
        result.imports().forEach((fqn, line) -> {
            ClassIndexEntry entry = index.getByFqn(fqn);
            String simple = simpleName(fqn);
            if (entry == null) {
                List<ClassIndexEntry> alternatives = index.bySimpleName(simple);
                if (!alternatives.isEmpty()) {
                    issues.add(new WrongPackageIssue(result.file(), line, fqn, "Replace with: " + formatCandidates(alternatives)));
                } else {
                    issues.add(new UnresolvedImportIssue(result.file(), line, fqn, "Remove unresolved import or add the missing dependency"));
                }
            } else if (!result.usedTypes().contains(simple)) {
                issues.add(new UnusedImportIssue(result.file(), line, simple, "Remove unused import"));
            }
        });

        result.staticImports().forEach((fqn, line) -> {
            String owningType = fqn.contains(".") ? fqn.substring(0, fqn.lastIndexOf('.')) : fqn;
            ClassIndexEntry entry = index.getByFqn(owningType);
            String simple = simpleName(fqn);
            if (entry == null) {
                issues.add(new UnresolvedImportIssue(result.file(), line, fqn, "Remove unresolved import or add the missing dependency"));
            } else if (!result.usedIdentifiers().contains(simple)) {
                issues.add(new UnusedImportIssue(result.file(), line, simple, "Remove unused import"));
            }
        });

        for (String used : result.usedTypes()) {
            if (result.declaredTypes().contains(used)) {
                continue;
            }
            boolean imported = result.imports().keySet().stream().anyMatch(fqn -> simpleName(fqn).equals(used));
            boolean samePackage = index.byPackage(result.packageName()).stream().anyMatch(entry -> entry.simpleName().equals(used));
            if (isJavaLang(used) || samePackage || imported) {
                continue;
            }
            List<ClassIndexEntry> candidates = index.bySimpleName(used);
            if (candidates.isEmpty()) {
                issues.add(new MissingImportIssue(result.file(), 1, used, "Add missing import or dependency for type " + used));
            } else if (candidates.size() == 1) {
                issues.add(new MissingImportIssue(result.file(), 1, used, "Add import for " + candidates.get(0).fullyQualifiedName()));
            } else {
                issues.add(new AmbiguousImportIssue(result.file(), 1, used, "Choose one import: " + formatCandidates(candidates)));
            }
        }
        return issues;
    }

    private boolean isJavaLang(String used) {
        return List.of("String", "Object", "System", "Exception", "RuntimeException", "Iterable").contains(used);
    }

    private String formatCandidates(List<ClassIndexEntry> candidates) {
        List<ClassIndexEntry> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator
                .comparingInt((ClassIndexEntry e) -> switch (e.origin()) {
                    case PROJECT_MAIN -> 0;
                    case PROJECT_TEST -> 1;
                    case DEPENDENCY_JAR -> 2;
                    case JDK -> 3;
                })
                .thenComparing(ClassIndexEntry::fullyQualifiedName));
        int limit = 5;
        String joined = sorted.stream()
                .limit(limit)
                .map(ClassIndexEntry::fullyQualifiedName)
                .collect(Collectors.joining(", "));
        if (sorted.size() > limit) {
            joined = joined + " … +" + (sorted.size() - limit) + " more";
        }
        return joined;
    }

    private void scanDependencies(ClassIndex index) {
        DependencyResolver resolver = new DependencyResolver();
        Set<Path> artifacts = resolver.findDependencyArtifacts(config.projectRoot());
        var executor = Executors.newFixedThreadPool(Math.max(1, config.threads() / 2));
        List<Callable<Void>> tasks = artifacts.stream().map(path -> (Callable<Void>) () -> {
            if (Files.isDirectory(path)) {
                scanClassDirectory(index, path);
            } else {
                scanJar(index, path);
            }
            return null;
        }).toList();
        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
        }
    }

    private void scanJar(ClassIndex index, Path jar) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            jarFile.stream()
                    .filter(e -> e.getName().endsWith(".class") && !e.isDirectory())
                    .forEach(entry -> addJarEntry(index, entry, jar));
        } catch (IOException ignored) {
        }
    }

    private void scanClassDirectory(ClassIndex index, Path directory) {
        try {
            Files.walk(directory)
                    .filter(path -> path.toString().endsWith(".class"))
                    .forEach(path -> {
                        String relative = directory.relativize(path).toString();
                        if (relative.contains("$")) {
                            relative = relative.substring(0, relative.indexOf('$')) + ".class";
                        }
                        if (relative.endsWith(".class")) {
                            String fqn = relative.substring(0, relative.length() - 6)
                                    .replace('/', '.')
                                    .replace('\\', '.');
                            if (!fqn.contains("module-info")) {
                                index.addEntry(new ClassIndexEntry(fqn, simpleName(fqn), ClassOrigin.DEPENDENCY_JAR, directory));
                            }
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private void addJarEntry(ClassIndex index, JarEntry entry, Path jar) {
        String name = entry.getName().replace('/', '.').replace(".class", "");
        if (name.contains("$")) {
            name = name.substring(0, name.indexOf('$'));
        }
        String simple = simpleName(name);
        index.addEntry(new ClassIndexEntry(name, simple, ClassOrigin.DEPENDENCY_JAR, jar));
    }

    private String simpleName(String fqn) {
        int idx = fqn.lastIndexOf('.') ;
        return idx >=0 ? fqn.substring(idx+1) : fqn;
    }

    private void seedJdk(ClassIndex index) {
        List<String> jdk = List.of(
                "java.lang.String",
                "java.lang.Object",
                "java.lang.System",
                "java.lang.Exception",
                "java.util.List",
                "java.util.Map",
                "java.util.Set",
                "java.nio.file.Path"
        );
        jdk.forEach(fqn -> index.addEntry(new ClassIndexEntry(fqn, simpleName(fqn), ClassOrigin.JDK, Path.of("<jdk>"))));
    }
}
