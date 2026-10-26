# Sinopia-Backport-Sicherungen

Die normalen Builds verwenden die bereits versionierten Quellen unter
`sinopia/` und die Tessera-Feature-Patches. Die hier abgelegten Git-Diffs sind
zusätzliche Sicherungen der Basisänderungen, **keine zweite automatisch
angewendete Patchschicht**. Nicht auf eine bereits aktualisierte Basis anwenden.

## Paper-Integration vom 26. Oktober 2026

`2026-10-26-paper-backports.patch` sichert die 68 geänderten/neuen Sinopia-Dateien
für Build 022: API, Adapter, Bibliothekskonfiguration, ATs und Baseline. Die nativen
Minecraft-/Folia-Anpassungen bleiben in Tesseras normaler Patchkette.
Ausgangsbasis ist der Sinopia-Inhalt von Tessera-Commit
`c1f9128c388cea89c3ef479dff6091fe780c685b`.

Nur auf dieser unveränderten Basis, nicht auf dem schon aktualisierten Checkout:

```powershell
git apply --check upstream-patches/sinopia/2026-10-26-paper-backports.patch
git apply upstream-patches/sinopia/2026-10-26-paper-backports.patch
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel
```

Vorwärts gegen einen separaten Index des Basiscommits und rückwärts gegen die
aktualisierten Quellen geprüft. Der normale Git-Index wurde dafür nicht ersetzt.
SHA-256: `7950643359c357d5917c23114f1304313c572df41c08bf2b4679f888a13f6c5f`.
Der [Buildbericht 022](../../docs/BUILD-26.3-022.md) beschreibt die komplette
Patchkette und Prüfung; dieser Basissicherungs-Patch allein ist keine Servermigration.

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
