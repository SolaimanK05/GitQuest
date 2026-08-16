package com.gitquest.core.codegraph;

import java.util.Set;

/**
 * One file-to-file dependency: {@code fromPath} references at least one
 * type declared in {@code toPath}. {@code referencedTypeNames} is every
 * class/interface/enum/record name that produced this edge — several
 * references between the same two files collapse into one edge, so the
 * graph reads as "these files are connected" rather than one line per
 * usage.
 */
public record DependencyEdge(String fromPath, String toPath, Set<String> referencedTypeNames) {
}
