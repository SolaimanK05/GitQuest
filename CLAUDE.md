# GitScape — Interactive Git Learning App

> Working title, rename freely. This file is a project brief for an academic
> final project (CSE 4402: Visual Programming Lab). It is meant to give an
> AI coding agent full context before writing any code.

## 1. Elevator pitch

A desktop app that teaches Git through **live, animated visualization**
instead of static tutorials. A guided campaign teaches commands step by
step; a sandbox mode turns any real repository into an animated, explorable
space — the commit graph, the file structure, and even the code's internal
relationships all move and update in real time as the user works.

**Course alignment:** UN SDG 4 (Quality Education). Avoids "management
system"-style scope. Targets ~10–12 hrs/week/person across a 3-person team
for one semester — see Section 6 for what that means for build order.

## 2. Tech stack (verified current as of writing)

| Purpose | Choice | Notes |
|---|---|---|
| Language / runtime | Java 21 LTS | Safe, broadly supported baseline. Java 25 LTS (newest LTS, released Sept 2025) is a valid alternative if the team wants newer language features — just keep JavaFX/JGit/JavaParser versions aligned to whichever JDK is picked. |
| GUI | JavaFX (matching JavaFX 21 if on Java 21, or JavaFX 25 if on Java 25) | Chosen over Swing specifically for animation quality — `Timeline`/`KeyFrame`, `TranslateTransition`, `FadeTransition`, `Interpolator`, and `Canvas` + `AnimationTimer` for custom physics-driven views. |
| Git engine | JGit 7.7.1+ (Java 11+ required) | Pure-Java Git implementation. Drives everything — never shell out to a `git` binary. |
| Code structure analysis | JavaParser 3.28.2+ (with JavaSymbolSolver) | Java-only scope. Used for the code-relationship graph. |
| Local persistence | Flat JSON (implemented) | Campaign progress, level definitions, badges — hand-rolled regex-based reader/writer, see `CampaignProgressStore`. SQLite was the documented alternative but wasn't needed. |
| AI Assistant | Google Gemini API (REST, `generateContent`) | No SDK dependency — the JDK's own `java.net.http.HttpClient` plus a small hand-rolled JSON reader/writer (see `com.gitquest.core.assistant`). Key from a `GEMINI_API_KEY` env var or a gitignored `.env` file, never hardcoded/committed. |
| Build | Maven or Gradle | Pin dependency versions; this is a single-user offline desktop app, not a service — most server/production concerns (auth, rate limiting, migrations) don't apply here. |

## 3. Core architecture — build this first, everything else sits on top

- **RepoStateModel** — wraps a JGit `Repository`, exposes commits, branches,
  refs, and HEAD as observable objects the UI can bind to. Built via
  `PlotWalk` + `PlotCommitList` (from `org.eclipse.jgit.revplot`) so branch
  lane/position layout comes from JGit directly — do not hand-roll commit
  graph layout.
- **CommandExecutor** — runs git operations through JGit's porcelain API
  (`Git.commit()`, `.branchCreate()`, `.merge()`, etc.), and returns a
  **diff of the graph state before/after** so the UI layer can animate the
  change rather than just re-rendering statically.
- **GoalValidator** — given a target repo state (branch shape, HEAD
  position, file contents), checks whether the current state matches.
  Validates against *end state*, not exact command sequence — there are
  multiple valid ways to reach most Git states, and only one should not be
  rewarded. Powers Campaign level completion.
- All JGit operations that touch disk or walk history must run off the
  JavaFX Application Thread (use `javafx.concurrent.Task`/`Service`, update
  UI via bound properties or `Platform.runLater`) — this app lives and dies
  on animation smoothness, so a blocking git call must never stall a frame.

## 4. Feature set

### 4.1 Entry point
Three ways to start a session, equally weighted, no built-in editor:
- **Clone** a remote URL.
- **Open** an existing local folder — validate it actually contains a
  `.git` directory; if not, offer to Initialize instead rather than
  failing silently.
- **Initialize** a new repo from scratch.

### 4.2 Guided Campaign
Skill-tree structured (not a flat list), gated by arc completion:

1. **Foundations** — init, add, commit, status, log, `.gitignore`
2. **Branching** — create/switch/delete, fast-forward vs. no-ff merge
3. **Conflicts** — induced conflicts, resolving, aborting a merge
4. **Rewriting history** — rebase, interactive rebase, cherry-pick, amend
5. **Recovery** — reflog, reset (soft/mixed/hard), revert vs. reset,
   recovering a deleted branch
6. **Remotes** — fetch vs. pull, push, tracking branches, force-push
   dangers (simulate locally, no real network required)

Each level: starting repo state → objective → tiered hints (a free hint,
then a costlier "show solution") → one line of real-world "why this
matters" context. Uses the shared `GoalValidator`.

**Progress persistence:** campaign progress (per-level completion, arc
unlock state, hint tier used) is saved to the local persistence layer
(flat JSON per Section 2) as levels are completed, not just at app exit
— a crash or close mid-session shouldn't lose earlier completions.

**Replay / reset a completed level:** users can revisit any previously
completed level and reset it to try again. This reset is **local to the
replay attempt only** — it re-initializes that level's starting repo state
so the user can practice, but it must never touch or clear the level's
stored completion record. A level already marked complete stays complete
(and stays unlocked-downstream) regardless of how a replay attempt turns
out, including a replay that ends without reaching the goal state again.
Model this as the campaign save storing one "best/completed" result per
level, separate from the transient in-progress repo state that
`CommandExecutor`/`GoalValidator` operate on during a replay.

### 4.3 Sandbox Mode
The centerpiece — turns any repo (real or scenario-template) into a live,
explorable, animated space.

**Git playground**
- Command palette (buttons) with a toggle to a real terminal-style text
  input for confident users.
- Live commit graph rendered from `RepoStateModel`, animated on every
  command via the diff from `CommandExecutor`.
- Hover tooltips explaining what each command does.

**No built-in editor — watch files instead**
- Users edit files in their own IDE/editor. Use the JDK's built-in file
  watch service (`java.nio.file.WatchService`) on the working directory;
  on a change event, debounce briefly (editors often fire multiple write
  events per save) then refresh.
- Edited-but-uncommitted changes are a **working-tree state**, not a graph
  node — represent them as a distinct "dirty" highlight on the affected
  file, separate from the commit graph. This distinction (edit → stage →
  commit) is itself a teaching moment; don't blur it in the UI.

**Remote change / push detection**
- For a real external remote (e.g. GitHub): Git has no push notifications —
  poll. Cheaply check remote ref tips (no download) on an interval or
  manual refresh; only do a full fetch once something's actually changed.
  Surface it as a passive "origin/main has new commits" notice — do **not**
  auto-fetch/merge; let the user run fetch/pull themselves so the
  fetch-vs-pull distinction from Arc 6 stays reinforced.
- For the local two-clone collaboration feature (see 4.5): the "remote" is
  just a bare repo on the same disk, so it can be watched the same way as
  the working directory — no polling needed, effectively instant.
- Webhooks are explicitly out of scope (would require a public server
  endpoint — real infra lift, not worth it for this project).

**AI Git tutor (Assistant tab)**
- Superseded the originally-planned treemap/churn/recency Codebase
  visualizer — dropped in favor of a Gemini-powered chat tab, per explicit
  product decision partway through the build. Lives in
  `com.gitquest.core.assistant` (`GeminiClient`, `GeminiConfig`,
  `RepoContextSummary`, `ChatMessage`, `MiniJson`), wired into
  `SandboxController`'s Assistant tab.
- **Repo-aware**: every message carries a live summary of the sandbox's
  actual state (current branch, HEAD, total commit count, the most recent
  commits, dirty file count, conflicted files) so answers can ground
  themselves in *this* repo, not just Git in the abstract.
- **File-aware**: whichever file is currently selected in the sidebar file
  tree gets its content attached (size-capped — large or binary files are
  skipped with a plain-language note instead of guessed at), so "explain
  this file" questions are answered from the real code.
- Built on the JDK's own `HttpClient` plus a small hand-rolled JSON
  reader/writer — no new Maven dependency for this feature.
- API key comes from a real `GEMINI_API_KEY` environment variable, or (as a
  local-dev convenience) a gitignored `.env` file in the project root —
  never hardcoded, never committed (see `.env.example` for the format).
- Resilient to transient failures: retries with exponential backoff on
  HTTP 503 (model overloaded), 429 (rate limited), and request timeouts —
  common in the days right after a new Gemini model ships. Model name is
  overridable via `GEMINI_MODEL` without a code change, since availability
  has proven to shift faster than general docs keep up with — trust the
  live API error message over docs if this breaks again.

**Code relationship graph (Java-scoped)**
- Tier 1 (build this): parse all `.java` files with JavaParser, index
  `class/interface name → declaring file`, then scan each file's type
  references (imports, extends/implements, field/param/return types,
  `new X()`) and draw a file→file edge when a reference resolves in the
  index. No classpath configuration needed.
- Tier 2 (stretch): method-level call resolution via `JavaSymbolSolver`
  with a configured `TypeSolver`. Real complexity jump — only attempt once
  Tier 1 is solid.
- Render as a node-link graph with **click-to-focus**: dim everything
  except the selected node's direct dependencies/dependents (1-hop) to
  avoid a spaghetti view.
- Known, acceptable limitations to surface in the UI, not hide: overloaded
  methods, polymorphism, reflection, and external library internals won't
  fully resolve. Frame it as "a map to help you navigate," not a compiler.

**Merge conflicts**
- Conflicted files get a distinct flag in the tree (separate from
  "modified").
- A merge that's conflicted is **pending**, not failed — represent it as a
  distinct in-progress state on the commit graph at the would-be merge
  commit's position, not as an error dialog.
- Three-way diff view per conflicted file (base / ours / theirs), driven
  directly by JGit's merge result data — don't re-derive this by parsing
  `<<<<<<<`/`=======`/`>>>>>>>` markers yourself.
- Resolution actions — **implemented**: keep-mine / keep-theirs buttons per
  conflicted file, then stage and commit to complete the merge. Manual
  per-region editing wasn't built as a separate in-app flow — consistent
  with "no built-in editor," a user who wants finer control just edits the
  file directly, then stages/commits the same way.
- Non-text conflicts (delete/modify, diverging renames) are an edge case —
  show a plain-language explanation rather than trying to diff them, at
  least for v1.

**Recovery**
- "Undo last command" — implemented as a reflog-backed reset, not a custom
  undo stack. Frames risky commands as safe to experiment with.

### 4.4 Home screen
Campaign progress (skill-tree thumbnail) + "enter sandbox" — kept simple
now that Daily Challenge is dropped.

### 4.5 Stretch goals

- Two-clone collaboration simulation (a local bare repo as a shared
  "remote," two working copies, to experience real divergence/conflicts) —
  **implemented.**

No further stretch goals are planned. A co-change graph and an
ownership/bus-factor view were considered and explicitly dropped from scope
— see Section 5.

## 5. Explicitly out of scope

- **Daily Challenge mode** — dropped. Do not build procedural puzzle
  generation, streaks, or a scoring/points system.
- Built-in code editor.
- Networked/online leaderboard.
- Webhook-based push notifications.
- Multi-language dependency parsing (Java only for the code-relationship
  graph).
- Full compiler-grade symbol resolution as an MVP requirement (Tier 2 is
  optional, not required).
- **Co-change graph** (files frequently modified in the same commit) —
  considered as a stretch goal, dropped. Do not build.
- **Ownership / bus-factor view** — considered as a stretch goal, dropped.
  Do not build.

## 6. Animation & visual design — this is now a primary priority

The whole pitch of this app is that Git becomes *visible*. Static
diagrams that just redraw on every state change will undersell it —
prioritize motion that shows *cause and effect*:

- **Commit graph**: new commits animate in (fade + slide into position on
  their lane) rather than popping in; branch/lane shifts should ease
  smoothly (`Interpolator.EASE_BOTH` or similar) rather than snapping.
- **Assistant chat**: new messages animate in rather than popping into the
  message list; keep the scroll position pinned to the newest message as
  replies arrive.
- **Code relationship graph**: a force-directed layout needs a continuous
  physics tick — use `AnimationTimer` (per-frame callback) rather than a
  discrete `Timeline`, since the layout is a running simulation, not a
  fixed set of keyframes.
- **Merge conflict pending state**: give it a distinct visual treatment
  (e.g. a subtle pulse) on the graph so it reads as "waiting on you," not
  static or broken.
- **Time-travel scrubber** (now the Code Graph's, since the treemap it
  originally scrubbed is gone): interpolate smoothly as the user drags, not
  in discrete jumps, so the sense of "moving through history" actually
  lands.

Treat every state change in the app as an opportunity to show *what
changed and why*, not just *what the new state is*.

## 7. Suggested build order

1. Core engine (`RepoStateModel`, `CommandExecutor`, `GoalValidator`) +
   Campaign Arcs 1–3 + basic Sandbox git playground with animated commit
   graph.
2. File watching (no editor) + working-tree "dirty" state visualization.
3. ~~Codebase visualizer: treemap + churn/recency overlays + time-travel
   scrubber.~~ Superseded — see the AI Git tutor (Assistant tab) under 4.3.
4. Campaign Arcs 4–6.
5. Merge conflict visualization (read-only three-way diff first, then
   resolution actions).
6. Code relationship graph, Tier 1, then Tier 2 if time allows.
7. Remote/push polling notification.
8. Stretch: two-clone collaboration simulation — done. No further stretch
   goals remain (see Section 5 for what was explicitly dropped).

Prioritize animation polish on whatever is built in each step rather than
rushing to the next feature with a static version — the visual quality is
core to the project's value proposition, not a final coat of paint.
