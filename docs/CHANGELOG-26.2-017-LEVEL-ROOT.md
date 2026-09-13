# Tessera 26.2-017 – Level-Root-Sicherung für Runtime-Snapshots

Nachtrag zu Tessera 26.2-017 für Minecraft 26.2 · Java 25

## Neu

- Runtime-World-Snapshots sichern jetzt zusätzlich den gemeinsamen Level-Hauptordner unter `level/`.
- Enthalten sind `level.dat`, `level.dat_old` (falls vorhanden), `data/` und `datapacks/`.
- Vorhandene `generated/`- und `resourcepacks/`-Verzeichnisse sowie sichere reguläre Root-Dateien wie `icon.png` werden ebenfalls übernommen.
- Die Quellpfade werden über die Server-API ermittelt; abweichende Weltordnernamen werden unterstützt.

## Behoben und verbessert

- Bisher fehlende gemeinsame Level-Metadaten und globale SavedData werden nun zusammen mit Dimensionen und Spielerdaten gesichert.
- Globale Daten werden auf ihrem zuständigen Thread erfasst und vor der Kopie vollständig geschrieben. Konkurrierende globale Speichervorgänge werden während dieser Phase zurückgestellt.
- Alle drei Ausgabeteile werden im selben geschützten Snapshot-Vorgang vorbereitet und erst nach vollständiger Kopie veröffentlicht.
- Bei einem Veröffentlichungsfehler werden bereits veröffentlichte Teile dieses Aufrufs zurückgenommen. Unvollständige Snapshots werden nicht als Erfolg gemeldet.
- Fehlende `level.dat`, bestehende Ausgabeziele und überlappende Quell-/Zielpfade führen zu einem kontrollierten Fehler.
- Symlinks, Junctions und Spezialdateien werden nicht verfolgt. `session.lock`, `dimensions/` und `players/` werden nicht nach `level/` kopiert.
- Vom Aufrufer verwaltete Inhalte wie `runtime/` bleiben unangetastet.

## Kompatibilität

- Die Signatur von `snapshotWorldsAsync(List<World>, Path)` bleibt unverändert.
- Snapshots mit verbundenen Spielern sowie die bestehende Owner-Thread-, Save-Queue- und Regions-Gate-Logik bleiben erhalten.
- Die nummerierten Dimensionsordner unter `worlds/` werden nicht umbenannt.
- Bestehende Stasis-, Enderperlen-, Portal- und Respawn-Anpassungen bleiben unverändert. MVE und MCC wurden nicht verändert.

## Neues Snapshot-Layout

```text
snapshotPath/
├── level/
│   ├── level.dat
│   ├── level.dat_old       # falls vorhanden
│   ├── data/
│   ├── datapacks/
│   ├── generated/         # falls vorhanden
│   └── resourcepacks/     # falls vorhanden
├── worlds/
│   ├── 0/
│   ├── 1/
│   └── ...
└── players/
    ├── data/
    ├── stats/
    └── advancements/
```

## Prüfung und Hinweise

- Geprüfter Implementierungsstand vom 10. September 2026: Patch-Neuaufbau und vollständiger Build erfolgreich.
- 9.776 automatisierte Testfälle, keine Fehler, 25 übersprungen; davon ein neuer Symlink-Test wegen fehlender Windows-Berechtigung. Der echte Windows-Junction-Test wurde bestanden.
- Zwei aufeinanderfolgende vollständige Online-Smoke-Tests mit 0, 1 und 3 Spielern erfolgreich, einschließlich getrennter Regionen, Dimensionswechsel, Wiederholung und Fehler-/Resume-Verhalten.
- Level- und Spieler-NBT wurden inhaltlich auf aktuelle Daten geprüft. Ein vorheriger einmaliger Dateisystemfehler wurde vollständig zurückgerollt und ist im technischen Bericht dokumentiert.

Backup-Programme müssen das erfolgreiche Snapshot-Ergebnis abwarten und den neuen `level/`-Teil berücksichtigen. Die drei Verzeichnis-Moves sind keine absturzatomare Dateisystemtransaktion. Die beteiligten Welten pausieren während Flush und Kopie; externe direkte Dateizugriffe werden nicht angehalten. Misslingt die Bereinigung, wird `CLEANUP_FAILED` zurückgegeben.

Die Erweiterung liegt als zusätzliche Patches vor: API `0011`, Server `0021` und Minecraft `0031`. Die Buildnummer bleibt `017`.

[Technischer Bericht mit Patchdateien, Build-Anleitung, Testprotokollen und JAR-Prüfsumme](runtime-snapshot-level-root-26.2-017.md)
