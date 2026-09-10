# Tessera 26.2-017 – Gemeinsamer Level-Root in Online-Snapshots

## Umfang und Ursache

Erweiterung auf Branch `ver/26.2.x`, Ausgangscommit `e2af586`.
Minecraft-Version, Paper-Referenz und Buildnummer bleiben unverändert.
Ausschließlich das Tessera-Repository wurde geändert; MVE und MCC nicht.
Die bisherigen Stasis-, Enderperlen-, Portal-, Respawn- und Online-Snapshot-
Patches bleiben erhalten.

Der bisherige `SnapshotPlan` kannte nur die angeforderten Dimensionspfade und
die drei gemeinsamen Player-Storage-Pfade. Seine Kopier-/Publikationsphase
erzeugte entsprechend nur `worlds/` und `players/`. Das über
`MinecraftServer.getWorldPath(LevelResource.ROOT)` erreichbare gemeinsame
Level-Hauptverzeichnis war keine Quelle. Zusätzlich flusht
`ServerLevel.saveLevelData(true)` den dimensionsbezogenen SavedData-Speicher,
nicht automatisch den globalen Server-Speicher und dessen `level.dat`.

## Neue Ausgabe

Die öffentliche Signatur bleibt unverändert:

```java
CompletionStage<WorldSnapshotResult> snapshotWorldsAsync(
    List<World> worlds,
    Path snapshotPath
);
```

```text
snapshotPath/
├── runtime/                    # falls vom Aufrufer angelegt; unangetastet
├── level/
│   ├── level.dat                # verpflichtend
│   ├── level.dat_old            # falls vorhanden
│   ├── data/                   # vollständig rekursiv, sonst leer
│   ├── datapacks/              # vollständig rekursiv, sonst leer
│   ├── generated/              # falls vorhanden, vollständig rekursiv
│   ├── resourcepacks/          # falls vorhanden, vollständig rekursiv
│   └── sichere reguläre Root-Dateien, z. B. icon.png
├── worlds/
│   ├── 0/                      # worlds.get(0), unveränderter Dimensionsinhalt
│   ├── 1/                      # worlds.get(1)
│   └── ...
└── players/
    ├── data/
    ├── stats/
    └── advancements/
```

Kein hartcodiertes `world`: Level-, Player- und Dimensionsquellen stammen aus
den tatsächlichen Server-/World-Pfaden. Die nummerierten Slots werden nicht
umbenannt. `level/` enthält niemals `session.lock`, `dimensions/` oder
`players/`. Unbekannte Root-Verzeichnisse gehören nicht zur Verzeichnis-
Allowlist; sichere reguläre Dateien direkt im Root werden dagegen mitgenommen.
Symlinks, Junctions/Reparse-Verzeichnisse und Spezialdateien werden abgelehnt,
nicht verfolgt. Auch vorhandene Pfadvorfahren werden geprüft und Quellen/Ziele
kanonisch auf Überlappung in beiden Richtungen geprüft.

## Konsistenz und Threads

Die bestehende Online-Pipeline wird erweitert, nicht ersetzt:

1. Alle geladenen Welten werden wegen des gemeinsamen Speichers beteiligt und
   eingefroren; kopiert werden weiterhin nur die angeforderten Dimensionen.
2. Interne Player-Saves laufen auf den jeweiligen Owner-Threads und werden
   vor den Region-Barrieren abgeschlossen.
3. Region-Barrieren und Gates warten auf auslaufende Owner-Arbeit.
4. Die bisherigen Dimensions-/Chunk-/Entity-/POI-Flushes laufen unverändert.
5. Der Global-Region-Thread erfasst globale SavedData einschließlich Scoreboard
   sowie das Root-`level.dat` als abgelöste NBT-Kopien. I/O-Worker schreiben
   diese Dateien. Bereits laufendes globales SavedData-I/O wird ebenfalls
   abgewartet, auch wenn keine neuen dirty Tags vorhanden sind.
6. Konkurrierende normale globale Saves werden bis zum Abschluss zurückgestellt.
   Neue Live-Änderungen bleiben dirty für spätere Saves. Erst nach erfolgreichem
   Flush kopiert der Lifecycle-I/O-Worker alle drei Ausgabeteile in dasselbe
   versteckte `.tessera-snapshot-<uuid>`-Staging.
7. Nach vollständiger Kopie werden `level`, `worlds`, `players` veröffentlicht.
8. Der vorhandene Resume-Pfad gibt globale Schreibsperre, Gates und Welten nach
   Erfolg wie Fehler wieder frei.

Die Snapshot-Varianten reichen I/O-Fehler weiter, die normale Vanilla-/Paper-
Speicherpfade teilweise nur protokollieren. Fehlgeschlagene SavedData-Schreib-
versuche markieren die Daten erneut dirty. Eine fehlgeschlagene Snapshot-
Future vergiftet keine nachfolgenden normalen Saves. Die NBT-Kopie verhindert,
dass ein später geändertes Live-CompoundTag in den laufenden Schreibvorgang
hineinwirkt. Kein neuer `join()` blockiert einen Region- oder Global-Thread.

## Fehler- und Publikationsvertrag

- Bereits vorhandenes `level`, `worlds` oder `players` ergibt vor dem Einfrieren
  `TARGET_EXISTS`; auch reguläre Dateien oder Links unter diesen Namen zählen.
- Fehlendes Root-`level.dat` ergibt `SAVE_FAILED` mit
  `LEVEL_ROOT_MISSING_LEVEL_DAT: <Pfad>`. Es wird nicht als leeres Backup
  akzeptiert. Die Prüfung erfolgt vor Aufnahme und nochmals beim Kopieren.
- Fehler beim globalen Flush ergeben `SAVE_FAILED`; fehlgeschlagener Root-
  Dateiaustausch enthält `LEVEL_ROOT_SAVE_FAILED` in der Diagnose/Cause-Kette.
- Kopier-/Publikationsfehler ergeben keinen Erfolg. Bereits publizierte
  Geschwister dieses Aufrufs werden in umgekehrter Reihenfolge entfernt;
  alle eigenen Geschwister werden auch nach einem einzelnen Löschfehler
  weiterhin zur Bereinigung versucht. Staging wird ebenfalls bereinigt.
- Misslingt das Entfernen eines eigenen publizierten Teils, lautet das Ergebnis
  `CLEANUP_FAILED`. Eine fehlende Dateisystem-Berechtigung kann die automatische
  Bereinigung unmöglich machen; verbleibende Teile sind dann manuell zu prüfen.
- Caller-eigene Kinder wie `runtime/` bleiben unverändert. Auch eine zwischen
  zwei Moves entstandene fremde Zielkollision wird nicht als eigenes Verzeichnis
  zurückgerollt.
- Die bestehenden 30-Sekunden-Timeouts für Player-Saves, Barrieren und Owner-
  Drain bleiben bestehen. Flush/Kopie werden nicht durch einen unsicheren Timer
  unterbrochen, der Tick-Arbeit über noch laufenden Schreibern freigeben würde.

**Wichtig:** Konsumenten müssen den erfolgreichen `WorldSnapshotResult`
abwarten. Drei Geschwister-Moves sind keine einzelne absturzatomare
Dateisystemtransaktion. Bei Prozessabsturz/Stromausfall kann ein Teil sichtbar
bleiben; der Fehler-Rollback gilt für den laufenden Prozess. Externe Programme
oder Plugins, die Dateien direkt manipulieren, werden von Region-Gates nicht
angehalten. Bösartige gleichzeitige Dateisystem-/Pfadersetzung ist keine
zugesicherte Sicherheitsgrenze dieses Snapshot-Vertrags.

## Neue Patches und geänderte Dateien

Die folgenden drei Patches ergänzen die bisherige Serie und gehören zusammen:

- `folia-api/paper-patches/features/0011-Document-shared-level-root-in-runtime-snapshots.patch`
  – ausschließlich Javadoc in `RuntimeWorldManager.java`.
- `folia-server/paper-patches/features/0021-Include-shared-level-root-in-runtime-snapshots.patch`
  – `CraftRuntimeWorldManager.java`, neue interne `SnapshotFiles.java`,
  `SnapshotFilesTest.java` und `SnapshotGlobalStorageTest.java`.
- `folia-server/minecraft-patches/features/0031-Flush-and-protect-global-level-snapshot-storage.patch`
  – `MinecraftServer.java`, `SavedDataStorage.java`, `LevelStorageSource.java`.

Im Hauptrepository: `test-plugin/.../RuntimeSnapshotSmoke.java`,
`smoke-tests/runtime-snapshot/run.mjs`, dieser Bericht sowie die aktualisierten
Verträge in `docs/runtime-world-lifecycle.md` und `docs/TESSERA-UPDATE-26.2-017.md`.
Keine neue öffentliche Plugin-API-Methode, kein geänderter Ergebnis-Typ und keine Änderung
an der bestehenden internen Player-Save-Queue oder `SnapshotTaskGate`.

## Prüfungen

Die gezielte Normal-Testgruppe ist erfolgreich. Die zwei neuen Testklassen
enthalten 27 Fälle: Pflicht-/optionale Dateien, rekursive Bäume, Ausschlüsse,
fehlendes `level.dat`, Kollisionen, Überlappungen, Links/Junctions/Spezialdateien,
Rollback an jedem der drei Moves, Caller-Erhalt und Wiederfreigabe sowie
echtes globales NBT-I/O, ausstehende Writes, tiefe Kopie und Fehler/Retry.
26 wurden ausgeführt; der Symlink-Test wurde wegen fehlenden Windows-Rechts
zur Symlink-Erstellung übersprungen. Der echte Windows-Junction-Test lief
erfolgreich. Spezialdateien werden über ihre Dateiattribute simuliert, nicht
mit echten Devices/Sockets erzeugt.

Auch die bisherigen 17 Fälle in `SnapshotPlayerSchedulerTest`,
`SnapshotTaskGateTest` und `SnapshotPipelineTest` liefen unverändert erfolgreich.
Protokoll: `build/level-root-tests.log`.

### Vollständiger Patch-Neuaufbau und Build

`applyAllPatches`: **BUILD SUCCESSFUL** (7 min 57 s).
Die erneut angewendeten API-, Server- und Minecraft-Quellen sind inhaltlich
identisch mit dem exportierten Stand; alle drei generierten Git-Repositories
sind sauber. Kein bisheriger Patch wurde verändert oder entfernt. Git-Diff
wurde vor und nach dem Neuaufbau geprüft.

`test build createPaperclipJar :test-plugin:jar`: **BUILD SUCCESSFUL**
(8 min 11 s), ohne Ausschluss von Test- oder Checkstyle-Tasks. Gradle nutzte
für unveränderte Eingaben seinen normalen Cache. Der spätere isolierte
Testplugin-Build für ausführlichere Cause-Ausgabe war ebenfalls erfolgreich.

| Testprojekt | Fälle | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| API | 526 | 0 | 2 |
| Server, sämtliche Gruppen | 9.247 | 0 | 23 |
| Checkstyle-Testprojekt | 3 | 0 | 0 |
| Gesamt | 9.776 | 0 | 25 |

Davon sind 24 Auslassungen bereits im Ausgangsstand vorhanden; hinzu kommt
der oben erläuterte Windows-Symlink-Test. Der Junction-Test wurde ausgeführt.
Buildprotokolle: `build/level-root-apply.log`,
`build/level-root-full-build.log`, `build/level-root-smoke-plugin.log`.

### Echter Smoke-Test mit dem finalen Paperclip-JAR

Erfolgreicher Lauf: `build/snapshot-smoke-1789058896280`.
`snapshot-smoke/result.txt` enthält `PASS`; der Node-Runner endet mit Exitcode
0 und `SNAPSHOT_END_TO_END_PASS`. Protokoll inklusive NBT-Auswertung:
`build/level-root-final-smoke-2.log`; Serverlogs liegen zusätzlich im Laufordner.

| Fall | Verbundene Spieler | Dauer |
| --- | ---: | ---: |
| Ohne Spieler | 0 | 819 ms |
| Ein Spieler | 1 | 638 ms |
| Unmittelbare Wiederholung | 1 | 580 ms |
| Getrennte Regionen, Overworld und Nether | 3 | 1.151 ms |
| Nach absichtlich ausgelöstem Kopierfehler | 3 | 950 ms |
| Nach Disconnect | 2 | 682 ms |

Alle sechs Ausgaben bestanden die `LEVEL_ROOT_VERIFIED`-Prüfung:
`level.dat` mit richtigem Level-Namen, `level.dat_old`, frische globale
CommandStorage-Daten, optionale rekursive Bäume, reguläre Root-Datei und
Ausschlüsse. Spieler-NBT enthält die frisch gesetzten XP-Werte 73/147 und
sieben Diamanten. Die drei Spielerdateien bestätigen zwei Dimensionen und
die entfernte Overworld-Position `[4096.5, 90, 4096.5]`.
Scheduler-Abweisung während des Snapshots und anschließende Owner-Aufgabe
nach Resume sind bestätigt. Keine Thread-Ownership-/ConcurrentModification-
Fehler und keine Snapshot-Timeouts im erfolgreichen Lauf.

Ein weiterer unabhängiger Bestätigungslauf mit demselben JAR war ebenfalls
vollständig erfolgreich: `build/snapshot-smoke-1789058989349`, Protokoll
`build/level-root-final-smoke-3.log`, Exitcode 0, `PASS` und sämtliche sechs
`LEVEL_ROOT_VERIFIED`-Prüfungen. Dauern in derselben Reihenfolge:
772 / 588 / 583 / 805 / 1.051 / 741 ms. Damit sind zwei aufeinanderfolgende
vollständige Läufe einschließlich Fehler-/Resume-Fällen bestanden.

Ein vorausgegangener Lauf (`build/snapshot-smoke-1789058751862`, Protokoll
`build/level-root-final-smoke.log`) scheiterte nach vier erfolgreichen
Snapshots an einem Dateisystemfehler beim Publizieren von `players/` im
Fall `after-failure`. Das Ergebnis war korrekt `COPY_FAILED`, kein Erfolg;
die anschließende Prüfung fand **null** verbliebene Kinder im betroffenen
Snapshot-Ziel. Der konkrete Betriebssystem-Auslöser ist nicht eindeutig
festgestellt. Die Testdiagnose wurde um die vollständige Cause-Kette ergänzt;
am Server-JAR wurde zwischen diesem Lauf und dem erfolgreichen Wiederholen
nichts geändert. Das ist ausdrücklich kein stillschweigend ignorierter Erfolg.
In beiden anschließenden vollständigen Läufen war dieser Dateisystemfehler
nicht reproduzierbar. Ein dauerhafter externer Datei-Lock bleibt ein möglicher
kontrollierter Fehlerfall, nicht etwas, das als erfolgreiches Backup gilt.

Der erweiterte Smoke-Test verwendet bewusst `level-name=tessera_snapshot_root`.
Er prüft nicht nur die Dateiliste, sondern dekomprimiert `level.dat` sowie
globale CommandStorage-NBT und erwartet pro Snapshot einen frischen Marker.
Der Marker wird ausschließlich im Testplugin auf dem Global-Owner gesetzt;
Folia-deaktivierte Commands werden hierfür nicht reaktiviert. Die bisherigen
0/1/3-Spieler-, Wiederholungs-, Kopierfehler-/Resume- und Disconnect-Tests
einschließlich Spieler-NBT bleiben erhalten.

## Anwenden und bauen

Die neuen Patchdateien zusätzlich zur vollständigen bisherigen Serie behalten.
Keine manuellen Änderungen an generiertem Minecraft-Code nötig. Mit Java 25:

```powershell
.\gradlew.bat applyAllPatches
.\gradlew.bat test build createPaperclipJar
.\gradlew.bat :test-plugin:jar
node smoke-tests/runtime-snapshot/run.mjs "C:/Program Files/Java/jdk-25.0.3/bin/java.exe"
```

Server-JAR: `build/libs/tessera-server-26.2.build.017-stable.jar`.
Die Buildnummer allein unterscheidet alte und neue 017-Artefakte nicht;
der SHA-256 des final geprüften JARs lautet:

```text
3393e984e8576ab56c0f0bd560abdf882d549b1fc7684ffe180f7014eed08de5
```

Absoluter Pfad:
`C:\Users\hunte\IdeaProjects\Tessera\build\libs\tessera-server-26.2.build.017-stable.jar`
(65.676.561 Bytes).

Der Smoke-Test bindet nur Loopback `127.0.0.1:25584`, erzeugt eigene Testwelten
unter `build/snapshot-smoke-<Zeitstempel>` und beendet den Server anschließend.
Vorhandene Spielwelten oder frühere Testläufe werden nicht verändert/gelöscht.
Es sind echte Protokollverbindungen, keine grafischen Minecraft-Clients.

Die vorhandenen Persistenz-/`disable*Saving`-Einstellungen behalten ihre
Bedeutung. Die Welten pausieren für Flush und Kopie; das ist kein Copy-on-write
oder unterbrechungsfreies Backup. Windows-OSHI-/JVM-Warnungen beim Start sind
von den Snapshot-Ergebnissen getrennt zu beurteilen.
