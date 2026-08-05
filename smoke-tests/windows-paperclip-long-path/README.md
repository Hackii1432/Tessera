# Windows Paperclip long-path smoke test

This test exercises the actually assembled Tessera Paperclip JAR before the
Tessera server main class starts. It runs Paperclip in `patchonly` mode with:

1. a short working directory and fresh cache;
2. a working directory longer than 180 characters;
3. a separate `bundlerRepoDir` longer than 180 characters.

Only the downloaded Mojang input JAR is seeded into the two long-path cases.
Paperclip must still open and close Java ZipFS, extract libraries, and apply the
server patch in each location. After exit, the script renames the cached Mojang
JAR to verify that no handle remains open.

Run on Windows with Java 25:

```powershell
.\smoke-tests\windows-paperclip-long-path\run.ps1 `
    -JarPath .\folia-server\build\libs\tessera-server-26.2.build-010-stable.jar
```

The default limits are 190 path characters and 180 seconds per Paperclip
process. Progress is printed every five seconds. A failure reports the absolute
working, repository, and expected Mojang paths with their character counts,
Java/Windows versions, `LongPathsEnabled`, and whether the bootstrap exception
was an `AccessDeniedException`.

Each invocation uses a unique directory below `build/runs` and retains it.
Windows PowerShell 5.1 cannot reliably remove the generated paths when they
cross its legacy path limit, especially when `LongPathsEnabled` is disabled.
Retention keeps shell cleanup limitations separate from the Paperclip result.

This is intentionally not implemented as a Tessera server-core workaround.
Paperclip is the JAR main class and performs the failing extraction before
Tessera code is loaded. A confirmed ZipFS/Paperclip defect should be fixed
upstream or by updating the Paperclip dependency; Tessera must not silently
relocate caches or guess a `\\?\` path transformation.
