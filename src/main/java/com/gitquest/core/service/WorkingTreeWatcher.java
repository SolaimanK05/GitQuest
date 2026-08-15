package com.gitquest.core.service;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Watches a working directory for on-disk changes (per CLAUDE.md 4.3: no
 * built-in editor — users edit files externally, this notices it). Registers
 * every subdirectory recursively (excluding {@code .git}, and picking up new
 * directories as they're created), and debounces bursts of events — editors
 * commonly fire several write events per save — before calling back once the
 * directory has gone quiet.
 *
 * <p>The callback runs on this watcher's own background thread, not the FX
 * Application Thread — callers touching UI state must hop back via
 * {@code Platform.runLater} themselves.
 */
public final class WorkingTreeWatcher {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

    private final Path root;
    private final Duration debounce;
    private final Runnable onSettled;
    private final Map<WatchKey, Path> keyToDir = new HashMap<>();

    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running;
    private volatile long lastEventNanos;
    private volatile long lastNotifiedNanos = -1;

    public WorkingTreeWatcher(Path root, Duration debounce, Runnable onSettled) {
        this.root = root;
        this.debounce = debounce;
        this.onSettled = onSettled;
    }

    public void start() {
        try {
            watchService = root.getFileSystem().newWatchService();
            registerAll(root);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Failed to start working-tree watcher", e);
        }
        running = true;
        watchThread = new Thread(this::watchLoop, "gitquest-working-tree-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException ignored) {
            // best-effort shutdown
        }
    }

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.poll(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.nio.file.ClosedWatchServiceException e) {
                return;
            }

            if (key != null) {
                handleKey(key);
            }
            checkDebounceElapsed();
        }
    }

    private void handleKey(WatchKey key) {
        Path dir = keyToDir.get(key);
        for (WatchEvent<?> event : key.pollEvents()) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                lastEventNanos = System.nanoTime();
                continue;
            }
            Path name = (Path) event.context();
            Path child = dir != null ? dir.resolve(name) : null;
            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                    && child != null && !isGitDir(child) && Files.isDirectory(child)) {
                try {
                    registerAll(child);
                } catch (IOException ignored) {
                    // directory may have already been removed; nothing to watch
                }
            }
            lastEventNanos = System.nanoTime();
        }
        boolean stillValid = key.reset();
        if (!stillValid) {
            keyToDir.remove(key);
        }
    }

    private void checkDebounceElapsed() {
        long last = lastEventNanos;
        if (last == 0 || last == lastNotifiedNanos) {
            return;
        }
        if (System.nanoTime() - last >= debounce.toNanos()) {
            lastNotifiedNanos = last;
            onSettled.run();
        }
    }

    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (isGitDir(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                WatchKey key = dir.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                keyToDir.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isGitDir(Path path) {
        return ".git".equals(String.valueOf(path.getFileName()));
    }
}
