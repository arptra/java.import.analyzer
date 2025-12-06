package com.example.importanalyzer.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

public class DependencyResolver {
    public Set<Path> findDependencyJars(Path projectRoot) {
        Set<Path> jars = new HashSet<>();
        Path gradleCache = Path.of(System.getProperty("user.home"), ".gradle", "caches");
        Path mavenCache = Path.of(System.getProperty("user.home"), ".m2", "repository");
        scanDirectory(gradleCache, jars);
        scanDirectory(mavenCache, jars);
        Path libs = projectRoot.resolve("libs");
        scanDirectory(libs, jars);
        // Also look for multi-module siblings that have already produced build outputs.
        Path parent = projectRoot.getParent();
        if (parent != null && Files.exists(parent)) {
            try {
                Files.list(parent)
                        .filter(Files::isDirectory)
                        .map(dir -> dir.resolve("build/libs"))
                        .filter(Files::exists)
                        .forEach(path -> scanDirectory(path, jars));
            } catch (IOException ignored) {
            }
        }
        return jars;
    }

    private void scanDirectory(Path dir, Set<Path> jars) {
        if (!Files.exists(dir)) return;
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".jar")) {
                        jars.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
        }
    }
}
