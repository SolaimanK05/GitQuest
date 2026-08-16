package com.gitquest.core.service;

import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Polls a remote's branch tips on an interval via {@code git ls-remote}
 * (lists refs over the transport, downloads no objects) and reports which
 * locally-tracked branches have since diverged — per CLAUDE.md 4.3: "Git has
 * no push notifications — poll... only do a full fetch once something's
 * actually changed... do not auto-fetch/merge; let the user run fetch/pull
 * themselves." This class never mutates the repository.
 *
 * <p>A branch only counts as "changed" once it already has a local
 * remote-tracking ref (i.e. it's been fetched at least once) — otherwise
 * every branch on a freshly cloned/never-fetched remote would trivially
 * read as "new", which isn't a useful signal.
 *
 * <p>Runs its own daemon polling thread; both the periodic callback and
 * {@link #checkNow()}'s callback fire on that thread (or, for a
 * caller-triggered {@code checkNow()}, on whatever thread calls it) — not
 * the JavaFX Application Thread. Touching UI state from the callback
 * requires hopping back via {@code Platform.runLater}.
 */
public final class RemoteRefPoller {

    private final Git git;
    private final String remoteName;
    private final Duration interval;
    private final Consumer<Set<String>> onChangedBranches;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread pollThread;

    public RemoteRefPoller(Git git, String remoteName, Duration interval, Consumer<Set<String>> onChangedBranches) {
        this.git = git;
        this.remoteName = remoteName;
        this.interval = interval;
        this.onChangedBranches = onChangedBranches;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        pollThread = new Thread(this::pollLoop, "gitquest-remote-poller");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void stop() {
        running.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
        }
    }

    /**
     * Runs one check immediately (blocking) and reports the result via the
     * same callback the background loop uses. Returns {@code null} if the
     * check couldn't run at all (no such remote configured, offline,
     * transport error) — the caller can distinguish that from a successful
     * check that simply found nothing new (an empty set).
     */
    public Set<String> checkNow() {
        Set<String> changed = checkOnce();
        if (changed != null) {
            onChangedBranches.accept(changed);
        }
        return changed;
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                checkNow();
            } catch (RuntimeException ignored) {
                // a bad tick shouldn't kill the polling loop; the next one retries
            }
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private Set<String> checkOnce() {
        try {
            Repository repository = git.getRepository();
            if (repository.getConfig().getString("remote", remoteName, "url") == null) {
                return null; // no such remote configured — nothing to poll
            }
            Collection<Ref> remoteRefs = git.lsRemote().setRemote(remoteName).setHeads(true).call();
            Set<String> changed = new HashSet<>();
            for (Ref remoteRef : remoteRefs) {
                String shortName = Repository.shortenRefName(remoteRef.getName());
                Ref trackingRef = repository.exactRef("refs/remotes/" + remoteName + "/" + shortName);
                if (trackingRef == null) {
                    continue; // never fetched locally yet — nothing to compare against
                }
                ObjectId trackedId = trackingRef.getObjectId();
                ObjectId liveId = remoteRef.getObjectId();
                if (trackedId != null && liveId != null && !trackedId.equals(liveId)) {
                    changed.add(shortName);
                }
            }
            return changed;
        } catch (Exception e) {
            return null; // transient network/transport failure — next tick retries
        }
    }
}
