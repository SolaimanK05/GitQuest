package com.gitquest.core.campaign;

import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.model.RepoStateModel;

/** Builds a level's starting scenario (files on disk, pre-existing commits) into a freshly-initialized repo. */
@FunctionalInterface
public interface LevelSetup {

    void build(RepoStateModel model, CommandExecutor executor) throws Exception;
}
