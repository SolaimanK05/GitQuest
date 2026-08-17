package com.gitquest.core.campaign;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.service.RepositorySessionFactory;

/**
 * Builds the actual graded starting repo for a Campaign level — shared by {@code CampaignController}
 * (levels with no tutorial yet skip straight here) and {@code TutorialController} (once the
 * tutorial finishes or is skipped). Always a fresh disposable temp repo, never reused across
 * replays (CLAUDE.md 4.2: a replay must never touch the level's stored completion record).
 */
public final class LevelSessionFactory {

    private LevelSessionFactory() {
    }

    /** Blocking (disk/git I/O) — call off the JavaFX Application Thread. */
    public static RepoStateModel buildObjectiveSession(LevelDefinition level) throws Exception {
        Path tempDir = Files.createTempDirectory("gitquest-level-" + level.id() + "-");
        RepoStateModel model = RepositorySessionFactory.init(tempDir);
        CommandExecutor executor = new CommandExecutor(model);
        level.setup().build(model, executor);
        model.markDisposable(List.of(tempDir), false);
        return model;
    }
}
