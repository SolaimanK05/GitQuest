package com.gitquest.core.codegraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

/**
 * Builds a {@link JavaDependencyGraph} by parsing every {@code .java} file
 * under a root with JavaParser — no classpath/symbol resolution configured,
 * per CLAUDE.md 4.3 Tier 1. Two passes: first index each file's declared
 * class/interface/enum/record names, then scan every file's type
 * references (imports, extends/implements, field/param/return types,
 * {@code new X()}, generic args — JavaParser represents essentially all of
 * these as {@link ClassOrInterfaceType} nodes, so one blanket scan covers
 * them) and draw an edge whenever a reference resolves — unambiguously —
 * to a name declared in a different file.
 *
 * <p>Overloaded simple names across files (ambiguous — skipped rather than
 * guessed), and anything needing real symbol resolution (polymorphism,
 * reflection, external library internals), are known, accepted gaps: this
 * is a map to navigate a codebase by, not a compiler. Blocking; callers
 * must run this off the JavaFX Application Thread.
 */
public final class JavaDependencyAnalyzer {

    private static final ParserConfiguration CONFIG =
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

    private JavaDependencyAnalyzer() {
    }

    public static JavaDependencyGraph analyze(Path root) throws IOException {
        List<Path> javaFiles = findJavaFiles(root);
        JavaParser parser = new JavaParser(CONFIG);

        Map<Path, CompilationUnit> parsedByFile = new LinkedHashMap<>();
        Set<String> filePaths = new LinkedHashSet<>();
        Set<String> parseErrors = new LinkedHashSet<>();
        // simple type name -> every file that declares a type with that name (ambiguous if more than one)
        Map<String, List<String>> declaredBy = new HashMap<>();

        for (Path file : javaFiles) {
            String relative = relativize(root, file);
            filePaths.add(relative);
            try {
                ParseResult<CompilationUnit> result = parser.parse(file);
                // JavaParser does lenient/partial parsing: even a malformed file can come back
                // with a non-empty best-effort AST, so isSuccessful() (not just getResult()'s
                // presence) is what actually marks a real parse failure.
                if (!result.isSuccessful() || result.getResult().isEmpty()) {
                    parseErrors.add(relative);
                    continue;
                }
                CompilationUnit cu = result.getResult().get();
                parsedByFile.put(file, cu);
                for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
                    declaredBy.computeIfAbsent(type.getNameAsString(), unused -> new ArrayList<>()).add(relative);
                }
            } catch (Exception e) {
                parseErrors.add(relative);
            }
        }

        Map<String, Map<String, Set<String>>> edgesByFile = new LinkedHashMap<>();
        for (Map.Entry<Path, CompilationUnit> entry : parsedByFile.entrySet()) {
            String fromPath = relativize(root, entry.getKey());
            for (String typeName : referencedTypeNames(entry.getValue())) {
                List<String> declaringFiles = declaredBy.get(typeName);
                if (declaringFiles == null || declaringFiles.size() != 1) {
                    continue; // not declared anywhere we scanned (JDK/library type), or ambiguous
                }
                String toPath = declaringFiles.get(0);
                if (toPath.equals(fromPath)) {
                    continue; // self-reference isn't a dependency edge
                }
                edgesByFile.computeIfAbsent(fromPath, unused -> new LinkedHashMap<>())
                        .computeIfAbsent(toPath, unused -> new LinkedHashSet<>())
                        .add(typeName);
            }
        }

        List<DependencyEdge> edges = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String>>> fromEntry : edgesByFile.entrySet()) {
            for (Map.Entry<String, Set<String>> toEntry : fromEntry.getValue().entrySet()) {
                edges.add(new DependencyEdge(fromEntry.getKey(), toEntry.getKey(), Set.copyOf(toEntry.getValue())));
            }
        }

        return new JavaDependencyGraph(filePaths, edges, parseErrors);
    }

    private static Set<String> referencedTypeNames(CompilationUnit cu) {
        Set<String> names = new LinkedHashSet<>();
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            names.add(type.getNameAsString());
        }
        cu.getImports().forEach(imp -> names.add(imp.getName().getIdentifier()));
        return names;
    }

    private static List<Path> findJavaFiles(Path root) throws IOException {
        Path gitDir = root.resolve(".git");
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(p -> !p.startsWith(gitDir))
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static String relativize(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
