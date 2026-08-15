package com.gitquest.core.command;

/** Unchecked wrapper around JGit's checked exceptions, so {@link CommandExecutor} has clean signatures. */
public class GitCommandException extends RuntimeException {

    public GitCommandException(String message, Throwable cause) {
        super(message, cause);
    }

    public GitCommandException(String message) {
        super(message);
    }
}
