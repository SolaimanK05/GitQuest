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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
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
 * <p>On top of that, a lightweight (Tier 1.5, not full Tier 2 symbol
 * resolution) pass tracks method calls: it maps each file's own field/
 * local-variable/parameter names to their declared type via the AST alone
 * (no classpath), then for every method call whose scope is one of those
 * symbols — or a bare class name, for static calls — resolves the target
 * file the same way and records the method name. Chained calls, calls on
 * {@code this}/inherited members, and anything needing real type inference
 * are accepted gaps here, same spirit as the type-reference pass.
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

        Map<String, Map<String, EdgeAccumulator>> edgesByFile = new LinkedHashMap<>();
        for (Map.Entry<Path, CompilationUnit> entry : parsedByFile.entrySet()) {
            String fromPath = relativize(root, entry.getKey());
            CompilationUnit cu = entry.getValue();

            for (String typeName : referencedTypeNames(cu)) {
                String toPath = resolveUnambiguous(declaredBy, typeName);
                if (toPath == null || toPath.equals(fromPath)) {
                    continue;
                }
                accumulator(edgesByFile, fromPath, toPath).types.add(typeName);
            }

            Map<String, String> localTypeBySymbol = localSymbolTypes(cu);
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                Optional<Expression> scope = call.getScope();
                if (scope.isEmpty() || !scope.get().isNameExpr()) {
                    continue; // no scope (this/inherited) or a shape we don't heuristically resolve (chains, this.x, ...)
                }
                String scopeName = scope.get().asNameExpr().getNameAsString();
                String declaredType = localTypeBySymbol.getOrDefault(scopeName, scopeName); // falls back to static-call-on-class-name
                String toPath = resolveUnambiguous(declaredBy, declaredType);
                if (toPath == null || toPath.equals(fromPath)) {
                    continue;
                }
                accumulator(edgesByFile, fromPath, toPath).methods.add(call.getNameAsString() + "()");
            }
        }

        List<DependencyEdge> edges = new ArrayList<>();
        for (Map.Entry<String, Map<String, EdgeAccumulator>> fromEntry : edgesByFile.entrySet()) {
            for (Map.Entry<String, EdgeAccumulator> toEntry : fromEntry.getValue().entrySet()) {
                EdgeAccumulator acc = toEntry.getValue();
                edges.add(new DependencyEdge(fromEntry.getKey(), toEntry.getKey(),
                        Set.copyOf(acc.types), Set.copyOf(acc.methods)));
            }
        }

        return new JavaDependencyGraph(filePaths, edges, parseErrors);
    }

    /** Mutable per-(from,to) accumulator while scanning — converted to an immutable {@link DependencyEdge} at the end. */
    private static final class EdgeAccumulator {
        final Set<String> types = new LinkedHashSet<>();
        final Set<String> methods = new LinkedHashSet<>();
    }

    private static EdgeAccumulator accumulator(Map<String, Map<String, EdgeAccumulator>> edgesByFile, String from, String to) {
        return edgesByFile.computeIfAbsent(from, unused -> new LinkedHashMap<>())
                .computeIfAbsent(to, unused -> new EdgeAccumulator());
    }

    /** Null if the name isn't declared in any scanned file (JDK/library type) or is declared in more than one (ambiguous). */
    private static String resolveUnambiguous(Map<String, List<String>> declaredBy, String simpleName) {
        List<String> declaringFiles = declaredBy.get(simpleName);
        return (declaringFiles != null && declaringFiles.size() == 1) ? declaringFiles.get(0) : null;
    }

    private static Set<String> referencedTypeNames(CompilationUnit cu) {
        Set<String> names = new LinkedHashSet<>();
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            names.add(type.getNameAsString());
        }
        cu.getImports().forEach(imp -> names.add(imp.getName().getIdentifier()));
        return names;
    }

    /**
     * Every field/local-variable/parameter name in the file mapped to its declared type's simple
     * name, purely from the AST (generics/arrays/qualifiers stripped down to the bare type name).
     * One flat map for the whole file, so a name reused across different scopes just has the last
     * declaration win — an accepted imprecision for a heuristic that intentionally avoids real
     * scope/type resolution.
     */
    private static Map<String, String> localSymbolTypes(CompilationUnit cu) {
        Map<String, String> symbolTypes = new HashMap<>();
        for (VariableDeclarator declarator : cu.findAll(VariableDeclarator.class)) {
            symbolTypes.put(declarator.getNameAsString(), simpleTypeName(declarator.getTypeAsString()));
        }
        for (Parameter parameter : cu.findAll(Parameter.class)) {
            symbolTypes.put(parameter.getNameAsString(), simpleTypeName(parameter.getTypeAsString()));
        }
        return symbolTypes;
    }

    private static String simpleTypeName(String rawType) {
        String type = rawType;
        int genericStart = type.indexOf('<');
        if (genericStart >= 0) {
            type = type.substring(0, genericStart);
        }
        type = type.replace("[]", "").trim();
        int lastDot = type.lastIndexOf('.');
        return lastDot >= 0 ? type.substring(lastDot + 1) : type;
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
