package com.gitquest.core.campaign;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/** Satisfied once a committed (not just present-on-disk) {@code .gitignore} contains {@code excludedEntry}. */
public record GitignoreExcludesGoal(String excludedEntry) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        Path workTree = model.getRepository().getWorkTree().toPath();
        Path gitignore = workTree.resolve(".gitignore");
        if (!Files.isRegularFile(gitignore)) {
            return false;
        }
        try {
            if (!Files.readString(gitignore).contains(excludedEntry)) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        return isCommittedAtHead(model.getRepository(), ".gitignore");
    }

    private static boolean isCommittedAtHead(Repository repository, String relativePath) {
        try {
            ObjectId headId = repository.resolve("HEAD");
            if (headId == null) {
                return false;
            }
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(headId);
                try (TreeWalk treeWalk = TreeWalk.forPath(repository, relativePath, commit.getTree())) {
                    return treeWalk != null;
                }
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String describeObjective() {
        return "Commit a .gitignore that excludes " + excludedEntry + ".";
    }
}
