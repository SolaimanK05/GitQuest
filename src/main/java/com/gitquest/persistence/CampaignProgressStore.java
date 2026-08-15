package com.gitquest.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gitquest.core.campaign.CampaignProgress;
import com.gitquest.core.campaign.LevelCompletionRecord;

/**
 * Loads/saves {@link CampaignProgress} as a small flat JSON file, per
 * CLAUDE.md's "SQLite via JDBC, or flat JSON" persistence choice — JSON
 * here, hand-rolled: the shape is one flat {@code levelId -> record} map,
 * not worth a library dependency. Default location is app-level state
 * (the user's home directory), independent of whichever repo Sandbox mode
 * happens to have open.
 */
public final class CampaignProgressStore {

    private static final Path DEFAULT_PATH =
            Path.of(System.getProperty("user.home"), ".gitquest", "campaign-progress.json");

    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "\"(?<id>(?:[^\"\\\\]|\\\\.)+)\"\\s*:\\s*\\{\\s*"
                    + "\"completedAt\"\\s*:\\s*\"(?<completedAt>[^\"]+)\"\\s*,\\s*"
                    + "\"hintTierUsed\"\\s*:\\s*(?<hintTier>\\d+)\\s*\\}");

    private final Path storePath;

    public CampaignProgressStore() {
        this(DEFAULT_PATH);
    }

    public CampaignProgressStore(Path storePath) {
        this.storePath = storePath;
    }

    public CampaignProgress load() {
        if (!Files.isRegularFile(storePath)) {
            return new CampaignProgress();
        }
        try {
            String json = Files.readString(storePath, StandardCharsets.UTF_8);
            return new CampaignProgress(parse(json));
        } catch (IOException e) {
            return new CampaignProgress();
        }
    }

    public void save(CampaignProgress progress) {
        try {
            if (storePath.getParent() != null) {
                Files.createDirectories(storePath.getParent());
            }
            Files.writeString(storePath, write(progress.completions()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save campaign progress", e);
        }
    }

    private static String write(Map<String, LevelCompletionRecord> completions) {
        StringBuilder json = new StringBuilder("{\n");
        int i = 0;
        for (Map.Entry<String, LevelCompletionRecord> entry : completions.entrySet()) {
            LevelCompletionRecord record = entry.getValue();
            json.append("  \"").append(escape(entry.getKey())).append("\": {")
                    .append("\"completedAt\": \"").append(record.completedAt()).append("\", ")
                    .append("\"hintTierUsed\": ").append(record.hintTierUsed())
                    .append("}");
            if (++i < completions.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("}\n");
        return json.toString();
    }

    private static Map<String, LevelCompletionRecord> parse(String json) {
        Map<String, LevelCompletionRecord> result = new LinkedHashMap<>();
        Matcher matcher = ENTRY_PATTERN.matcher(json);
        while (matcher.find()) {
            String id = unescape(matcher.group("id"));
            Instant completedAt = Instant.parse(matcher.group("completedAt"));
            int hintTier = Integer.parseInt(matcher.group("hintTier"));
            result.put(id, new LevelCompletionRecord(id, completedAt, hintTier));
        }
        return result;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
