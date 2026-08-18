package com.gitquest.core.assistant;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads Gemini API configuration from a real OS environment variable, or (as a convenience
 * fallback for local dev) a plain {@code KEY=value} {@code .env} file in the project's working
 * directory — either way, the key must never be hardcoded or committed (see .gitignore). A
 * missing key just means the Assistant tab shows a setup notice instead of a chat box; it's
 * never a hard failure.
 */
public final class GeminiConfig {

    // gemini-2.0-flash was retired 2026-03-31. gemini-2.5-flash returns HTTP 404 for newer API
    // keys ("no longer available to new users"), and its own error response names
    // gemini-3.6-flash as the replacement -- trust that live signal over general docs, since
    // model availability apparently varies per account/key. Override with GEMINI_MODEL if this
    // drifts again; check the live error message first, it's more current than any doc snapshot.
    private static final String DEFAULT_MODEL = "gemini-3.6-flash";
    private static final Map<String, String> DOT_ENV = loadDotEnv();

    private GeminiConfig() {
    }

    public static boolean isConfigured() {
        String key = apiKey();
        return key != null && !key.isBlank();
    }

    public static String apiKey() {
        return valueOf("GEMINI_API_KEY");
    }

    /** Override with GEMINI_MODEL if a teammate wants a different Gemini model; otherwise a sensible default. */
    public static String model() {
        String override = valueOf("GEMINI_MODEL");
        return override != null && !override.isBlank() ? override : DEFAULT_MODEL;
    }

    /** A real environment variable always wins if both are set — the .env file is just a local-dev convenience. */
    private static String valueOf(String key) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return DOT_ENV.get(key);
    }

    /**
     * Reads {@code .env} from the current working directory (the project root, for both an
     * Eclipse run configuration and {@code mvn javafx:run}) if present. Silently absent — not an
     * error — if the file doesn't exist or can't be read; this is a convenience, not a
     * requirement.
     */
    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<>();
        Path envFile = Path.of(".env");
        if (!Files.isRegularFile(envFile)) {
            return values;
        }
        try {
            for (String rawLine : Files.readAllLines(envFile)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                if (value.length() >= 2 && isQuoted(value)) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException e) {
            // Best-effort convenience feature -- fall back to no .env values rather than crash startup.
        }
        return values;
    }

    private static boolean isQuoted(String value) {
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        return (first == '"' && last == '"') || (first == '\'' && last == '\'');
    }
}
