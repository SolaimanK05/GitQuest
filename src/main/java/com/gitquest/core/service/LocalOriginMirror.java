package com.gitquest.core.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;

/**
 * Redirects a repository's "origin" remote to a fresh, local, disposable bare repo — the same
 * trick Arc 6's campaign levels and the Collaboration Demo already use (CLAUDE.md 4.3: "no real
 * network required"). Used to turn a real clone/copy of someone's actual project into a true
 * sandbox: push/fetch/pull stay real JGit operations (so the lessons are real — a rejected
 * non-fast-forward push behaves exactly like it would against a real remote), but the destination
 * is always a harmless local stand-in, never wherever the repo was really hosted.
 */
public final class LocalOriginMirror {

    private LocalOriginMirror() {
    }

    /** Blocking (disk/git I/O) — call off the JavaFX Application Thread. Returns the mirror's path, so callers can track it for later disposal. */
    public static Path redirectOriginToLocalMirror(Git git) throws Exception {
        Path bareMirror = Files.createTempDirectory("gitquest-sandbox-mirror-");
        try (Git ignored = Git.init().setDirectory(bareMirror.toFile()).setBare(true).call()) {
            // just creating the local stand-in; nothing else to do with this handle
        }
        String mirrorUri = bareMirror.toUri().toString();

        boolean hasAnyCommit = git.getRepository().resolve(Constants.HEAD) != null;
        if (hasAnyCommit) {
            // Seed the mirror with whatever this repo currently has, so it starts in sync —
            // matching a real clone's "nothing new to fetch yet" starting state.
            git.push().setRemote(mirrorUri).setPushAll().call();
        }

        if (git.getRepository().getConfig().getString("remote", "origin", "url") != null) {
            git.remoteRemove().setRemoteName("origin").call();
        }
        git.remoteAdd().setName("origin").setUri(new URIish(mirrorUri)).call();

        // Real `git clone` only sets up tracking for the branch you land on, not every branch —
        // matches that, and leaves any other branch trackable later via "checkout -b <name>
        // origin/<name>" (Arc 6's own teaching moment for setting up a tracking branch).
        String currentBranch = git.getRepository().getBranch();
        if (currentBranch != null && hasAnyCommit) {
            StoredConfig config = git.getRepository().getConfig();
            config.setString("branch", currentBranch, "remote", "origin");
            config.setString("branch", currentBranch, "merge", Constants.R_HEADS + currentBranch);
            config.save();
            git.fetch().setRemote("origin").call();
        }
        return bareMirror;
    }
}
