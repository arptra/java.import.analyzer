# Java Import Analyzer

A multi-module Gradle project that scans very large Java codebases for import problems using JavaParser and Picocli.

## Modules
- **core** – parallel import analysis engine, class index, caching.
- **report** – console and JSON reporting helpers.
- **cli** – Picocli-based command line for running the analyzer.
- **example** – broken demo project to showcase findings.

## Architecture
```
+-------------------------+
|        CLI (picocli)    |
+------------+------------+
             |
+------------v------------+
|       Report module     |
+------------+------------+
             |
+------------v------------+
|   Core analyzer engine  |
|  - ClassIndex           |
|  - ImportGraph          |
|  - JavaParser parsing   |
|  - Cache persistence    |
+------------+------------+
             |
      Example project
```

## Requirements
- Java 17+
- Gradle with Kotlin DSL
- The Gradle wrapper JAR is downloaded on-demand; ensure `curl` or `wget` is available on first run (the script derives the version from `gradle/wrapper/gradle-wrapper.properties`).

## Quickstart (CLI)
```
./gradlew :cli:run --args="analyze --project example"
```

To emit JSON:
```
./gradlew :cli:run --args="json --project example --pretty"
```

## Library usage
```java
import com.example.importanalyzer.core.*;
import java.nio.file.Path;

ImportAnalyzer analyzer = new ImportAnalyzerBuilder()
    .sourceRoot(Path.of("src/main/java"))
    .testSourceRoot(Path.of("src/test/java"))
    .includeDependencies(true)
    .threads(Runtime.getRuntime().availableProcessors())
    .indexCachePath(Path.of(".import-cache.json"))
    .reuseIndex(true)
    .build();

analyzer.analyze();
```

## Performance
- Parallel file discovery and parsing using fixed thread pools.
- Parallel JAR scanning for dependency class indexes.
- ConcurrentHashMap-based ClassIndex optimized for read-heavy workloads.
- Minimal AST traversal to extract imports and type usages.

## Caching and Graph Model
- IndexCache serializes the class index and graph to disk for reuse.
- ImportGraph maps file-to-type usages for quick dependency lookups.
- Reuse cache with `--reuse-index`; disable with `--no-cache`.
