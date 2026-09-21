# GitQuest

An interactive desktop app that teaches Git through **live, animated
visualization** instead of static tutorials. A guided, skill-tree-structured
campaign teaches Git commands step by step, and a sandbox mode turns any real
repository into an animated, explorable space where the commit graph, the
working tree, and the code's internal structure all move and update in real
time as you work.

## Contributors

| Name                 | ID        |
| -------------------- | --------- |
| S. M. Solaiman Kalam | 230041254 |
| Mahmudul Hasan       | 230041230 |
| Samman Rahin         | 230041260 |

## Video presentation

https://drive.google.com/file/d/1lPQl7r8aRCiILlhLz_543eMOVn8ckDmK/view?usp=sharing

## Table of contents

- [Why GitQuest](#why-gitquest)
- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Getting started](#getting-started)
- [Running the app](#running-the-app)
- [Running the tests](#running-the-tests)
- [Project structure](#project-structure)
- [The AI Git tutor](#the-ai-git-tutor)
- [Known limitations](#known-limitations)
- [Contributors](#contributors)
- [Video presentation](#video-presentation)

---

## Why GitQuest

Most people learn Git from a wall of text and a terminal that gives no
feedback beyond a prompt. GitQuest's premise is that Git becomes far easier
to understand once its internal model, the commit graph, branches, HEAD,
the working tree, is something you can literally _see move_. Every command
in GitQuest animates the transition from the old state to the new one,
rather than just redrawing a static diagram, so cause and effect stay
visible instead of disappearing between two silent screenshots.

## Features

### Guided Campaign

A skill-tree-structured campaign (not a flat list) gated by arc completion,
covering six arcs:

1. **Foundations** — init, add, commit, status, log, `.gitignore`
2. **Branching** — create/switch/delete, fast-forward vs. no-ff merge
3. **Conflicts** — induced conflicts, resolving, aborting a merge
4. **Rewriting history** — rebase, interactive rebase, cherry-pick, amend
5. **Recovery** — reflog, reset (soft/mixed/hard), revert vs. reset,
   recovering a deleted branch
6. **Remotes** — fetch vs. pull, push, tracking branches, force-push
   dangers (simulated locally, no network required)

Every level has a starting repo state, an objective, tiered hints (a free
hint, then a costlier "show solution"), and a line of real-world context for
why the command matters. Levels are validated against the _end state_ of the
repo, not an exact command sequence, since Git usually offers more than one
correct path to a goal. Progress is written to local storage as each level
completes, so a crash or an early exit never loses earlier progress, and any
completed level can be replayed from scratch without touching its stored
completion record.

### Sandbox mode

The centerpiece of the app: turn any real repository (or a scenario
template) into a live, explorable, animated space.

- **Git playground** — a button-based command palette, with a toggle to a
  real terminal-style text input for users who already know the commands
  they want to type. Every command animates the before/after diff of the
  commit graph rather than just re-rendering it.
- **No built-in editor, by design** — you edit files in your own IDE.
  GitQuest watches the working directory (`java.nio.file.WatchService`),
  debounces the burst of write events a save typically fires, and refreshes
  automatically. An edited-but-uncommitted file is shown as a distinct
  "dirty" highlight, kept visually separate from the commit graph, so the
  edit → stage → commit lifecycle stays legible instead of blurred together.
- **Remote change detection** — polls a real remote's ref tips (no data
  download) and surfaces a passive "origin/main has new commits" notice.
  GitQuest never auto-fetches or auto-merges on your behalf; you run fetch
  and pull yourself, which keeps the fetch-vs-pull distinction from the
  Remotes arc meaningful instead of papered over.
- **AI Git tutor (Assistant tab)** — a Gemini-powered chat tab that is both
  repo-aware (current branch, HEAD, recent commits, dirty/conflicted file
  counts) and file-aware (whichever file is selected in the sidebar gets
  its content attached, size-capped, so "explain this file" is answered
  from the real code). See [The AI Git tutor](#the-ai-git-tutor) below.
- **Code relationship graph (Java-scoped)** — parses every `.java` file
  with JavaParser, indexes `class/interface name → declaring file`, and
  draws a file→file edge wherever a reference resolves, no classpath
  configuration required. Method-level call resolution is layered on top
  via `JavaSymbolSolver`. Rendered as a force-directed, click-to-focus
  node-link graph that dims everything outside the selected node's direct
  dependencies/dependents.
- **Merge conflicts** — conflicted files get a distinct flag in the file
  tree, and a conflicted merge is rendered as a pending, pulsing state on
  the commit graph rather than an error dialog. A three-way diff (base /
  ours / theirs) is driven directly from JGit's merge result, with
  keep-mine / keep-theirs resolution actions per file.
- **Recovery** — an "undo last command" action backed by the real reflog,
  not a custom undo stack, so commands stay safe to experiment with.
- **Two-clone collaboration demo** — a local bare repo standing in as a
  shared "remote," with two working copies, to experience real divergence
  and conflicts without needing an actual network or GitHub account.

### Home screen

A campaign-progress skill-tree thumbnail plus a direct path into Sandbox.

## Tech stack

| Purpose                 | Choice                                                                                                                                                        |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Language / runtime      | Java 21 (LTS)                                                                                                                                                 |
| GUI                     | JavaFX 21 — `Timeline`/`KeyFrame`, `TranslateTransition`, `FadeTransition`, `Interpolator`, and `Canvas` + `AnimationTimer` for the physics-driven code graph |
| Git engine              | JGit 7.7.1 (pure-Java; GitQuest never shells out to a `git` binary)                                                                                           |
| Code structure analysis | JavaParser 3.28.2 + JavaSymbolSolver                                                                                                                          |
| Local persistence       | Flat JSON (hand-rolled reader/writer) for campaign progress, level definitions, and badges                                                                    |
| AI Assistant            | Google Gemini API over plain `java.net.http.HttpClient`, no SDK dependency                                                                                    |
| Build                   | Maven, with per-OS profiles selecting the right JavaFX platform classifier                                                                                    |

## Architecture

GitQuest's core sits underneath everything else and is built to keep Git
mechanics, UI, and animation cleanly separated:

- **`RepoStateModel`** wraps a JGit `Repository` and exposes commits,
  branches, refs, and HEAD as observable state the UI binds to. Commit
  layout (lanes, positions) comes straight from JGit's own
  `PlotWalk`/`PlotCommitList`, rather than a hand-rolled graph layout.
- **`CommandExecutor`** runs every Git operation through JGit's porcelain
  API (`Git.commit()`, `.branchCreate()`, `.merge()`, ...) and returns a
  diff of the graph state before/after, so the UI can animate the change
  instead of re-rendering it statically.
- **`GoalValidator`** checks whether the sandbox's current repo state
  matches a campaign level's target state (branch shape, HEAD position,
  file contents) — validating the destination, not the path taken to it.

Every JGit operation that touches disk or walks history runs off the
JavaFX Application Thread via `javafx.concurrent.Task`/`Service`, publishing
results back through bound properties or `Platform.runLater`, so a blocking
Git call never stalls a frame of animation.

## Getting started

### Prerequisites

- JDK 21
- Maven (or use your IDE's embedded Maven — Eclipse's m2e works out of the
  box against the included `pom.xml`)
- A Gemini API key if you want the Assistant tab to answer questions (the
  rest of the app works without one)

### Clone and configure

```bash
git clone <this repo's URL>
cd GitQuest
cp .env.example .env
# then edit .env and paste a real GEMINI_API_KEY
```

`.env` is gitignored; the key is never hardcoded or committed. You can
instead set `GEMINI_API_KEY` as a real environment variable if you prefer.
`GEMINI_MODEL` is optional and overrides the default model name without a
code change, since Gemini model availability has shifted faster than
general documentation keeps up with.

## Running the app

**From an IDE (recommended for day-to-day development):** run
`com.gitquest.app.Launcher` directly as a Java application. No VM arguments
are required.

**From Maven:**

```bash
mvn javafx:run
```

The `javafx.platform` classifier (win/mac/linux) is selected automatically
by a Maven profile based on your OS.

## Running the tests

```bash
mvn test
```

Tests cover campaign progress persistence, the Java dependency analyzer
behind the code relationship graph, conflict diff reading, and the commit
graph diff calculator that drives animation.

## Project structure

```
src/main/java/com/gitquest/
  app/                 Application entry point (Launcher, GitQuestApp)
  core/
    assistant/         Gemini client, config, repo/file context summaries
    campaign/           Arc and level definitions (Foundations..Remotes)
    codegraph/          JavaParser-based file/method relationship analysis
    command/            CommandExecutor -- JGit porcelain wrapper + diffing
    conflict/           Three-way conflict diff modeling
    model/              RepoStateModel, CommitNode, BranchRef, GraphDiff...
    service/            Background/async plumbing off the FX thread
    validation/         GoalValidator
  persistence/          Flat-JSON campaign progress store
  ui/
    campaign/           Skill-tree and level UI
    collab/             Two-clone collaboration demo UI
    common/             Shared widgets (loading indicators, etc.)
    entry/              Clone / Open / Initialize entry screen
    home/                Home screen + progress thumbnail
    sandbox/             The sandbox: commit graph, terminal, Assistant tab,
                          conflict resolution, code graph
    tutorial/            Animated per-level guided tutorials
src/test/java/           Unit tests (JUnit 5)
```

## The AI Git tutor

The Assistant tab lives in `com.gitquest.core.assistant`
(`GeminiClient`, `GeminiConfig`, `RepoContextSummary`, `ChatMessage`,
`MiniJson`) and is wired into the Sandbox's Assistant tab. It replaced an
earlier planned codebase visualizer (treemap + churn/recency overlays),
which was dropped mid-build in favor of a repo-aware chat tutor as a more
directly useful teaching tool.

Every message sent to Gemini carries a live summary of the sandbox's
actual state, current branch, HEAD, total commit count, the most recent
commits, dirty file count, and any conflicted files, so answers ground
themselves in the repo actually open, not just Git in the abstract.
Whichever file is selected in the sidebar file tree gets its content
attached too (size-capped; large or binary files are skipped with a
plain-language note rather than guessed at), so "explain this file"
questions are answered from real code.

The client retries with exponential backoff on HTTP 503 (model
overloaded), 429 (rate limited), and request timeouts, all common in the
days right after a new Gemini model ships.

## Known limitations

These are deliberate scope decisions, not oversights:

- No built-in code editor. GitQuest is a Git visualizer, not an IDE; you
  edit files in your own editor and GitQuest watches for the change.
- No networked/online leaderboard, no Daily Challenge mode, no procedural
  puzzle generation or scoring system.
- The code relationship graph is Java-only, and its Tier 1 (file-level)
  resolution won't fully resolve overloaded methods, polymorphism,
  reflection, or external library internals. It's meant as a map to help
  you navigate a codebase, not a compiler.
- Non-text merge conflicts (delete/modify, diverging renames) are shown as
  a plain-language explanation rather than diffed.
- No webhook-based push notifications (would require a public server
  endpoint, out of scope for a single-user offline desktop app).
