package com.gitquest.core.service;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.gitquest.core.model.RepoStateModel;

/**
 * Opens/creates a {@link RepoStateModel} for one session (Clone / Open /
 * Initialize, per CLAUDE.md 4.1). Blocking; callers must run these off the
 * JavaFX Application Thread (see {@link CommandService}).
 *
 * <p>The {@code Git}/{@code Repository} handles created here are deliberately
 * left open for the lifetime of the session — {@link RepoStateModel} owns
 * them for the rest of the app's run.
 */
public final class RepositorySessionFactory {

    private RepositorySessionFactory() {
    }

    public static RepoStateModel init(Path targetDir) throws GitAPIException, IOException {
        Git git = Git.init().setDirectory(targetDir.toFile()).call();
        return newSession(git.getRepository());
    }

    public static RepoStateModel open(Path existingRepoDir) throws IOException {
        Repository repository = new FileRepositoryBuilder()
                .setGitDir(existingRepoDir.resolve(".git").toFile())
                .readEnvironment()
                .findGitDir()
                .build();
        return newSession(repository);
    }

    public static RepoStateModel clone(String uri, Path targetDir, ProgressMonitor progress)
            throws GitAPIException, IOException {
        Git git = Git.cloneRepository()
                .setURI(uri)
                .setDirectory(targetDir.toFile())
                .setProgressMonitor(progress)
                .call();
        return newSession(git.getRepository());
    }

    private static RepoStateModel newSession(Repository repository) throws IOException {
        RepoStateModel model = new RepoStateModel(repository);
        model.refresh();
        return model;
    }
}
