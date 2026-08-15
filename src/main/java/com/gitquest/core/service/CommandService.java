package com.gitquest.core.service;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javafx.concurrent.Task;

/**
 * Runs blocking work (JGit calls) on a single dedicated background thread
 * and always delivers the result back on the JavaFX Application Thread —
 * the seam that keeps every git operation off the FX thread per CLAUDE.md
 * Section 3, without call sites needing their own {@code Platform.runLater}.
 *
 * <p>A single worker thread serializes mutating JGit calls against the
 * shared {@code Repository}/{@code Git} instance, since JGit does not
 * guarantee safe concurrent mutation.
 */
public final class CommandService {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gitquest-git-worker");
        thread.setDaemon(true);
        return thread;
    });

    public <T> void submit(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(task.getException()));
        executor.submit(task);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
