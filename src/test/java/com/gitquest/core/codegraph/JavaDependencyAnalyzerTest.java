package com.gitquest.core.codegraph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class JavaDependencyAnalyzerTest {

    @Test
    void draws_an_edge_when_one_file_references_a_type_declared_in_another() throws IOException {
        Path root = Files.createTempDirectory("jda-test");
        write(root, "A.java", "class A { B field; }");
        write(root, "B.java", "class B { }");

        JavaDependencyGraph graph = JavaDependencyAnalyzer.analyze(root);

        assertTrue(hasEdge(graph, "A.java", "B.java"), "expected an edge from A.java to B.java");
        assertTrue(edgeBetween(graph, "A.java", "B.java").referencedTypeNames().contains("B"));
    }

    @Test
    void draws_an_edge_for_extends() throws IOException {
        Path root = Files.createTempDirectory("jda-test");
        write(root, "A.java", "class A { }");
        write(root, "D.java", "class D extends A { }");

        JavaDependencyGraph graph = JavaDependencyAnalyzer.analyze(root);

        assertTrue(hasEdge(graph, "D.java", "A.java"));
    }

    @Test
    void selfReferenceIsNotAnEdge() throws IOException {
        Path root = Files.createTempDirectory("jda-test");
        write(root, "Self.java", "class Self { Self next; }");

        JavaDependencyGraph graph = JavaDependencyAnalyzer.analyze(root);

        assertTrue(graph.edges().isEmpty(), "a self-referencing field must not produce a self-edge");
    }

    @Test
    void referencesToJdkTypesAreSilentlyIgnored() throws IOException {
        Path root = Files.createTempDirectory("jda-test");
        write(root, "Only.java", "import java.util.List; class Only { List<String> items; }");

        JavaDependencyGraph graph = JavaDependencyAnalyzer.analyze(root);

        assertTrue(graph.edges().isEmpty(), "java.util.List isn't declared in any scanned file, so it must not produce an edge");
        assertTrue(graph.filePaths().contains("Only.java"));
    }

    @Test
    void ambiguousSimpleNameAcrossFilesProducesNoEdge() throws IOException {
        Path root = Files.createTempDirectory("jda-test");
        write(root, "Dup1.java", "class Dup { }");
        write(root, "Dup2.java", "class Dup { }");
        write(root, "User.java", "class User { Dup d; }");

        JavaDependencyGraph graph = JavaDependencyAnalyzer.analyze(root);

        assertFalse(hasEdge(graph, "User.java", "Dup1.java"));
        assertFalse(hasEdge(graph, "User.java", "Dup2.java"));
    }

    @Test
    void unparseableFileIsRecordedNotThrown() throws IOException {
        Path root = Files.createTempDirectory("jda-test");
        write(root, "Broken.java", "class Broken { this is not valid java!!! ");
        write(root, "Fine.java", "class Fine { }");

        JavaDependencyGraph graph = JavaDependencyAnalyzer.analyze(root);

        assertTrue(graph.filesWithParseErrors().contains("Broken.java"));
        assertTrue(graph.filePaths().contains("Fine.java"));
    }

    private static void write(Path root, String name, String content) throws IOException {
        Files.writeString(root.resolve(name), content);
    }

    private static boolean hasEdge(JavaDependencyGraph graph, String from, String to) {
        return graph.edges().stream().anyMatch(e -> e.fromPath().equals(from) && e.toPath().equals(to));
    }

    private static DependencyEdge edgeBetween(JavaDependencyGraph graph, String from, String to) {
        Optional<DependencyEdge> edge = graph.edges().stream()
                .filter(e -> e.fromPath().equals(from) && e.toPath().equals(to))
                .findFirst();
        assertTrue(edge.isPresent());
        return edge.get();
    }
}
