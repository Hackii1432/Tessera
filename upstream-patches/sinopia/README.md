# Sinopia-Backport-Sicherungen

Die normalen Builds verwenden die bereits versionierten Quellen unter
`sinopia/` und die Tessera-Feature-Patches. Die hier abgelegten Git-Diffs sind
zusätzliche Sicherungen der Basisänderungen, **keine zweite automatisch
angewendete Patchschicht**. Nicht auf eine bereits aktualisierte Basis anwenden.

## Paper-Integration vom 11. Oktober 2026

`2026-10-11-paper-backports.patch` sichert alle 43 geänderten beziehungsweise
neuen Sinopia-Dateien dieser Integration, einschließlich öffentlicher APIs,
Adapter, kleiner Minecraft-Quellpatches und der Baseline-Dokumentation.
Ausgangsbasis ist der Sinopia-Inhalt von Tessera-Commit
`c159a4e973747dbe787648d569826124a7b3c308`.

Nur zur Rekonstruktion auf genau dieser **unveränderten Sinopia-Basis**, vom
Tessera-Root aus:

```powershell
git apply --check upstream-patches/sinopia/2026-10-11-paper-backports.patch
git apply upstream-patches/sinopia/2026-10-11-paper-backports.patch
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel
```

Die separat versionierten Minecraft-/Server-/API-Feature-Patches müssen ebenfalls
im Repository vorhanden sein. Der normale Build wendet sie an. Diese Sicherung
allein ist kein vollständiger Tessera-Quellstand und keine Migration beliebiger
neuer Paper-Versionen. Bei einer schon integrierten Basis muss stattdessen
`git apply --reverse --check` erfolgreich sein; dann **nicht** erneut anwenden.

Vorwärts gegen den unveränderten Sinopia-Index und rückwärts gegen den finalen
Arbeitsbaum geprüft. SHA-256:
`d945a265e2305b8a1cf3c1d39e22bad99ca25c9fa83c02f0ef649d3c59b2f5c0`.
Umfang und weitere Patch-Hashes: [Buildbericht 019](../../docs/BUILD-26.3-019.md).
