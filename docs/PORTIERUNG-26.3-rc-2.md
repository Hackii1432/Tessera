# Tessera / Sinopia – Portierung auf 26.3-rc-2

> Folgeänderung vom 13.09.2026: [Sinopia-Aufnahme und Bett-Korrektur](CHANGELOG-26.3-001-BEDS.md)
> ergänzt Patch 0036 und dokumentiert den neueren Build mit den lokalen Werten `001-alpha`.

Lokaler Arbeitsstand auf `ver/26.3.x`, ausgehend von Tessera `0b66675` und
Paper `38b0bfeb67855206ede9cb1df4f3354c4611c4c2`. Minecraft-Version,
Buildnummer und Channel wurden nicht geändert. MCC- und MVE-Code wurden
nicht bearbeitet. Es wurde kein Root-Commit oder Push ausgeführt.

## Umfang

- Alle 34 ursprünglich unter Sinopias `features_unapplied` abgelegten Features
  sind portiert und aktiviert. Dazu gehören Moonrise/Chunk-System, Starlight,
  Entity Activation Range, Netzwerk-, Tracker-, Pathfinder- und I/O-Optimierungen.
- Sinopia-Patch **0035** verbindet den bis Datenversion 4903 gepflegten
  DataConverter mit den originalen Vanilla-Datafixern für neuere Versionen.
  Das berücksichtigt unter anderem die neuen Blockstate-Schlüssel und die
  zusammengelegten Terrain-Chunk-Status. Daten werden nicht nur mit einer
  höheren Versionsnummer versehen; die Konvertierungen werden ausgeführt.
- Alle bisherigen **31 Folia-/Tessera-Minecraft-Patches** sind portiert.
  Die bisherigen Gamerule-, Enderperlen-, Locatorbar-, Golem-, Portal-,
  Respawn-, Runtime-World- und Snapshot-Patches bleiben Teil der Serie.
- Der zusätzliche Minecraft-Patch **0032** sichert weitere 26.3-Schnittstellen-
  und Owner-Thread-Anpassungen. Implementierungspatch **0023** enthält
  Regions-Regressionsprüfungen und den aktualisierten Konsolen-Testkonstruktor.
- Die zwölf API-Patches bleiben erhalten. Technische Paper-/Bukkit-Paketnamen
  werden aus Kompatibilitätsgründen nicht global umbenannt. Sinopia ist die
  integrierte Basis, Tessera bleibt der gebaute Server.

## Wichtige Portierungsdetails

Die neuen Vanilla-Befehlsrückmeldungen verwenden einen Tracker. Über Regionen
hinweg werden dafür Namen und Ergebniswerte gesammelt, keine später erneut
auszulesenden Live-Entities. Mutationen laufen auf dem jeweiligen Entity-Owner.
Entfernte oder abgelehnte Scheduler schließen ihren Anteil ohne Entity-Zugriff
ab. Spieler-Rückmeldungen werden auf den aktuellen Spieler-Owner zurückgeführt.
Wie bei asynchronen Folia-Befehlen üblich kann der unmittelbare Rückgabewert
eines regionsübergreifenden Befehls nicht auf alle Zieloperationen warten.

Der neue `/compute`-Pfad prüft Regionsbesitz vor Welt-/Entity-Zugriffen.
Fremde oder global ausgeführte Weltkontexte erhalten eine klare Fehlermeldung;
es findet kein synchrones Chunk-Laden oder globaler Weltzugriff statt.
Dies ist ausdrücklich keine beliebig regionsübergreifende `/compute`-Implementierung.

Die neue Terrain-Generierung bleibt erhalten; alte NOISE/SURFACE/CARVERS-
Status werden nicht wieder eingeführt. Gemeinsame Density-Puffer altern einmal
pro globalem Welt-Tick. Uhrzeitpakete berücksichtigen wieder die Clock-Daten
und Spielerzeit; Broadcasts prüfen beim Spieler-Callback die aktuelle Welt-
Clock, damit verspätete Nachrichten nicht auf einen gewechselten Kontext wirken.

## Prüfstand

- Sinopia: vollständige Kompilierung erfolgreich.
- Sinopia-Standardtests: **9.627 erfasst, 86 übersprungen, 0 Fehler**.
- Darin enthalten: 13 neue Datenmigrationsfälle einschließlich paralleler
  Vanilla-DFU-Vergleiche und vier Storage-/Bitpacking-Regressionsfälle.
- Tessera: vollständige Server-Kompilierung erfolgreich.
- Neun neue Regions-Regressionsfälle: erfolgreich, keine Fehler.
- Erneuter zentraler Gesamtbuild `buildTessera`: **BUILD SUCCESSFUL** in neun
  Minuten. Alle gespeicherten Tessera-Patches erneut angewendet; Tests,
  Checkstyle, verbotene API-Aufrufe und Paperclip-Verpackung erfolgreich geprüft.

| Tessera-Testmodul | Erfasste Tests | Übersprungen | Fehler/Fehlschläge |
| --- | ---: | ---: | ---: |
| API | 529 | 2 | 0 |
| Server | 9.744 | 87 | 0 |
| Checkstyle-Prüfmodul | 3 | 0 | 0 |
| Gesamt | 10.276 | 89 | 0 |

Die separaten Sinopia-Testzahlen werden nicht nochmals zu dieser Tabelle addiert.

Erzeugtes startbares Artefakt:
`build/libs/tessera-server-26.3-rc-2.build.018-stable.jar` (66.415.694 Bytes).

SHA-256: `88ff6b5535668328e0af44ea073f7459bc36c27eb71b3f8d49cd9c919a9d170a`.

Der Paperclip-Launcher wurde in einem isolierten Build-Ordner mit `--version`
geprüft, ausdrücklich auch mit **Java 25.0.3**, Exit-Code 0 und Ausgabe
`26.3-rc-2-018-0b66675`. Die gepackten Klassen verwenden Classfile-Version 69
(Java 25). Das Servermanifest enthält `Brand-Name: Tessera`,
`Brand-Id: mosaikdev:tessera` und `Git-Branch: ver/26.3.x`.

Die Standardkonfiguration schließt weiterhin den Test-Tag `Slow` aus.
Es wurde keine Produktionswelt geöffnet oder migriert und kein manueller
Mehrspieler-/Gameplay-Test durchgeführt. Erfolgreiche Unit-Tests sind keine
vollständige Laufzeitfreigabe für den umfangreichen 26.3-Port.
Die Launcher-Prüfung startet keine Spielwelt und ersetzt keinen Server-Smoke-Test.

## Anwenden und bauen

Im Tessera-Hauptverzeichnis mit **Java 25**:

```powershell
.\gradlew.bat buildTessera --no-daemon --console=plain
```

Dieser zentrale Aufruf erstellt die lokale Sinopia-Basis, wendet die komplette
Patchkette an, führt die Standardtests aus und baut Tessera. Ein zweites
Repository oder ein separat zu veröffentlichendes Sinopia-Artefakt ist nicht nötig.

Nur die Quellen vorbereiten:

```powershell
.\gradlew.bat applyAllPatches --no-daemon --console=plain
```

Eigene weitere Änderungen an generierten Quellen zuerst in der richtigen
Patchschicht sichern, bevor erneut angewendet wird. Die Anleitung dazu steht
in [SINOPIA-WORKFLOW.md](SINOPIA-WORKFLOW.md). Die alten 34 Konflikt-Patches
aus `build/port-26.3-original-features-unapplied/` sind nur eine Sicherung;
sie dürfen nicht zusätzlich zur aktiven Serie angewendet werden.
Der frühere Sinopia-Arbeitsbereich ist unter
`build/sinopia-workspace-porting-archive/` gesichert. Für neue Basisänderungen
erst `applySinopiaPatches` aufrufen; das erzeugt einen aktuellen Arbeitsbereich.

## Vor produktiver Nutzung noch abnehmen

Mit separaten Testwelten und vollständigen Backups prüfen:

1. Neustart und Laden migrierter 26.2-Chunks, Strukturen und Blockstates;
   neu generiertes Terrain einschließlich Chunk-Grenzen und Beleuchtung.
2. Zwei getrennte Regionen: Befehle, Teleports, Dimensionswechsel und Disconnects
   während ausstehender Scheduler-Aufgaben; End-Gateway mit neuer Zielinsel.
3. Tick-Befehl, Spielerzeit und neue Zeitmarker-/Clock-Befehle; Locatorbar und
   ihre Gamerule; Golem-Aggression nach Villager-Angriff und Spielertod.
4. Enderperlen-/Stasis-Chambers bei Regionswechsel, Dimensionswechsel,
   Rejoin und Serverneustart; Portal- und Respawn-Ereignisse.
5. Runtime-Strongholds und Block-Nachbearbeitung in parallelen Regionen;
   Runtime-Welt-Lifecycle, Online-Snapshot-Barrieren und Datensicherung.

Ein Dateiname mit dem vorhandenen Channel `stable` ist bei `26.3-rc-2`
keine Aussage über Produktionsreife. Die bestehende Restore-Freigabegrenze
wurde durch diese Portierung nicht aufgehoben.

## Lokale Protokolle

- `build/port-26.3-sinopia-all-tests.log`
- `build/port-26.3-sinopia-tests-3.log`
- `build/port-26.3-sinopia-capture-2.log`
- `build/port-26.3-tessera-compile-3.log`
- `build/port-26.3-tessera-rebuild-2.log`
- `build/port-26.3-tessera-region-tests-3.log`
- `build/port-26.3-tessera-build-2.log`
- `build/port-26.3-launcher-check/launcher-java25-version.log`

Diese Build-Dateien sind Nachweise, keine zusätzlich anzuwendenden Patches.
Die ursprünglichen Diagnoseberichte bleiben als historische Dokumentation erhalten.
