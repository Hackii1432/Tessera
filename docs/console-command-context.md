# Console and RCON command world context

Tessera accepts console input before startup worlds are available. The raw
command text is placed in the existing FIFO queue, but a server-console world
context is resolved only when the Global Region processes that queue. At that
point the startup worlds and the default Paper respawn dimension are available.

This avoids retaining a `CommandSourceStack` whose level was `null` when stdin
was read. Commands entered before `Done` execute afterward in their original
order. No startup, region, or Global Region thread waits for a world or command
future.

Only a worldless source owned by the dedicated server console is refreshed.
Explicit dimension contexts, players, entities, command blocks, and plugin
sources retain their original world. RCON continues to construct an Overworld
source at dispatch time on the Global Region.

`Commands.executeCommandInContext` also rejects any remaining malformed
worldless source with a descriptive `IllegalStateException`; it no longer
dereferences `getLevel().getGameRules()` blindly.

The reproducible test is
`smoke-tests/console-command-context/run.ps1`. It covers stdin before and after
`Done`, FIFO order, `stop`, an explicit Overworld context, a plugin command, and
authenticated RCON.
