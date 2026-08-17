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
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

/**
 * Builds a {@link JavaDependencyGraph} by parsing every {@code .java} file's
 * source with JavaParser — no classpath/symbol resolution configured, per
 * CLAUDE.md 4.3 Tier 1. Two passes: first index each file's declared
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
 * <p>The actual analysis ({@link #analyzeSources}) works from an in-memory
 * {@code path -> source text} map rather than reading disk directly, so it
 * can be fed either the live working directory ({@link #analyze(Path)}) or
 * a historical commit's blobs (read via {@code HistoricalJavaSourceReader},
 * no checkout) for the Code Graph's time-travel scrubber.
 *
 * <p>{@link #analyzeWithSymbolResolution(Path)} is Tier 2 (CLAUDE.md 4.3 stretch): real method-call
 * resolution via {@link JavaSymbolSolver} instead of the scope-name heuristic above — see its own
 * javadoc for what that buys and what it costs.
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

    /** Reads every {@code .java} file under {@code root} from disk, then delegates to {@link #analyzeSources}. */
    public static JavaDependencyGraph analyze(Path root) throws IOException {
        List<Path> javaFiles = findJavaFiles(root);
        Map<String, String> sourceByPath = new LinkedHashMap<>();
        Set<String> unreadable = new LinkedHashSet<>();
        for (Path file : javaFiles) {
            String relative = relativize(root, file);
            try {
                sourceByPath.put(relative, Files.readString(file));
            } catch (IOException e) {
                unreadable.add(relative); // e.g. non-UTF-8 content — treat as a parse error, not a hard failure
            }
        }
        JavaDependencyGraph graph = analyzeSources(sourceByPath);
        if (unreadable.isEmpty()) {
            return graph;
        }
        Set<String> filePaths = new LinkedHashSet<>(graph.filePaths());
        filePaths.addAll(unreadable);
        Set<String> parseErrors = new LinkedHashSet<>(graph.filesWithParseErrors());
        parseErrors.addAll(unreadable);
        return new JavaDependencyGraph(filePaths, graph.edges(), parseErrors);
    }

    /** The actual analysis, independent of where the source text came from. See the class doc. */
    public static JavaDependencyGraph analyzeSources(Map<String, String> sourceByPath) {
        ParsedFiles parsed = parseAll(sourceByPath, new JavaParser(CONFIG));

        Map<String, Map<String, EdgeAccumulator>> edgesByFile = new LinkedHashMap<>();
        for (Map.Entry<String, CompilationUnit> entry : parsed.parsedByFile().entrySet()) {
            String fromPath = entry.getKey();
            CompilationUnit cu = entry.getValue();
            accumulateTypeEdges(fromPath, cu, parsed.declaredBy(), edgesByFile);

            Map<String, String> localTypeBySymbol = localSymbolTypes(cu);
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                Optional<Expression> scope = call.getScope();
                if (scope.isEmpty() || !scope.get().isNameExpr()) {
                    continue; // no scope (this/inherited) or a shape we don't heuristically resolve (chains, this.x, ...)
                }
                String scopeName = scope.get().asNameExpr().getNameAsString();
                String declaredType = localTypeBySymbol.getOrDefault(scopeName, scopeName); // falls back to static-call-on-class-name
                String toPath = resolveUnambiguous(parsed.declaredBy(), declaredType);
                if (toPath == null || toPath.equals(fromPath)) {
                    continue;
                }
                accumulator(edgesByFile, fromPath, toPath).methods.add(call.getNameAsString() + "()");
            }
        }

        return buildGraph(sourceByPath.keySet(), edgesByFile, parsed.parseErrors());
    }

    /**
     * Tier 2 (CLAUDE.md 4.3 stretch): real method-call resolution via {@link JavaSymbolSolver} with
     * a {@link CombinedTypeSolver} — JDK types via reflection, the project's own types via a
     * {@link JavaParserTypeSolver} pointed at {@code root}. Unlike Tier 1's scope-name heuristic
     * ({@link #analyzeSources}), this correctly follows chained calls ({@code getFoo().bar()}),
     * calls with no explicit scope ({@code this}/inherited members), and real overload resolution —
     * and since a resolved call's declaring type is where the method is actually defined (not just
     * the local variable's declared type), an inherited-method call now correctly points at the
     * superclass/interface file that declares it.
     *
     * <p>The tradeoff: {@code JavaParserTypeSolver} needs real source files on disk to resolve
     * against, so unlike {@link #analyzeSources} this only works against a real directory — there's
     * no way to point it at a historical commit's blobs without checking them out somewhere, so the
     * Code Graph's time-travel scrubber stays on Tier 1 for historical points (see
     * {@code SandboxController}). Type-reference edges are unchanged from Tier 1 here — only
     * method-call edges get real resolution. Still not a compiler: an unresolvable call (external
     * library, reflection, an incomplete classpath) is silently skipped rather than guessed, same
     * accepted-gap spirit as Tier 1. Blocking; callers must run this off the JavaFX Application
     * Thread.
     */
    public static JavaDependencyGraph analyzeWithSymbolResolution(Path root) throws IOException {
        List<Path> javaFiles = findJavaFiles(root);
        Map<String, String> sourceByPath = new LinkedHashMap<>();
        Set<String> unreadable = new LinkedHashSet<>();
        for (Path file : javaFiles) {
            String relative = relativize(root, file);
            try {
                sourceByPath.put(relative, Files.readString(file));
            } catch (IOException e) {
                unreadable.add(relative);
            }
        }

        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(root.toFile()));
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));
        ParsedFiles parsed = parseAll(sourceByPath, new JavaParser(config));

        Map<String, Map<String, EdgeAccumulator>> edgesByFile = new LinkedHashMap<>();
        for (Map.Entry<String, CompilationUnit> entry : parsed.parsedByFile().entrySet()) {
            String fromPath = entry.getKey();
            CompilationUnit cu = entry.getValue();
            accumulateTypeEdges(fromPath, cu, parsed.declaredBy(), edgesByFile);

            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                try {
                    ResolvedMethodDeclaration resolved = call.resolve();
                    String declaringSimpleName = simpleNameOf(resolved.declaringType().getQualifiedName());
                    String toPath = resolveUnambiguous(parsed.declaredBy(), declaringSimpleName);
                    if (toPath == null || toPath.equals(fromPath)) {
                        continue;
                    }
                    accumulator(edgesByFile, fromPath, toPath).methods.add(call.getNameAsString() + "()");
                } catch (Exception e) {
                    // unresolvable -- external library call, missing classpath entry, reflection: accepted gap
                }
            }
        }

        Set<String> filePaths = new LinkedHashSet<>(sourceByPath.keySet());
        Set<String> parseErrors = new LinkedHashSet<>(parsed.parseErrors());
        parseErrors.addAll(unreadable);
        filePaths.addAll(unreadable);
        return buildGraph(filePaths, edgesByFile, parseErrors);
    }

    /** Parses every file, indexing which file declares which simple type name(s) — shared by Tier 1 and Tier 2. */
    private static ParsedFiles parseAll(Map<String, String> sourceByPath, JavaParser parser) {
        Map<String, CompilationUnit> parsedByFile = new LinkedHashMap<>();
        Set<String> parseErrors = new LinkedHashSet<>();
        // simple type name -> every file that declares a type with that name (ambiguous if more than one)
        Map<String, List<String>> declaredBy = new HashMap<>();

        for (Map.Entry<String, String> entry : sourceByPath.entrySet()) {
            String relative = entry.getKey();
            try {
                ParseResult<CompilationUnit> result = parser.parse(entry.getValue());
                // JavaParser does lenient/partial parsing: even a malformed file can come back
                // with a non-empty best-effort AST, so isSuccessful() (not just getResult()'s
                // presence) is what actually marks a real parse failure.
                if (!result.isSuccessful() || result.getResult().isEmpty()) {
                    parseErrors.add(relative);
                    continue;
                }
                CompilationUnit cu = result.getResult().get();
                parsedByFile.put(relative, cu);
                for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
                    declaredBy.computeIfAbsent(type.getNameAsString(), unused -> new ArrayList<>()).add(relative);
                }
            } catch (Exception e) {
                parseErrors.add(relative);
            }
        }
        return new ParsedFiles(parsedByFile, declaredBy, parseErrors);
    }

    private record ParsedFiles(Map<String, CompilationUnit> parsedByFile, Map<String, List<String>> declaredBy,
            Set<String> parseErrors) {
    }

    /** Type-reference edges (imports/extends/fields/params/returns/{@code new X()}) — identical logic for Tier 1 and Tier 2. */
    private static void accumulateTypeEdges(String fromPath, CompilationUnit cu, Map<String, List<String>> declaredBy,
            Map<String, Map<String, EdgeAccumulator>> edgesByFile) {
        for (String typeName : referencedTypeNames(cu)) {
            String toPath = resolveUnambiguous(declaredBy, typeName);
            if (toPath == null || toPath.equals(fromPath)) {
                continue;
            }
            accumulator(edgesByFile, fromPath, toPath).types.add(typeName);
        }
    }

    private static JavaDependencyGraph buildGraph(Set<String> filePaths, Map<String, Map<String, EdgeAccumulator>> edgesByFile,
            Set<String> parseErrors) {
        List<DependencyEdge> edges = new ArrayList<>();
        for (Map.Entry<String, Map<String, EdgeAccumulator>> fromEntry : edgesByFile.entrySet()) {
            for (Map.Entry<String, EdgeAccumulator> toEntry : fromEntry.getValue().entrySet()) {
                EdgeAccumulator acc = toEntry.getValue();
                edges.add(new DependencyEdge(fromEntry.getKey(), toEntry.getKey(),
                        Set.copyOf(acc.types), Set.copyOf(acc.methods)));
            }
        }
        return new JavaDependencyGraph(Set.copyOf(filePaths), edges, Set.copyOf(parseErrors));
    }

    private static String simpleNameOf(String qualifiedName) {
        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
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
