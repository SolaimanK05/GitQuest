package com.gitquest.core.codegraph;

import java.util.List;
import java.util.Set;

/**
 * The Java-scoped file dependency graph (CLAUDE.md 4.3 Tier 1): every
 * {@code .java} file found is a node, whether or not it has any edges —
 * an unconnected file is still meaningful information ("nothing else in
 * this codebase uses this"), not noise to drop.
 */
public record JavaDependencyGraph(Set<String> filePaths, List<DependencyEdge> edges, Set<String> filesWithParseErrors) {
}
