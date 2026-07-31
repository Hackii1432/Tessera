# Region-safe Bukkit scoreboards

Tessera implements the Bukkit scoreboard surface needed by MCC without
reactivating Folia's old unsafe code path. Availability is explicit:

```java
boolean enabled =
    Bukkit.getTesseraCapabilities().supportsRegionSafeScoreboards();
```

A common Paper/Folia/Tessera JAR should use the reflective capability selection
shown in [runtime-world-lifecycle.md](runtime-world-lifecycle.md). If the
capability is absent, MCC can disable only its Sidebar module; BossBars,
ActionBars, timers, and challenge logic remain independent.

## Ownership and publication

Every Bukkit scoreboard is an independent model. `getNewScoreboard()` creates a
new `ServerScoreboard` rather than returning or aliasing the main scoreboard.
The model's monitor is its single serialization domain for objectives, teams,
scores, and display slots. Mutations are short, in-memory operations; region
threads never wait on the global-region scheduler, chunk I/O, or a player
connection to mutate a model.

For client publication, the model emits immutable packet lists carrying a
monotonic revision. Each assigned player has a separate subscriber and pending
queue. Updates are transferred to that player's entity scheduler and the
connection is touched only after Tessera verifies the current entity owner.
The delivery callback also verifies that the player is still subscribed to the
same scoreboard.

Mutations produced during one update burst are coalesced until the next player
tick and delivered as one versioned batch. Objective-title changes, team-prefix
changes, score changes, and resets therefore arrive together without rebuilding
the entire board. A complete immutable snapshot is generated only for join,
rejoin, or an actual scoreboard switch.

No broadcast enumerates all online players and dereferences their region state.
Different player boards have different models, revisions, queues, and
subscribers, so concurrent MAB arenas cannot overwrite one another.

## Supported Bukkit surface

The regionsafe implementation covers:

- `Bukkit.getScoreboardManager()`
- `ScoreboardManager#getMainScoreboard()` and `getNewScoreboard()`
- `Player#getScoreboard()` and `setScoreboard(...)`
- Adventure objective display names and team prefixes/suffixes
- objective registration, Sidebar display slots, scores, and resets
- team registration and entry add/remove
- objective/team removal and restoring a previous or main scoreboard

Entity scoreboard tags are deliberately separate. They are entity state:
`getScoreboardTags`, `addScoreboardTag`, and `removeScoreboardTag` enforce the
entity's current tick-thread owner, and reads return an immutable copy.

## Player lifecycle

Join subscribes the current board and schedules its full snapshot through the
new entity owner. Quit retires and removes the subscription. A scheduled update
whose player retires is discarded without touching its connection. Respawn,
world changes, cross-region teleports, and cross-world arena teleports keep the
subscription keyed by player UUID; actual packet application follows the
current entity scheduler.

Switching boards first produces an immutable removal snapshot for the old
board, publishes the override on the entity thread, subscribes the new board,
and then sends its full snapshot. Setting the main scoreboard clears the
override. This also allows a plugin to retain and later restore a scoreboard
owned by a second plugin.

On plugin disable, cancel the plugin's update producers and assign the saved
previous board (or main board) to each live player. If a player quits during
that handoff, Tessera retires the task and drops it.

## MCC Sidebar example

```java
ScoreboardManager manager = Bukkit.getScoreboardManager();
Scoreboard previous = player.getScoreboard();
Scoreboard sidebar = manager.getNewScoreboard();

Objective objective = sidebar.registerNewObjective(
    "mcc",
    Criteria.DUMMY,
    Component.text("Monster Army Battle")
);
objective.setDisplaySlot(DisplaySlot.SIDEBAR);

for (int line = 0; line < 15; ++line) {
    String entry = "mcc_line_" + line;
    Team team = sidebar.registerNewTeam("mcc_" + line);
    team.addEntry(entry);
    team.prefix(Component.text(renderLine(line)));
    objective.getScore(entry).setScore(15 - line);
}

// Safe from any caller; packet application is transferred to the entity owner.
player.setScoreboard(sidebar);

// A timer update changes deltas only. A burst is coalesced for the next
// entity tick, so update all lines before returning from this callback.
for (int line = 0; line < 15; ++line) {
    sidebar.getTeam("mcc_" + line).prefix(Component.text(renderLine(line)));
}
sidebar.resetScores("obsolete_entry");

// End of round/plugin disable:
player.setScoreboard(previous != null ? previous : manager.getMainScoreboard());
```

Keep model mutation bursts finite and do not perform blocking work between
individual line changes. When a plugin needs to read or mutate entity tags,
schedule that separate work through `player.getScheduler()` (or the owning
entity's scheduler).

## Automated coverage

The patch suite includes:

- a complete Adventure Sidebar with team prefix, entry, score, reset, and
  display-slot removal;
- 50 scoreboards updated concurrently, each with 15 unique lines;
- assertions that each player's title, teams, entries, prefixes, and scores
  remain isolated.

The runtime smoke plugin additionally exercises assignment, rapid updates,
world transfer, restoration, and cleanup with a real server/player when an
interactive test player is present.

