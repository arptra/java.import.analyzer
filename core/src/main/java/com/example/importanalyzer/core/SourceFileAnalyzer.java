package com.example.importanalyzer.core;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SourceFileAnalyzer {
    private SourceFileAnalyzer() {}

    public static SourceFileResult analyze(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(Files.readString(file));

        Map<String, Integer> imports = new HashMap<>();
        Map<String, Integer> wildcardImports = new HashMap<>();
        cu.getImports().forEach(imp -> {
            if (imp.isAsterisk()) {
                wildcardImports.put(imp.getNameAsString(), imp.getBegin().map(p -> p.line).orElse(1));
            } else {
                imports.put(imp.getNameAsString(), imp.getBegin().map(p -> p.line).orElse(1));
            }
        });

        Set<String> declaredTypes = new HashSet<>();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(decl -> declaredTypes.add(decl.getNameAsString()));
        cu.findAll(EnumDeclaration.class).forEach(decl -> declaredTypes.add(decl.getNameAsString()));
        cu.findAll(RecordDeclaration.class).forEach(decl -> declaredTypes.add(decl.getNameAsString()));

        Set<String> used = new HashSet<>();
        cu.accept(new VoidVisitorAdapter<Set<String>>() {
            @Override
            public void visit(ClassOrInterfaceType n, Set<String> collector) {
                super.visit(n, collector);
                collector.add(n.getName().getIdentifier());
            }
        }, used);

        String pkg = cu.getPackageDeclaration().map(pd -> pd.getName().asString()).orElse("");
        return new SourceFileResult(file, pkg, imports, wildcardImports, declaredTypes, used);
    }
}
