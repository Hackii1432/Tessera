# Windows Paperclip long-path diagnostics

Tessera's distributable server JAR starts through Paperclip. Paperclip extracts
and patches the Mojang server before Tessera's server main class is loaded.
Consequently, an exception from `io.papermc.paperclip.Paperclip.extractFiles`
or Java ZipFS during that phase cannot be repaired safely in Tessera's world or
server lifecycle code.

Tessera deliberately does not relocate Paperclip caches, invent a `\\?\` path
conversion, or retry extraction into a different directory. Such workarounds
would hide the effective storage location and could introduce a second cache
with different locking and cleanup behavior.

The reproducible Windows test is:

```powershell
.\smoke-tests\windows-paperclip-long-path\run.ps1 `
    -JarPath .\folia-server\build\libs\tessera-server-26.2.build-010-stable.jar
```

It runs the assembled Paperclip JAR in patch-only mode in three configurations:

1. a short control path with an empty cache;
2. a working directory longer than 180 characters;
3. an explicit `bundlerRepoDir` longer than 180 characters.

Only the Mojang input JAR from the control run is seeded into the long-path
cases. Paperclip must perform its own extraction, ZipFS close, and patch
application. The test then renames the cached Mojang JAR to prove that no
Windows file handle remains open.

Every case has a bounded timeout and emits progress every five seconds. On
failure the script reports the absolute working path, repository path, expected
Mojang cache path and their lengths, plus Java, Windows,
`LongPathsEnabled`, and whether an `AccessDeniedException` occurred. These
details should accompany an upstream Paperclip or JDK ZipFS report.

The test retains a uniquely named artifact directory for every invocation.
This is intentional: Windows PowerShell 5.1 may itself fail to recursively
remove paths above its legacy limit when `LongPathsEnabled` is disabled. That
shell limitation must not be misreported as a Paperclip extraction failure.

The path length alone does not identify the owner of a failure. A failure in
Paperclip's extraction stack before Tessera starts is an upstream bootstrap
boundary; a later failure in Tessera's own storage lifecycle remains a Tessera
issue.
