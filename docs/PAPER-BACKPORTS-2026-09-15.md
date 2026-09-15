# Selektive Paper-Backports vom 15. September 2026

## Basis und Umfang

Übernommen wurden die drei bewerteten Laufzeitkorrekturen aus dem geprüften
[Paper-Stand 2307f8f477997c9dbe17f789007c22356070e3a9](https://github.com/PaperMC/Paper/commit/2307f8f477997c9dbe17f789007c22356070e3a9).
Paper hat die Entwicklungshistorie neu zusammengefasst. Deshalb wurde der
Dateistand mit dem zuvor geprüften Stand `790e90fcf5bd7afcc1dc09febcb74ec4a75bca37`
verglichen, statt die abweichende Commit-Anzahl als neue Änderungen zu behandeln.

Dies ist **kein vollständiger Rebase**. Die lokale Sinopia-Basis, bestehende
Tessera-/Folia-Korrekturen, Minecraft `26.3-rc-3`, API `26.3` und Build `003`
bleiben erhalten. `paperRef` bleibt die ursprüngliche Importreferenz.
Lizenzen und bestehende Urheberhinweise werden nicht verändert.

## Übernommene Änderungen

### Tab-Vervollständigung

- Zu tief verschachtelte Tag-Eingaben erzeugen den vorgesehenen
  `TagParseCommandSyntaxException`-Fehlertyp und werden mit der Spam-Kick-Ursache
  zurückgewiesen. Gewöhnliche unvollständige Befehle führen nicht zum Kick.
- Falls der Thread-Stack bereits vor der unveränderten 512-Ebenen-Grenze erschöpft
  ist, fängt Tessera dies ausschließlich am Parser-Aufruf ab und verwirft ebenfalls
  die Anfrage. Andere Fehler aus Suggestion-Plugins werden dadurch nicht verdeckt.
- Auch von `AsyncTabCompleteEvent` behandelte Vorschläge werden geprüft.
  Die Prüfung nutzt denselben begrenzten Request-Pfad wie Server-Vorschläge;
  zusätzliche Suggestion-Provider werden dabei nicht erneut aufgerufen.
- Tessera führt das Parsen ausschließlich auf dem Besitzer-Thread des aktuellen
  Spielers aus. Der Entity-Scheduler folgt Regions- und Dimensionswechseln;
  der Listener aktualisiert den Spielerbezug beim Tick.
- Überholte, abgemeldete oder bereits beendete Requests werden abgebrochen.
  Retired-Scheduler und geschlossene Verbindungen führen nicht zu Weltzugriffen.
  Es gibt keine blockierende Warteoperation und keine Global-Thread-Weiterleitung.
- Die asynchronen Plugin-Events bleiben asynchron. Vor der Weiterleitung wird
  die Vorschlagsliste kopiert; vor dem Senden wird die Verbindung erneut geprüft.

### Benutzerdefinierte Chunk-Generatoren

- Noise-, Surface-/Bedrock- und Caves-Callbacks sind wieder in die Terrain-Pipeline
  eingebunden. Bei Noise-Generatoren folgt der jeweilige Plugin-Callback direkt
  auf seine Vanilla-Phase; die `shouldGenerate...`-Entscheidungen steuern weiterhin
  die entsprechende Vanilla-Phase, nicht den Plugin-Callback.
- Andere Generatoren sowie der Sonderfall ohne Vanilla-Terrainhöhe führen die
  vorgesehenen Plugin-Phasen ebenfalls aus.
- Moonrises vorhandener Inline-Generierungsexecutor bleibt erhalten: keine
  zusätzlichen Global-/Regions-Tasks und kein neuer Hintergrundexecutor für
  Chunk-Mutationen. Die bestehende Future-Kette des Generierungsauftrags bleibt bestehen.
- Tessera isoliert den historischen Zufallsgenerator pro Chunk bei unveränderter
  Seed-Formel. Nicht parallelfähige Legacy-Generatoren behalten ihre Serialisierung.
- Temporäre `CraftChunkData`-Ansichten werden auch bei Plugin-Ausnahmen freigegeben.

### Bett-Events

- Eintritts- und Fehler-Events bekommen die bereits ermittelte Regel des konkreten
  Betts statt pauschal der normalen Bettenregel der Dimension. Das berücksichtigt
  insbesondere Straw Beds und positionsabhängige Attribute.
- Die bisherigen dreiargumentigen internen Event-Brücken bleiben verfügbar und
  ermitteln bei Verwendung ebenfalls die Regel des tatsächlichen Betttyps.
- Bestehende Force-, Abbruch-, Rekursions- und Beobachter-Synchronisationskorrekturen
  bleiben bestehen. Die öffentliche Bukkit-API wurde nicht verändert.

## Versionierte Patchschichten

| Schicht | Dateien |
| --- | --- |
| Sinopia / Minecraft | `sinopia/paper-server/patches/features/0041-Guard-command-suggestion-tag-parsing.patch`, `0042-Restore-custom-terrain-generation-phases.patch`, `0043-Pass-the-actual-bed-rule-to-plugin-events.patch` |
| Sinopia / Implementierung | `CustomChunkGenerator.java`, `CraftEventFactory.java` und Bett-Regressionstests unter `sinopia/paper-server/src/` |
| Tessera / Folia-Minecraft | `folia-server/minecraft-patches/features/0038-Keep-suggestion-validation-on-the-current-player-region.patch` |
| Tessera / Folia-Implementierung | `folia-server/paper-patches/features/0028-Isolate-custom-generation-callback-state.patch`, `0029-Test-suggestion-ownership-and-custom-terrain-callbacks.patch` |

Die erzeugten Java-Dateien unter `paper-server/` und `folia-server/src/minecraft/`
sind keine weiteren Wartungsstellen. Nach erneutem Anwenden der Patches werden
sie aus den oben genannten versionierten Quellen wiederhergestellt.

## Anwenden und bauen

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel
```

Dieser Task bereitet Sinopia vor, wendet alle Patchschichten an und führt Tests
sowie den Build aus. Ausgabe bei unveränderter Build-Nummer:

```text
build/libs/tessera-server-26.3-rc-3.build.003-alpha.jar
```

Die vorherige lokale JAR wurde vor dem Verifikationslauf gesichert:

```text
build/backport-verification-20260915/tessera-server-26.3-rc-3.build.003-alpha.before.jar
```

## Testabdeckung und Grenzen

25 neue Regressionstestfälle ergänzen die vorhandene Testsuite:

- `SuggestionOwnershipRegressionTest`: zehn Fälle für beide Vorschlagswege,
  normale Syntaxfehler, Parser-Tiefengrenze, asynchrone Einreichung, Request-Bündelung,
  Retired-Scheduler, abgemeldete Spieler und aktualisierte Spieler-/Regionsbesitzer.
- `CustomTerrainRegressionTest`: elf Fälle für Phasenreihenfolge, deaktivierte
  Vanilla-Phasen, andere Delegates, leere Terrainhöhe, Plugin-Fehler, freigegebene
  Chunk-Ansichten und parallele Zufallszustände sowie Schreibzugriffe pro Chunk.
- `BedEventRuleRegressionTest`: vier Fälle für die übergebene Bettregel und die
  kompatiblen Event-Brücken für normale Betten beziehungsweise Straw Beds.

Zusammen mit den zwölf bestehenden `BedSleepRegressionTest`-Fällen wurden alle
37 gezielten Fälle erfolgreich ausgeführt. Der beim ersten Lauf gefundene
Stack-Overflow-Grenzfall wurde im Tessera-Patch abgesichert und danach erneut getestet.

Die Tests rufen die tatsächlichen Laufzeitmethoden auf, isolieren aber Welt-,
Netzwerk-, Plugin- und Scheduler-Grenzen mit Mocks. Die parallelen Generierungstests
nutzen echte getrennte Worker und überprüfen die Chunk-Schreibaufrufe.
Dies ersetzt keinen Live-Mehrspielertest mit euren Plugins und echten Regionswechseln.

## Abschließender Prüfstand

- `buildTessera --console=plain --max-workers=2 --no-parallel` wurde am
  15. September 2026 ohne Testfilter erfolgreich abgeschlossen: `BUILD SUCCESSFUL`.
- Alle Sinopia- und Tessera-Patchschichten wurden erneut angewendet. Beide
  generierten Git-Arbeitsverzeichnisse sind danach sauber; keine Korrektur hängt
  von nicht versionierten Handänderungen in generierten Java-Dateien ab.
- Die Server-XML-Berichte enthalten 9.948 Testfälle, davon 87 übersprungen,
  mit 0 Fehlern und 0 Fehlschlägen. Alle 25 neuen Fälle wurden ausgeführt.
- Die unveränderten API-Tests wurden von Gradle als aktuell bestätigt:
  529 Fälle, davon 2 übersprungen, ebenfalls ohne Fehler oder Fehlschläge.
  Die Build-Prüfungen einschließlich `scanJarForBadCalls` waren erfolgreich.
- Die fertige Paperclip-JAR wurde mit Java `25.0.3`, `-Xmx1G` und `--version`
  im vorhandenen isolierten Launcher-Testverzeichnis
  `build/rc3-build003-launcher-check-20260914-205354` geprüft.
  Ergebnis: `26.3-rc-3-003-4ac2731`, Exit-Code `0`; kein Weltstart.

Artefaktgröße: `67.654.304` Bytes.

SHA-256 der geprüften JAR:

```text
b58a17d969aeb63fb5b5dff148d7b9ff93d585993075a8ba0ea1d82ad13e161f
```
