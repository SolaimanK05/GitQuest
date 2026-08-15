package com.gitquest.core.model;

import org.eclipse.jgit.lib.ObjectId;

/** Immutable snapshot of a single branch/tag ref for the graph view. */
public record BranchRef(String name, ObjectId targetId, boolean isHead, boolean isRemote) {
}
