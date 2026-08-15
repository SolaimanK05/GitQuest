package com.gitquest.ui.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.model.GraphDiff;

/**
 * Parses a single terminal-style command line and dispatches it through the
 * same {@link CommandExecutor} the button palette uses — per CLAUDE.md 4.3's
 * "toggle to a real terminal-style text input for confident users". Supports
 * {@code add}, {@code commit -m "message"}, {@code branch <name>},
 * {@code checkout <name>}, {@code merge <name> [--no-ff]},
 * {@code delete <name>}. {@code status}
 * and {@code refresh}/{@code log} don't produce a {@link GraphDiff} and are
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
            case "branch":
                return executor.createBranch(requireArg(tokens, verb));
            case "checkout":
                return executor.checkout(requireArg(tokens, verb));
            case "merge": {
                String branch = requireArg(tokens, verb);
                boolean noFastForward = tokens.contains("--no-ff");
                return executor.merge(branch, noFastForward);
            }
            case "delete":
                return executor.deleteBranch(requireArg(tokens, verb));
            default:
                throw new IllegalArgumentException("Unknown command: " + verb
                        + ". Supported: add, commit -m \"...\", branch <name>, checkout <name>, merge <name> [--no-ff], "
                        + "delete <name>, status, refresh");
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

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(input.trim());
        while (matcher.find()) {
            tokens.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }
        return tokens;
    }
}
