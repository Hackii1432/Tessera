# Build 017 – TPS-Kapazität und aktueller Pregen-Status

Stand: 07.10.2026. Nachtrag zu Tessera `26.3-017-beta`, Minecraft/API `26.3`,
Java 25. Die Buildnummer bleibt unverändert. Keine Änderung an Sinopia,
MCC-/MVE-Produktcode, Abhängigkeiten oder dem Generierungsverfahren.

Dieser Nachweis gehört zum unten genannten JAR-Hash. Die danach gebaute 017-JAR
mit korrigiertem Windows-Konsolenshutdown steht im
[Shutdown-Nachtrag](CONSOLE-SHUTDOWN-2026-10-07.md). Die native Pregen-Abnahme hier
ist ein historisches Ergebnis und wurde nicht auf der neuen Datei wiederholt.

## TPS-Übersicht

`/tps`, `/tps list` und `/tps server [count]` zeigen wieder die gemessene
Gesamtauslastung und die maximal verfügbare Kapazität. Beispiel:

```text
Utilisation (15 s): 180.00% / 400.00% max · Tick threads: 4
```

100 % entsprechen einem tatsächlich verfügbaren Scheduler-Tickthread. Der
Zähler summiert vorhandene gültige 15-Sekunden-Berichte aller Regionen und des
Global-Ticks, auch wenn die Ansicht weniger Regionen auflistet. Das ist Tickzeit,
keine CPU-Auslastung und keine Kapazität der Chunk-Worker. Fehlende Berichte
werden als fehlend beziehungsweise `Partial samples` gekennzeichnet, nicht als
erfundene Nullwerte. Freeze-/Sprint-Hinweise bleiben erhalten. Die Darstellung
verwendet ausschließlich den bestehenden Snapshot, keine neuen Weltzugriffe.

## Pregen-Status und Aufbewahrung

`/pregen status` und der weiterhin akzeptierte Alias `/pregen status all` zeigen
nur den neuesten Auftrag, unabhängig von seinem Zustand. Die englische Ausgabe
hat farbige Zustände, Fortschrittsbalken und passende klickbare Aktionen. Beispiel:

```text
Tessera · Pregeneration #9 · minecraft:overworld
[RUNNING] · Mode: BALANCED · Simulation: FROZEN
Progress: [█████░░░░░░░░░░░░░░░] 25.00% · 250 / 1,000 chunks
Rate: 10.0 chunks/s · ETA: 1m 15s · In flight: 4
[Refresh] [Pause] [Cancel]
```

Zusätzliche Diagnose erscheint nur bei Bedarf. Der Fortschritts-Hover trennt neu
generierte und schon fertige Chunks. Abgeschlossene/abgebrochene Aufträge bieten
keine ungültigen Pause-/Resume-/Cancel-Aktionen. Cancel schlägt den Befehl vor;
Refresh fragt den jeweils neuesten Auftrag über `/tessera:pregen status` ab.

Ältere abgeschlossene/abgebrochene JSON-Checkpoints werden auf dem bestehenden
Metadaten-Worker entfernt, nachdem ein neuerer Checkpoint dauerhaft publiziert
wurde. Abbruch muss zuvor alle zugelassenen Aufgaben drainen. Auch alte
Checkpoints werden beim Start bereinigt. Verspätete Schreibvorgänge dürfen
entfernte Historie nicht wiederherstellen oder einen fertigen Auftrag auf einen
alten laufenden Stand zurücksetzen. Bereinigungsfehler werden gedrosselt gemeldet.

Ältere **unfertige**, pausierte oder wiederaufnehmbare fehlgeschlagene Aufträge
bleiben intern und per expliziter ID steuerbar. Parallele Arbeit wird nicht
still abgebrochen oder um ihre Neustart-Wiederaufnahme gebracht. Generierte
Chunks, Regiondateien, Welten und Spielerstände werden nicht gelöscht.

## Quellstand und Patches

- Branch: `ver/26.3.x`; Root-Basiscommit
  `752dced66a794e6617406c7eca131ae86bef658d` **plus diese lokalen Änderungen**.
  Kein Commit oder Push im Hauptrepository.
- Sinopia unverändert: `cebdb10d9418432a18b920f7f2e902a8d03a43f4`.
- [Minecraft-Patch 0057](../folia-server/minecraft-patches/features/0057-Restore-TPS-utilisation-capacity-and-retain-current-.patch):
  `671d6f19954deeba5e7df9fbcca8c4d3380abe888a30ec18557045cd60d94f27`.
- [Server-/Testpatch 0046](../folia-server/paper-patches/features/0046-Simplify-latest-pregeneration-status-and-test-TPS-po.patch):
  `91d54029869f07e557e46f54662a001450cd31fffe253b6964f743114d7e0134`.
- Generierte HEADs nach erneuter Anwendung:
  Minecraft `cb379cdc540fbf17e6268d6f90d3c374d9daa48e`,
  Server `df3f72d618cd2a555e5f0006c9a7d3506de5bbd9`; beide sauber.

Die Änderungen wurden über die bestehenden Feature-Rebuild-Aufgaben exportiert
und mit `buildTessera` erneut angewendet. Nur generierte Quellen zu ändern reicht
nicht; die beiden Patches sind die dauerhafte Implementierung.

## Build und native Prüfung

```powershell
.\gradlew.bat buildTessera --console=plain --no-configuration-cache --max-workers=2 --no-parallel
node smoke-tests/native-pregeneration/run.mjs 'C:\Program Files\Java\jdk-25.0.3\bin\java.exe' build/libs/tessera-server-26.3.build.017-beta.jar
```

Vollständiger Build: **BUILD SUCCESSFUL**, 8 Minuten 18 Sekunden, Java 25.0.3,
Windows 11, Gradle 9.8.0. Server: **10.261 Fälle**, 87 bestehende Skips,
0 Fehler/Failures. API: **529 Fälle**, 2 bestehende Skips, 0 Fehler/Failures.
Die 20 neuen Regressionen decken TPS-Kapazität sowie Status/Retention ab.
Vorhandene Qualitätsprüfungen bleiben aktiv. API-Format/Metadaten und 51 Links
in 19 Artikeln geprüft; 35 Validator-Tests bestanden und 10 vollständige
Java-Beispiele gegen die tatsächliche API kompiliert.

Native Fixture `native-pregeneration-1791332255715`, Loopback-Port `59665`, vier
Tickthreads, zwei verbundene Protokollclients, zwei Serverphasen auf derselben JAR:

| Prüfung | Ergebnis |
| --- | --- |
| Echte Statusbefehle | Neuester abgebrochener Auftrag #5; `status`/`all` identisch, fünf Zeilen, ältere fertige ID abgewiesen |
| Retention | Zunächst nur `5.json`; später bleiben unfertige #10/#11 für Wiederaufnahme erhalten; am Ende nur neuester abgebrochener #11 |
| Echte TPS-Abfrage | `/paper:tps server 1`: 400 % Kapazität bei vier tatsächlichen Tickthreads während Freeze |
| Bestehende Pregen-Pfade | Rechte/Dimensionen, parallele Regionen/Dimensionen bei Freeze, Ticketfreigabe, vorhandene FULL-Chunks, Pause/Resume/Cancel, eigener Generator, Runtime-Unload und Snapshot bestanden |
| Fehlerfälle | Native No-Save- und Checkpoint-Publikationsfehler gezielt ausgelöst; danach erfolgreiche Wiederaufnahme, keine neue Arbeit bei fehlgeschlagener Publikation |
| Neustart | Aktive Nether-Arbeit sauber gedraint; pausiert wiedergefunden; #10 mit 1.089 Zielchunks erfolgreich wiederaufgenommen, zwei bereits gespeicherte Chunks erkannt |
| Clients/Prozess | Je Phase zwei Logins, null Kicks und null Quits vor Ergebnis; beide Exitcodes 0, keine Region-/Ownership-Ausnahme |

Der erste native Versuch scheiterte an einem nicht zugelassenen generischen
Befehlssender der Testfixture. Der vorhandene `FeedbackForwardingSender` ersetzt
ihn; das isolierte Testplugin wurde separat neu kompiliert. Dispatcher,
Server-JAR und Assertions wurden nicht gelockert. Der erneute vollständige
native Lauf einschließlich Recovery ist bestanden.

## Ausführbare Datei und Nachweis

`build/libs/tessera-server-26.3.build.017-beta.jar`, **55.618.208 Bytes**, SHA-256:

```text
037a380de916b2c82d1a4c701fe4d679661e74ccc0550e42a003fa9b2eb223c9
```

Der [kompakte Nachweis](test-evidence/26.3-017/tps-pregen-status.json) hält Hash,
Quellstand, Testzahlen und tatsächliche Ergebnisse fest. Die unveränderten
nativen JSON-/Log-/Prüftext-Dateien liegen unter
`build/reports/native-fixtures/native-pregeneration-1791332255715/`; die erste
fehlgeschlagene Fixture ist daneben separat archiviert und kein Erfolgsnachweis.
Nach Marker-, Pfad- und Archiv-Hash-Prüfung wurden nur die beiden selbst
angelegten, beendeten Testserver mit ihren wegwerfbaren Welten/Serverkopien
entfernt: insgesamt 433.835.633 Bytes, rund 414 MiB. Die Runner können sie neu
erzeugen; produktive Welten, vorhandene Saves und die finale JAR bleiben erhalten.

Protokollclients rendern keine Minecraft-GUI: Farben/Klickereignisse sind durch
Komponenten und Regressionen geprüft, nicht visuell mit einem Vanilla-Client.
Kein großer Durchsatzbenchmark, kein Hard-Power-Loss-/Disk-Full-Test und keine
erneute MCC-/MVE-/TAB-/LuckPerms-Integration. Der beabsichtigte Neustart trennt
die beiden Testphasen; er wird nicht als Seamless-Restore ausgegeben. Die
bestehenden Windows-OSHI-/JVM-Warnungen bleiben unverändert.

Die NBT-/Spieler-Restore-Nachweise vom 06.10. gehören zum damaligen JAR-Hash im
[ursprünglichen Buildbericht](BUILD-26.3-017.md), nicht zu diesem nativen Pregen-Lauf.
