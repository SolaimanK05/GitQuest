package com.gitquest.core.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.PersonIdent;

import com.gitquest.core.model.RepoStateModel;

/**
 * Builds the shared starting point for the two-clone collaboration sandbox (CLAUDE.md 4.5): a
 * fresh local bare repo standing in for a remote like GitHub — no real network, the same trick
 * Arc 6's campaign levels use (see {@code RemotesLevels.setUpOrigin}) — with two independent real
 * clones of it, each already tracking "origin" exactly as a real {@code git clone} would leave
 * them. From here the two clones are just ordinary Sandbox sessions that happen to share a
 * remote: pushing from one and fetching/pulling from the other is what makes divergence and
 * conflicts real instead of staged.
 */
public final class CollaborationSessionFactory {

    private static final PersonIdent SEED_AUTHOR = new PersonIdent("GitQuest", "gitquest@example.com");

    private CollaborationSessionFactory() {
    }

    public record CollaborationPair(RepoStateModel cloneA, RepoStateModel cloneB, Path bareOrigin) {
    }

    /** Blocking (disk/git I/O) — call off the JavaFX Application Thread. */
    public static CollaborationPair createPair() throws Exception {
        Path bareOrigin = Files.createTempDirectory("gitquest-collab-origin-");
        try (Git ignored = Git.init().setDirectory(bareOrigin.toFile()).setBare(true).call()) {
            // just creating the bare "remote"; nothing else to do with this handle
        }

        Path seedDir = Files.createTempDirectory("gitquest-collab-seed-");
        try (Git seed = Git.init().setDirectory(seedDir.toFile()).call()) {
            Files.writeString(seedDir.resolve("README.md"),
                    "# Shared project\n\nA starting point for two teammates working from separate clones.\n");
            seed.add().addFilepattern(".").call();
            seed.commit().setMessage("Shared base").setAuthor(SEED_AUTHOR).call();
            seed.push().setRemote(bareOrigin.toUri().toString()).add("main").call();
        }

        String originUri = bareOrigin.toUri().toString();
        Path cloneADir = Files.createTempDirectory("gitquest-collab-a-");
        Path cloneBDir = Files.createTempDirectory("gitquest-collab-b-");
        RepoStateModel cloneA = RepositorySessionFactory.clone(originUri, cloneADir, NullProgressMonitor.INSTANCE);
        RepoStateModel cloneB = RepositorySessionFactory.clone(originUri, cloneBDir, NullProgressMonitor.INSTANCE);

        return new CollaborationPair(cloneA, cloneB, bareOrigin);
    }
}
