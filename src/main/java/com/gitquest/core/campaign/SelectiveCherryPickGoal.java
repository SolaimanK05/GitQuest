package com.gitquest.core.campaign;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/** Satisfied once {@code includedFile} is committed at HEAD but {@code excludedFile} isn't — proof a cherry-pick pulled in one specific commit, not the whole branch. */
public record SelectiveCherryPickGoal(String includedFile, String excludedFile) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        Repository repository = model.getRepository();
        return isCommittedAtHead(repository, includedFile) && !isCommittedAtHead(repository, excludedFile);
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
        return "Cherry-pick just the commit that adds " + includedFile + ", and leave " + excludedFile + " out.";
    }
}
