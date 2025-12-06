package com.example.importanalyzer.core;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public record SourceFileResult(Path file, String packageName, Map<String, Integer> imports, Map<String, Integer> wildcardImports,
                               Set<String> declaredTypes, Set<String> usedTypes) {
}
