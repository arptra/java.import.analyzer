import os
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed

# ---------- CONFIG ----------
MAIN_SRC = "src/main/java/com/example/importanalyzer/example/"
TEST_SRC = "src/test/java/com/example/importanalyzer/example/"
MAX_WORKERS = 12
# ----------------------------

def find_package_line(content: list[str]) -> int:
    """Ищет строку package ... ; и возвращает индекс."""
    for i, line in enumerate(content):
        if line.strip().startswith("package "):
            return i
    return -1


def generate_imports(main_dir_path: str, package_path: str) -> list[str]:
    """Создаёт список import ...; для всех классов внутри main_src."""
    imports = []

    for file in os.listdir(main_dir_path):
        if file.endswith(".java"):
            class_name = file.replace(".java", "")
            imports.append(f"import {package_path}.{class_name}.*;\n")

    return imports


def process_test_file(test_file_path: str, project_path: str):
    """Обрабатывает один тестовый файл: генерирует и вставляет импорты."""

    # Полный путь к TEST_SRC
    rel_test_path = os.path.relpath(test_file_path, os.path.join(project_path, TEST_SRC))

    # Пример:
    #   rel_test_path = mtd/dir1/Class1Test.java
    test_dir = os.path.dirname(rel_test_path)

    # Соответствующая папка в main
    main_dir_path = os.path.join(project_path, MAIN_SRC, test_dir)

    if not os.path.isdir(main_dir_path):
        print(f"[WARN] Папка {main_dir_path} не найдена, пропускаю {test_file_path}")
        return

    # Собираем package-path: mtd.dir1
    package_path = test_dir.replace(os.sep, ".")

    # Читаем тестовый файл
    with open(test_file_path, "r", encoding="utf-8") as f:
        content = f.readlines()

    package_line_index = find_package_line(content)
    if package_line_index == -1:
        print(f"[ERROR] Не найден package в {test_file_path}")
        return

    # Генерируем импорты
    imports = generate_imports(main_dir_path, package_path)

    # Вставляем после package
    new_content = (
        content[: package_line_index + 1]
        + ["\n"]  # разделитель
        + imports
        + ["\n"]  # разделитель
        + content[package_line_index + 1 :]
    )

    # Перезаписываем файл
    with open(test_file_path, "w", encoding="utf-8") as f:
        f.writelines(new_content)

    print(f"[OK] Updated imports in {test_file_path}")


def collect_test_files(project_path: str):
    """Собирает все тестовые файлы src/test/java/**/*.java"""
    test_root = os.path.join(project_path, TEST_SRC)
    result = []

    for root, dirs, files in os.walk(test_root):
        for file in files:
            if file.endswith("Test.java"):
                result.append(os.path.join(root, file))

    return result


def main():
    parser = argparse.ArgumentParser(description="Auto-import generator for test classes.")
    parser.add_argument("--project-path", required=True, help="Path to the project root")

    args = parser.parse_args()
    project_path = os.path.abspath(args.project_path)

    test_files = collect_test_files(project_path)

    print(f"Найдено {len(test_files)} тестовых классов\n")

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = [
            executor.submit(process_test_file, test_file, project_path)
            for test_file in test_files
        ]
        for future in as_completed(futures):
            future.result()

    print("\nГотово! Все импорты обновлены.")


if __name__ == "__main__":
    main()
