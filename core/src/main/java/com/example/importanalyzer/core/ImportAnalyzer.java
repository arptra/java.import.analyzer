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
                issues.add(new WildcardIssue(result.file(), line, pkg + ".*", "Package not found"));
            } else if (!used) {
                issues.add(new WildcardIssue(result.file(), line, pkg + ".*", "Wildcard import unused"));
            }
        });

        // import checks
        result.imports().forEach((fqn, line) -> {
            ClassIndexEntry entry = index.getByFqn(fqn);
            String simple = simpleName(fqn);
            if (entry == null) {
                List<ClassIndexEntry> alternatives = index.bySimpleName(simple);
                if (!alternatives.isEmpty()) {
                    String hint = alternatives.stream().limit(3).map(ClassIndexEntry::fullyQualifiedName).collect(Collectors.joining(", "));
                    issues.add(new WrongPackageIssue(result.file(), line, fqn, "Did you mean: " + hint));
                } else {
                    issues.add(new UnresolvedImportIssue(result.file(), line, fqn, "Import cannot be resolved"));
                }
            } else if (!result.usedTypes().contains(simple)) {
                issues.add(new UnusedImportIssue(result.file(), line, simple, "Import not used"));
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
                issues.add(new MissingImportIssue(result.file(), 1, used, "No matching type found"));
            } else if (candidates.size() == 1) {
                issues.add(new MissingImportIssue(result.file(), 1, used, "Import required for " + candidates.get(0).fullyQualifiedName()));
            } else {
                issues.add(new AmbiguousImportIssue(result.file(), 1, used, "Multiple matches: " + candidates.stream().map(ClassIndexEntry::fullyQualifiedName).collect(Collectors.joining(", "))));
            }
        }
        return issues;
    }

    private boolean isJavaLang(String used) {
        return List.of("String", "Object", "System", "Exception", "RuntimeException", "Iterable").contains(used);
    }

    private void scanDependencies(ClassIndex index) {
        DependencyResolver resolver = new DependencyResolver();
        Path projectRoot = config.sourceRoots().isEmpty() ? Path.of(".") : config.sourceRoots().get(0).getParent().getParent();
        Set<Path> jars = resolver.findDependencyJars(projectRoot);
        var executor = Executors.newFixedThreadPool(Math.max(1, config.threads() / 2));
        List<Callable<Void>> tasks = jars.stream().map(jar -> (Callable<Void>) () -> {
            scanJar(index, jar);
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
                "java.util.Map"
        );
        jdk.forEach(fqn -> index.addEntry(new ClassIndexEntry(fqn, simpleName(fqn), ClassOrigin.JDK, Path.of("<jdk>"))));
    }
}
