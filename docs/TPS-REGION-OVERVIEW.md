# Tessera 26.3-007 – TPS regions and player queries

The existing `/tps` command now combines a player-first summary with a clickable
overview of all loaded regions. All built-in output, help, hover text and diagnostic
messages in this view are English. No plugin or additional measurement task is needed.

## Commands

| Command | Output |
| --- | --- |
| `/tps` or `/tps list` | Your region first, followed by all other regions, with TPS, MSPT and visible players |
| `/tps region` | Details for your current region |
| `/tps region <ID>` | Details for an existing region |
| `/tps player <name>` | Target player's region and comparison with your own region |
| `/tps <name>` | Short form of the player lookup; use `player <name>` for names matching a subcommand |
| `/tps all` | Full regional histories, global tick metrics and server-wide chunk rates |
| `/tps server [count]` | Overview with an optional maximum number of displayed regions |
| `/tps help` | Command help |

The existing permission `bukkit.command.tps` is retained and checked. Grant it
through your permission plugin if ordinary players should have access. No new
public-by-default permission is introduced. Clicks use `/paper:tps`, the existing
namespaced registration, so a plugin's unqualified `/tps` alias does not hijack
the navigation. No click teleports a player or changes the server.

## Example

Illustrative values, not a measurement of this server:

```text
Tessera · Your region & overview
Target: 20.00 TPS
Your region is running normally.
3 regions · 15 s averages

▶ Region #7 · world · YOUR REGION
[OK] 20.00 TPS · 8.00 ms/tick · 1 visible player
Visible players: Alice (you)

Region #9 · world_nether
[LAG] 12.00 TPS · 80.00 ms/tick · 1 visible player
Visible players: Bob

Region #11 · world
[NO DATA] No measurements yet · 0 visible players
Visible players: none

Snapshot · Player assignments can change.
[My region] [Find player] [Overview] [All details] [Refresh]
```

Region titles and player names are clickable. `Find player` suggests the command
without immediately running it. `Refresh player` resolves the player again,
whereas `Refresh region` queries that region's ID again.

## Status colours and meaning

- Green `OK`: recent TPS at least 95% of the configured target, with headroom.
- Yellow `BUSY`: target broadly maintained, but average tick time uses at least
  75% of the target tick budget or measured utilisation reaches 85%.
- Red `LAG`: recent TPS below 95% of the configured target.
- Aqua `PAUSED` / `SPRINT`: intentional `/tick` mode; ordinary lag classification
  is disabled while that mode is active.
- Grey `NO DATA`: no usable measurements yet. This is not silently treated as 20 TPS.

Classification follows the configured target, not a hard-coded 20 TPS. The overview
uses existing 15-second reports. Details use existing 5-second, 15-second, 1-minute,
5-minute and 15-minute reports, including average tick time, minimum, median,
maximum, sample count and utilisation. During warm-up, these reports contain only
the samples already collected, not a fabricated full window. A recent tick-rate
change can temporarily affect the classification until old samples age out.

Utilisation is the scheduler's existing time-based metric, not process CPU usage.
Regional values do not measure the cost of an individual player or identify who
caused lag. Global tick and chunk load/generation rates are labelled separately.

## Region safety and privacy

- The command reads the existing published player-to-region association; it does
  not inspect another player's position or scan foreign entities/chunks.
- Permission and visibility checks for a player sender run on that player's
  owning entity thread. Off-owner command calls are scheduled through the existing
  entity scheduler; off-owner tab completion returns no suggestions.
- A topology lock is held only to copy references, not while formatting output or
  reading timing reports. No synchronous wait for another region is introduced.
- Region existence is rechecked when collecting. Every click requests a fresh
  snapshot. A removed/merged region or unloaded world produces a diagnostic;
  missing player associations during login/transfer produce a retry message.
- IDs refer to the runtime region IDs also used by the regionizer; they are not
  permanent world identifiers. A snapshot is not an atomic map of all regions.
- Player names, completions and displayed player counts respect the sender's
  Bukkit `canSee` visibility. Hidden and offline player lookups return the same
  message. Console senders can see all online players. Visibility plugins must
  actually publish their vanish state through Bukkit's visibility API.
- No coordinates or teleport buttons are shown. Aggregate region/chunk/entity
  statistics remain diagnostics, not a guarantee of concealing all server activity.
- Report generation is limited to one accepted query per player every 500 ms.
  No extra continuous sampling, tick task or public API is added.
- Long reports are sent as separate chat lines, each limited to 512 characters,
  while preserving colours and navigation. `/tps all` intentionally has no paging.

## Patch and build

The implementation is stored in Tessera's Minecraft feature patch `0043` and
server implementation/test patch `0032`. Generated working sources are not the
sole copy of the change. Sinopia, the plugin API version and MVE are unchanged.

Build from the repository root with JDK 25 and the checked-in Gradle wrapper:

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel
```

Output: `build/libs/tessera-server-26.3.build.007-alpha.jar`.

The complete `buildTessera` run succeeded for build 007, including patch reapplication,
compilation, tests, checks and Paperclip packaging. All 19 new regression tests passed.
The server suite recorded 10,031 tests with no failures/errors (87 skipped); the API
suite recorded 529 tests with no failures/errors (2 skipped).

The regression tests cover formatting, command routing, permissions, visibility,
missing data, custom tick rates, freeze/sprint, scheduling, published region
replacement and stale-region filtering. These are controlled automated tests;
a multiplayer live test with real region splits and a vanish plugin remains a
separate deployment check, not an implied result of unit tests.
