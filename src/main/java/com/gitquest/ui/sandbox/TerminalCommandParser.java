package com.gitquest.ui.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.ResetCommand.ResetType;

import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.model.GraphDiff;

/**
 * Parses a single terminal-style command line and dispatches it through the
 * same {@link CommandExecutor} the button palette uses — per CLAUDE.md 4.3's
 * "toggle to a real terminal-style text input for confident users". Supports
 * {@code add}, {@code commit -m "message"}, {@code branch <name>
 * [<startpoint>]}, {@code checkout <name>}, {@code checkout -b <local>
 * <startpoint>}, {@code merge <name> [--no-ff]}, {@code merge --abort},
 * {@code delete <name>}, {@code amend -m "message"},
 * {@code cherry-pick <ref>}, {@code rebase <upstream>},
 * {@code rebase --squash <upstream> -m "message"},
 * {@code rebase --continue|--abort}, {@code reset [--soft|--mixed|--hard]
 * <ref>}, {@code revert <ref>}, {@code fetch}, {@code pull},
 * {@code push [--force]}, {@code undo} (reflog-backed "undo last command").
 * {@code status}, {@code reflog}, and
 * {@code refresh}/{@code log} don't produce a {@link GraphDiff} and are
 * special-cased by the caller before reaching this parser. Unrecognized
 * input throws {@link IllegalArgumentException} so the caller can log it as
 * an error rather than crash.
 */
final class TerminalCommandParser {

    private static final Pattern TOKEN = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    private TerminalCommandParser() {
    }

    static GraphDiff execute(String rawInput, CommandExecutor executor) {
        List<String> tokens = tokenize(rawInput);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Empty command");
        }
        String verb = tokens.get(0);
        switch (verb) {
            case "add":
            case "stage":
                return executor.stageAll();
            case "commit":
                return executor.commit(requireFlagValue(tokens, "-m", verb), "GitQuest User", "gitquest@example.com");
            case "branch": {
                String name = requireArg(tokens, verb);
                return tokens.size() >= 3 ? executor.createBranchAt(name, tokens.get(2)) : executor.createBranch(name);
            }
            case "checkout": {
                int trackIndex = tokens.indexOf("-b");
                if (trackIndex >= 0) {
                    if (trackIndex + 2 >= tokens.size()) {
                        throw new IllegalArgumentException(
                                "checkout -b requires a new branch name and a start point, e.g. \"checkout -b feature origin/feature\"");
                    }
                    return executor.checkoutNewTrackingBranch(tokens.get(trackIndex + 1), tokens.get(trackIndex + 2));
                }
                return executor.checkout(requireArg(tokens, verb));
            }
            case "merge": {
                if (tokens.contains("--abort")) {
                    return executor.abortMerge();
                }
                String branch = requireArg(tokens, verb);
                boolean noFastForward = tokens.contains("--no-ff");
                return executor.merge(branch, noFastForward);
            }
            case "delete":
                return executor.deleteBranch(requireArg(tokens, verb));
            case "amend":
                return executor.amend(requireFlagValue(tokens, "-m", verb));
            case "cherry-pick":
                return executor.cherryPick(requireArg(tokens, verb));
            case "rebase": {
                if (tokens.contains("--abort")) {
                    return executor.rebaseAbort();
                }
                if (tokens.contains("--continue")) {
                    return executor.rebaseContinue();
                }
                if (tokens.contains("--squash")) {
                    String upstream = requirePositionalArg(tokens, verb);
                    String message = requireFlagValue(tokens, "-m", verb);
                    return executor.rebaseSquash(upstream, message);
                }
                return executor.rebase(requireArg(tokens, verb));
            }
            case "reset": {
                ResetType mode = ResetType.MIXED;
                if (tokens.contains("--soft")) {
                    mode = ResetType.SOFT;
                } else if (tokens.contains("--hard")) {
                    mode = ResetType.HARD;
                }
                return executor.reset(requirePositionalArg(tokens, verb), mode);
            }
            case "revert":
                return executor.revert(requireArg(tokens, verb));
            case "fetch":
                return executor.fetch();
            case "pull":
                return executor.pull();
            case "push":
                return executor.push(tokens.contains("--force") || tokens.contains("-f"));
            case "undo":
                return executor.undoLastCommand();
            default:
                throw new IllegalArgumentException("Unknown command: " + verb
                        + ". Supported: add, commit -m \"...\", branch <name> [<startpoint>], checkout <name>, "
                        + "checkout -b <local> <startpoint>, merge <name> [--no-ff], merge --abort, delete <name>, "
                        + "amend -m \"...\", cherry-pick <ref>, rebase <upstream>, rebase --squash <upstream> -m \"...\", "
                        + "rebase --continue|--abort, reset [--soft|--mixed|--hard] <ref>, revert <ref>, "
                        + "fetch, pull, push [--force], undo, status, reflog, refresh");
        }
    }

    private static String requireArg(List<String> tokens, String verb) {
        if (tokens.size() < 2) {
            throw new IllegalArgumentException(verb + " requires an argument, e.g. \"" + verb + " feature\"");
        }
        return tokens.get(1);
    }

    private static String requireFlagValue(List<String> tokens, String flag, String verb) {
        int index = tokens.indexOf(flag);
        if (index < 0 || index + 1 >= tokens.size()) {
            throw new IllegalArgumentException(verb + " requires " + flag + " \"message\"");
        }
        return tokens.get(index + 1);
    }

    /** First token after the verb that isn't a recognized flag (or a flag's value) — the ref/branch argument. */
    private static String requirePositionalArg(List<String> tokens, String verb) {
        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals("-m")) {
                i++; // skip the flag's value too
                continue;
            }
            if (token.startsWith("-")) {
                continue;
            }
            return token;
        }
        throw new IllegalArgumentException(verb + " requires an argument");
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(input.trim());
        while (matcher.find()) {
            tokens.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
        return tokens;
    }
}
