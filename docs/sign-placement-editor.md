# Schildeditor nach dem Platzieren (26.3-rc-2)

## Ursache und Korrektur

Beim Platzieren eines gewöhnlichen Schilds öffneten zwei Pfade den Editor:
Vanillas `SignBlock#setPlacedBy` und die nachgelagerte Paper-Behandlung in
`ItemStack#useOn`. Beim zweiten Öffnen ersetzt der 26.3-rc-2-Client das erste
Fenster. Dessen `removed()` sendet bereits ein `ServerboundSignUpdatePacket`.
Dieses erste Speichern verbraucht die Bearbeitungsfreigabe des Schilds, sodass
der anschließend eingegebene Text mit `just tried to change non-editable sign`
abgelehnt wird. Die spätere manuelle Bearbeitung setzt die Freigabe neu.

Tessera verschiebt die automatische Öffnung bei erfassten Blockplatzierungen
vollständig hinter das erfolgreiche `BlockPlaceEvent`:

- Während `captureBlockStates` aktiv ist, öffnet `SignBlock#setPlacedBy` nichts.
- Nach erlaubter Platzierung und Übernahme der Block-Entities ruft `ItemStack`
  denselben Vanilla-Platzierungspfad genau einmal auf.
- Gewachste Schilder und nicht editierbare Texte werden nach den bestehenden
  Vanilla-Prüfungen behandelt. Das gilt auch für Änderungen durch Plugins
  während des Platzierungsereignisses.
- Benutzerdefinierte Block-Entity-Daten verhindern nicht pauschal das Öffnen;
  entscheidend bleibt wie in Vanilla der tatsächliche Schildzustand.
- Ein abgebrochenes `BlockPlaceEvent` oder verweigertes `canBuild` öffnet keinen
  Editor. `PlayerOpenSignEvent` und `SignChangeEvent` bleiben abbrechbar.
- Direkte Platzierungen außerhalb der Blockerfassung behalten ihren bisherigen
  Pfad. Platzierungen ohne Spieler lesen dafür keine zusätzlichen Regionsdaten.

Die Prüfung der Bearbeiter-UUID, Entfernung und Wachsschicht sowie die Warnung
bei tatsächlich unzulässigen Änderungen bleiben erhalten. Es gibt keine neuen
Scheduler-Tasks, globalen Zugriffe oder Wartezeiten. Der Fix läuft im bestehenden
Platzierungsablauf auf dem zuständigen Regions-Thread. Sinopia und Plugins werden
nicht verändert.

## Patch und Build

In den Tessera-Commit gehören:

- `folia-server/minecraft-patches/features/0036-Open-placed-sign-editor-once.patch`
- `folia-server/paper-patches/features/0026-Test-sign-placement-editor.patch`
- Diese Dokumentation.

Die vollständigen generierten Java-Dateien müssen nicht separat aufgenommen
werden. Andere noch nicht gesicherte Änderungen an generierten Quellen müssen
wie bisher vor erneuter Patch-Anwendung exportiert werden.

Mit JDK 25 im Repository-Hauptverzeichnis:

```powershell
.\gradlew.bat buildTessera
```

Dieser Task wendet die Patchschichten an, führt Tests aus und baut die startbare
JAR unter `build/libs/tessera-server-26.3-rc-2.build.001-alpha.jar`.
Ein einzelnes manuelles `git apply` der Feature-Patches ist nicht erforderlich.

## Testumfang und abschließender Spieltest

Die Regressionstests verwenden echte `ItemStack`-, `BlockItem`-, `SignBlock`-
und `SignBlockEntity`-Methoden einschließlich Bearbeitungsfreigabe und ausgehender
Editor-Pakete. Weltablage, Platzierungskontext, Spielerzustand und Plugin-Dispatcher
sind isolierte Testdoubles; ein echter Client wird dabei nicht gestartet.

Auf einem Testserver mit unverändertem 26.3-rc-2-Client abschließend prüfen:

1. Stehendes Schild, Wandschild, Decken-Hängeschild und Wand-Hängeschild platzieren.
   Direkt im automatisch geöffneten Fenster Text eingeben und speichern.
2. Das Schild danach erneut bearbeiten; Vorder- und Rückseite prüfen.
3. In einem geschützten Bereich platzieren: Bei abgebrochener Platzierung darf
   kein Schildeditor geöffnet werden.
4. Mit den tatsächlich eingesetzten Plugins und zwei Spielern in getrennten
   Regionen wiederholen. Beide Spieler müssen unabhängig beschriften können.

Der automatisierte Test ersetzt diese Live-Prüfung der konkreten Plugin- und
Client-Kombination nicht.

## Automatisierte Prüfung

- JDK 25.0.3, Minecraft 26.3-rc-2.
- Alle 16 Fälle in `SignPlacementEditorTest` erfolgreich, keine übersprungen.
- Die gesamte `AllFeaturesTestSuite` ist mit dem Fix erfolgreich durchgelaufen.
- Die neuen Feature-Patches wurden mit den bestehenden Paperweight-Tasks
  exportiert; Änderungen an älteren Patch-Indexzeilen wurden nicht übernommen.
- Die erneute Anwendung beider Patches war konfliktfrei. Die Git-Inhaltshashes
  von `ItemStack`, `SignBlock` und `SignPlacementEditorTest` stimmen exakt mit dem
  getesteten Stand überein; beide generierten Arbeitskopien sind sauber.
- Der vorherige Bett-Fix und sein Testpatch sind ebenfalls inhaltlich unverändert.
- Keine Änderungen an Plugins oder an der versionierten Sinopia-Basis.
- Vollständiger Build am 14.09.2026:
  `.\gradlew.bat buildTessera --offline --no-daemon --console=plain`:
  **BUILD SUCCESSFUL** in 9 min 14 s, einschließlich `scanJarForBadCalls`.
- Server-Berichte: 9.839 erfasste Testfälle, 87 übersprungen, keine Fehler.
  API-Berichte: 529 erfasste Testfälle, 2 übersprungen, keine Fehler.
  Die 16 neuen Schildfälle sind in der Server-Zahl enthalten.
- Die neue Capture-Prüfung ist auch in der kompilierten Serverklasse vorhanden.
- Ein Live-Spieltest mit euren konkreten Plugins wurde nicht durchgeführt.
