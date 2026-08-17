package com.gitquest.core.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;

import com.gitquest.core.model.RepoStateModel;

/**
 * Opens/creates a {@link RepoStateModel} for one session (Clone / Open / Initialize, per
 * CLAUDE.md 4.1). Blocking; callers must run these off the JavaFX Application Thread (see
 * {@link CommandService}).
 *
 * <p>Every session is a true sandbox: {@link #init}, {@link #openAsSandbox}, and
 * {@link #cloneAsSandbox} all end up running against an app-managed, disposable temp directory —
 * never the real folder a user pointed at — and any "origin" is always a local, disposable bare
 * repo (see {@link LocalOriginMirror}), never a real network endpoint. That means push/fetch/pull
 * are real JGit operations (so the lessons stay real — a rejected non-fast-forward push behaves
 * exactly like it would for real), but nothing you do in Sandbox can ever reach a real remote or
 * write back into a real project's actual folder. The plain {@link #clone} below is the one
 * exception: it does no origin-redirection, since {@code CollaborationSessionFactory} deliberately
 * wants its two clones' "origin" to stay pointed at their one shared local bare repo.
 *
 * <p>The {@code Git}/{@code Repository} handles created here are deliberately
 * left open for the lifetime of the session — {@link RepoStateModel} owns
 * them for the rest of the app's run.
 */
public final class RepositorySessionFactory {

    private RepositorySessionFactory() {
    }

    /** Always call with an app-managed temp directory — never a real folder the user actually cares about. */
    public static RepoStateModel init(Path targetDir) throws GitAPIException, IOException {
        Git git = Git.init().setDirectory(targetDir.toFile()).call();
        return newSession(git.getRepository());
    }

    /**
     * True-sandbox "Open": {@code existingRepoDir} is a real folder the user picked to explore —
     * it is only ever read from, never written to. If it's already a git repo, its full history is
     * cloned (a local {@code git clone}, which only reads {@code existingRepoDir}'s committed
     * objects — any of its uncommitted working-tree edits are intentionally not carried over) into
     * {@code sandboxDir}, then {@code origin} is redirected to a disposable local mirror
     * ({@link LocalOriginMirror}) so push/fetch/pull can never reach back into the original. If
     * it's not a git repo yet, its current files are copied into {@code sandboxDir} and a fresh
     * repo is initialized there — {@code existingRepoDir} itself never gets a {@code .git} written
     * into it.
     */
    public static RepoStateModel openAsSandbox(Path existingRepoDir, Path sandboxDir) throws Exception {
        if (GitDirectoryValidator.isGitRepository(existingRepoDir)) {
            Git git = Git.cloneRepository()
                    .setURI(existingRepoDir.toUri().toString())
                    .setDirectory(sandboxDir.toFile())
                    .call();
            Path mirror = LocalOriginMirror.redirectOriginToLocalMirror(git);
            RepoStateModel model = newSession(git.getRepository());
            model.markDisposable(List.of(sandboxDir, mirror), true);
            return model;
        }
        FileTreeCopy.copyRecursively(existingRepoDir, sandboxDir);
        Git git = Git.init().setDirectory(sandboxDir.toFile()).call();
        RepoStateModel model = newSession(git.getRepository());
        model.markDisposable(List.of(sandboxDir), true);
        return model;
    }

    /**
     * A real clone from {@code uri} — the one unavoidable real network operation, since that's the
     * whole point of "Clone" — into {@code targetDir}, immediately followed by redirecting
     * {@code origin} to a disposable local mirror ({@link LocalOriginMirror}) so every subsequent
     * push/fetch/pull is real-but-harmless, same as {@link #openAsSandbox}.
     */
    public static RepoStateModel cloneAsSandbox(String uri, Path targetDir, ProgressMonitor progress) throws Exception {
        Git git = Git.cloneRepository()
                .setURI(uri)
                .setDirectory(targetDir.toFile())
                .setProgressMonitor(progress)
                .call();
        Path mirror = LocalOriginMirror.redirectOriginToLocalMirror(git);
        RepoStateModel model = newSession(git.getRepository());
        model.markDisposable(List.of(targetDir, mirror), true);
        return model;
    }

    /**
     * A plain clone with {@code origin} left pointing at wherever {@code uri} says — used by
     * {@code CollaborationSessionFactory}, which deliberately wants both of its clones' "origin" to
     * stay pointed at their one shared local bare repo rather than each getting its own separate
     * mirror. Not used by the Entry screen directly; see {@link #cloneAsSandbox} for that.
     */
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
