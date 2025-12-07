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
The CLI will resolve a relative `--project` path against both the current working directory and the repository root, so the above
command works whether you run it from the repo root or inside a module directory.

To emit JSON:
```
./gradlew :cli:run --args="json --project example --pretty"
```

## Library usage
```java
import com.example.importanalyzer.core.*;
import java.nio.file.Path;

ImportAnalyzer analyzer = new ImportAnalyzerBuilder()
    .projectRoot(Path.of("."))
    .sourceRoot(Path.of("src/main/java"))
    .testSourceRoot(Path.of("src/test/java"))
    .includeDependencies(true)
    .threads(Runtime.getRuntime().availableProcessors())
    .indexCachePath(Path.of(".import-cache.json"))
    .reuseIndex(true)
    .build();

analyzer.analyze();
```

### Embedding as a background service
```java
ImportAnalyzerConfig config = new ImportAnalyzerBuilder()
    .projectRoot(Path.of("/path/to/project"))
    .sourceRoot(Path.of("/path/to/project/src/main/java"))
    .testSourceRoot(Path.of("/path/to/project/src/test/java"))
    .includeDependencies(true)
    .buildConfig();

ImportAnalyzerService service = new AsyncImportAnalyzerService(config);
service.startScan();

ScanResult result = service.scan(Path.of("/path/to/project/src/main/java/com/example/App.java")).join();
```

`ScanResult` reports progress while a background scan is running and, once complete, returns the action the user should take:

- `ADD` – add an import (only one candidate exists)
- `DELETE` – remove an import that cannot be resolved
- `SELECT` – choose between the provided candidate types
- `UNKNOWN` – no actionable import change

The result also includes the target file, the relevant line number (when deletion is needed), and candidate fully qualified class names.

## Работа с API для кодовых агентов (например, Codex)
Эти подсказки помогают интегрировать сканер в автоматические агенты, которые применяют правки в коде.

### ScanResult: структура ответа
- **file** — `Path` к файлу, для которого запрошен анализ.
- **action** — `ImportAction` с инструкцией для агента:
  - `ADD` — добавить импорт; существует ровно один кандидат.
  - `DELETE` — удалить импорт, потому что тип недоступен.
  - `SELECT` — выбрать один из нескольких кандидатов.
  - `UNKNOWN` — агент не должен менять импорты.
- **line** — номер строки, когда требуется удаление (`DELETE`) или точка привязки действия.
- **candidates** — `List<String>` с FQN, которые можно добавить или выбрать.
- **source** — `ImportSource`, указывается только при `ADD`:
  - `LOCAL` — кандидат находится в текущем проекте (например, класс из `src/main/java`).
  - `LIBRARY` — кандидат пришёл из внешних зависимостей/JAR.
- **progressTotal / progressCompleted** — количество файлов всего и сколько уже обработано; если сканирование ещё идёт, агент может показывать пользователю прогресс и повторить запрос позже.

### Поток работы агента
1. Вызвать `service.startScan()` для запуска фоновой индексации.
2. Периодически вызывать `service.scan(targetPath)`:
   - Если индексация не завершена, получить прогресс (поля `progressCompleted`/`progressTotal`).
   - После завершения получить итоговое действие и, если `action == ADD`, использовать `source` для решения: предпочитайте `LOCAL`, если он доступен.
3. Применить правку в файле согласно `action` и выбранному кандидату из `candidates` (при `SELECT` или `ADD`).

### Publishing locally
Publish to your Maven Local repository:
```
./gradlew publishToMavenLocal
```

Then depend on the core module from another Gradle project:
```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.example.importanalyzer:core:${version}")
}
```

Replace `${version}` with the version defined in this repository's Gradle build.

### Dependency scope
Анализатор ограничивает индекс зависимостями, полученными из Gradle Tooling API (runtime/compile classpath) и локальными сборочными каталогами (`build/libs`, `build/classes`). Полный обход `~/.m2` или `~/.gradle/caches` не выполняется, поэтому подсказки по импортам основаны только на реально задекларированных зависимостях проекта и локальных артефактах.

## Performance
- Parallel file discovery and parsing using fixed thread pools.
- Parallel JAR scanning for dependency class indexes.
- ConcurrentHashMap-based ClassIndex optimized for read-heavy workloads.
- Minimal AST traversal to extract imports and type usages.

## Caching and Graph Model
- IndexCache serializes the class index and graph to disk for reuse.
- ImportGraph maps file-to-type usages for quick dependency lookups.
- Reuse cache with `--reuse-index`; disable with `--no-cache`.
