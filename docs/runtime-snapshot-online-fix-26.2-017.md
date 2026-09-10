# Tessera 26.2-017 – Online-Snapshots ohne Tick-Deadlock

## Stand und Umfang

Korrektur auf Branch `ver/26.2.x`, Ausgangscommit `1a592ab`.
Minecraft/Paper-Referenz und Buildnummer bleiben unverändert. Die Buildnummer
017 allein unterscheidet deshalb das alte und das korrigierte JAR nicht.
Die Änderungen betreffen ausschließlich Snapshots, ihre internen
Scheduler-/Speicherpfade und Tests. MCC und die bestehenden Stasis-,
Enderperlen-, Portal- und Respawn-Patches bleiben unverändert.

Neue, aufeinander angewiesene Patches:

- `folia-server/paper-patches/features/0020-Fix-online-runtime-snapshot-phase-ordering.patch`
- `folia-server/minecraft-patches/features/0030-Drain-and-seal-runtime-snapshots-on-region-owners.patch`

Zusätzlich behebt
`folia-api/paper-patches/features/0010-Fix-snapshot-API-test-import-style.patch`
einen beim Gesamtbuild gefundenen bestehenden Import-Checkstyle-Verstoß im
Snapshot-API-Test. Er enthält keine API- oder Verhaltensänderung.

Die öffentliche Signatur `snapshotWorldsAsync(List<World>, Path)`,
`WorldSnapshotResult` und das Verzeichnislayout bleiben unverändert.

## Tatsächliche Ursache

Der bisherige Ablauf setzte die Quellwelten auf `SNAPSHOTTING`, wartete auf
Region-Barrieren und registrierte erst anschließend in `flushSnapshotPlayers`
einen internen Entity-Scheduler-Task mit Verzögerung `1L` pro Online-Spieler.
`EntityScheduler.executeTick()` wird aber durch den normalen Servertick
aufgerufen, den `TickRegions` während `SNAPSHOTTING` überspringt. Die interne
Registrierung gelang, der benötigte Tick kam nicht. Das Player-Future blieb
offen und erreichte nach 30 Sekunden den Timeout. `TimeoutException` besitzt
gewöhnlich keine eigene Message; daraus entstand der Text `failed: null`.

Die öffentliche Scheduler-Abweisung ist davon zu unterscheiden: Sie ist eine
gewollte Schutzmaßnahme während des Snapshots und bleibt erhalten.

Der echte Servertest zeigte außerdem: Die Zwischen-Tick-Verarbeitung des
EDF-Schedulers ist opportunistisch. Allein darauf darf auch eine interne
Region-Barriere nicht angewiesen sein. Eingefrorene Regionen verarbeiten ihre
internen Queues daher zusätzlich aus ihrem regulären Scheduler-Zeitfenster.
Die Spielsimulation und normale Entity-Scheduler-Ticks werden dabei nicht
weitergeschaltet.

## Alte und neue Reihenfolge

| Bisher | Korrigiert |
| --- | --- |
| Quellwelten einfrieren | Geladene Welten sperren und Snapshot-Aufnahme beginnen |
| Region-Barriere | Interne Player-Saves ohne Tick-Verzögerung registrieren |
| Player-Saves für einen unterdrückten Tick planen | Saves auf der jeweiligen Spielerregion ausführen/Retirement auflösen |
| 30 Sekunden auf offene Futures warten | Erst danach Region-Barrieren einreihen und abarbeiten |
| Fehler und Reaktivierung | Neue Region-Arbeit sperren und bereits laufende Owner-Arbeit vollständig verlassen lassen |
| — | Quellwelten und ausstehendes Datei-I/O flushen, Dateien kopieren/publizieren |
| — | Queued Saves aufräumen, Sperren öffnen, Welten reaktivieren und Queues wecken |

`SnapshotTaskGate` umfasst den vollständigen Regions-Besitz einschließlich
`tryMarkTicking`/`markNotTicking`: Auch Merge-/Split-Nacharbeiten müssen beendet
sein, bevor der vorhandene Snapshot-Speicherthread die Region serialisiert.
Die Wartephase verwendet Futures; es wird kein Regions- oder Global-Thread
mit `join()` auf einen anderen Owner blockiert. Ein bereits laufender Task
wird nicht gewaltsam abgebrochen. Das Queue-Drain-Budget begrenzt die Auswahl
weiterer Tasks, nicht die Laufzeit eines einzelnen Tasks.

Während der Dateikopie sind Region-Arbeit, weltbezogene globale Chunk-Tasks
und Spielerpaket-Verarbeitung der betroffenen Welten gesperrt. Globale
Serverkoordination bleibt verfügbar. Bereits laufendes Datei-I/O wird vor der
Kopie über die bestehenden Flush-Operationen abgeschlossen.

### Warum alle geladenen Welten kurz pausieren

`players/data`, `players/stats` und `players/advancements` stammen aus einem
gemeinsamen serverweiten Speicher. Spieler einer nicht angeforderten Welt
dürften diese Dateien sonst während der Kopie weiterhin überschreiben.
Deshalb werden alle geladenen Welten vorübergehend beteiligt; **kopiert werden
weiterhin ausschließlich die angeforderten Welten und der gemeinsame
Spielerspeicher**. Gleichzeitige Snapshots werden über diesen Speicher
serialisiert. Ein zwischen Aufnahme und Sperrerwerb geänderter Weltsatz führt
kontrolliert zu `SOURCE_BUSY`, nicht zu einer ungesicherten Kopie.

Die Pause umfasst auch Flush und Kopie und ist damit abhängig von Weltgröße
und Datenträger. Das ist kein Copy-on-write- oder unterbrechungsfreies Backup.
Direkte Dateimanipulationen durch externe Programme/Plugins werden durch
Regions-Sperren nicht verhindert und liegen außerhalb dieses Vertrags.

## Disconnect, Retirement und Fehler

- Der separate Save-Eintrag gehört zum übertragbaren `EntityScheduler` des
  Spielers; ausgeführt wird er mit dem aktuellen Handle und Owner-Prüfung.
- Ein ausstehender Save wird bei Scheduler-Retirement abgeschlossen. Ein
  bereits retired Scheduler liefert unmittelbar einen abgeschlossenen Future.
  Der normale Disconnect-Pfad speichert den Spieler vor dem Retirement; die
  nachfolgende Region-Barriere wartet auch dessen noch laufende Owner-Arbeit ab.
- Fehler des internen Player-Saves schließen dessen Future exceptional ab.
  Snapshot-spezifische Varianten von PlayerDataStorage, Statistiken und
  Advancements reichen auch sonst nur protokollierte Dateifehler weiter.
  Normale Saves behalten ihr bisheriges Verhalten. Persistenz- und
  `disable*Saving`-Einstellungen werden weiterhin respektiert.
- Nach Fehler/Timeout werden noch nicht ausgeführte interne Saves storniert,
  damit sie nicht später in einen anderen Snapshot schreiben. Bereits laufende
  synchrone Saves können nicht per Future-Abbruch unterbrochen werden.
- Die Reaktivierung wird nach jeder gestarteten Pipeline ausgeführt, auch bei
  teilweisem Prepare-Fehler. Ein Reaktivierungsfehler wird nicht als Erfolg
  verschwiegen, sondern als `CLEANUP_FAILED` gemeldet.
- Die bisherigen 30 Sekunden bleiben für Player-Save, Region-Barriere und
  abschließendes Owner-Drain erhalten. Flush/Kopie erhalten keinen unsicheren
  Abbruch-Timer, der Welten während eines noch laufenden Dateischreibers öffnen
  könnte.

Diagnosen nennen die Phase und behalten den ursprünglichen Fehler in der
Cause-Kette, beispielsweise:

```text
Runtime world snapshot failed: PLAYER_SAVE_TIMEOUT: TimeoutException
Runtime world snapshot failed: REGION_BARRIER_TIMEOUT: TimeoutException
Runtime world snapshot failed: REGION_SEAL_TIMEOUT: TimeoutException
PLAYER_SAVE_REJECTED: <UUID>
Runtime world snapshot failed: PLAYER_SAVE_FAILED: <Speicherfehler>
Runtime world snapshot failed: SNAPSHOT_COPY_FAILED: could not copy or publish snapshot
SNAPSHOT_RESUME_FAILED
```

## Geänderte Implementierungsdateien

Server-Patch: `CraftRuntimeWorldManager`, `EntityScheduler`, neue interne
`SnapshotPipeline` und `SnapshotTaskGate`; drei neue Testklassen.

Minecraft-Patch: `TickRegions`, `TickRegionScheduler`, `RegionizedServer`,
`ServerLevel`, `PlayerList`, `PlayerDataStorage`, `ServerStatsCounter` und
`PlayerAdvancements`.

Im Hauptrepository: Snapshot-Smoke-Modus des Testplugins, dessen neue
`RuntimeSnapshotSmoke`-Klasse, `smoke-tests/runtime-snapshot/` und diese
Dokumentation.

## Prüfungen

17 neue automatisierte Regressionstests prüfen 0/1/4 Player-Futures,
Save-vor-Barriere-vor-Kopie, kopierte Dateien, Retirement, Save-Exceptions,
stornierte Saves, öffentliche Scheduler-Abweisung, leere/wiederholte Gates,
gleichzeitige Owner-Arbeit, die drei Timeout-Phasen und Reaktivierung nach
Fehlern. Diese Unit-Tests simulieren Player-Futures; die tatsächliche
Mehrwelt-/Mehrregion-Ausführung prüft zusätzlich der Server-Smoke-Test.

Der Entwicklungs-JAR-Smoke-Test mit Java 25 und echten lokalen
Protokollverbindungen war erfolgreich:

| Fall | Gemessene Dauer |
| --- | ---: |
| Ohne Spieler | 371 ms |
| Ein Spieler | 267 ms |
| Direkt folgender Snapshot | 241 ms |
| Drei Spieler, getrennte Regionen, Overworld und Nether | 770 ms |
| Nach absichtlich ausgelöstem Kopierfehler | 398 ms |
| Nach Disconnect, zwei verbleibende Spieler | 360 ms |

Die ersten zwei Spieler-NBT-Dateien wurden dekomprimiert und inhaltlich
geprüft: `XpTotal=73` beziehungsweise `147`, Inventar mit sieben Diamanten.
Nach Abschluss lief eine normale Spieler-Scheduler-Aufgabe wieder.
Ein exaktes Disconnect-Rennen innerhalb des Freeze-Fensters wird durch die
Retirement-Unit-Tests abgedeckt; der reale Disconnect-Test prüft den
anschließenden Snapshot. Die Messwerte sind keine Performance-Garantie.

### Abschließende Ergebnisse (10. September 2026)

`applyAllPatches` wurde mit den beiden Implementierungspatches erfolgreich
ausgeführt. Der zusätzliche API-Testpatch wurde anschließend erzeugt und mit
`git apply --reverse --check` gegen den angewendeten Stand geprüft.

`test build createPaperclipJar`: **BUILD SUCCESSFUL**, keine Tests oder
Checkstyle-Tasks aus dem Build ausgeschlossen.

| Testsuite | Testfälle | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| API | 526 | 0 | 2 |
| Server (alle Testgruppen) | 9.220 | 0 | 22 |
| Checkstyle-Testprojekt | 3 | 0 | 0 |
| Gesamt | 9.749 | 0 | 24 |

Buildprotokoll: `build/snapshot-full-build-final.log`.

Auch das **finale Paperclip-JAR** wurde mit Java 25 erfolgreich getestet:
ohne Spieler 343 ms, ein Spieler 256 ms, unmittelbare Wiederholung 259 ms,
drei Spieler/mehrere Regionen/zwei Dimensionen 492 ms, nach injiziertem
Kopierfehler 450 ms, nach Disconnect 490 ms. Entity- und Region-Scheduler-
Abweisung während `SNAPSHOTTING` sowie erfolgreiche Owner-Aufgabe nach
Reaktivierung wurden bestätigt. Die NBT-Prüfung bestätigte zusätzlich alle
drei Spielerdateien, Overworld/Nether und die entfernte Position
`[4096.5, 90, 4096.5]`. Kein 30-Sekunden-Timeout und keine
Thread-Ownership-/ConcurrentModification-Fehler im erfolgreichen Lauf.

Ergebnis und vollständige Logs:
`build/snapshot-smoke-1789045713664/snapshot-smoke/result.txt` (`PASS`),
`build/snapshot-smoke-1789045713664/runner.log` und `logs/latest.log`.
Die Protokollclients stellen echte Serververbindungen her, rendern aber
keinen grafischen Minecraft-Client. Windows-OSHI-/JVM-Warnungen beim Start
sind unabhängig vom Snapshot-Test weiterhin vorhanden.

SHA-256 des geprüften Paperclip-JARs:

```text
de7c260180cd2f72f673378cfb41d78b080c1fc501fe20487289e9b62f0afeba
```

## Anwenden und bauen

Die beiden Implementierungspatches und den API-Testpatch in der vorhandenen Patchserie behalten.
Keine manuelle Änderung unter `folia-server/src/minecraft/java` nötig.
Im Repository mit Java 25 ausführen:

```powershell
.\gradlew.bat applyAllPatches
.\gradlew.bat test build createPaperclipJar
```

Lauffähiges Ergebnis:
`build/libs/tessera-server-26.2.build.017-stable.jar`.
Zum erneuten isolierten Online-Test:

```powershell
.\gradlew.bat :test-plugin:jar
node smoke-tests/runtime-snapshot/run.mjs "C:/Program Files/Java/jdk-25.0.3/bin/java.exe"
```

Der Test öffnet ausschließlich `127.0.0.1:25584`, verwendet eigene temporäre
Welten unter `build/snapshot-smoke-<Zeitstempel>`, verbindet drei Offline-
Protokollclients und beendet den Testserver danach. Er verändert keine
bestehenden Spielwelten. Portanpassung: Umgebungsvariable
`SNAPSHOT_SMOKE_PORT`. Logs, Snapshot-Dateien und `result.txt` bleiben zur
Nachprüfung erhalten; der Test löscht keine früheren Läufe.
