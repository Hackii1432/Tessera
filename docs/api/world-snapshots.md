---
title: "Frische Welt- und Spieler-Snapshots"
description: "Konsistente Live-Sicherungen, gemeinsame Level-Daten und Abschluss- sowie Fehlervertrag."
navTitle: "Snapshots"
order: 60
updated: 2026-10-05
minecraftVersion: "26.3"
badge: "Referenz"
---

`RuntimeWorldManager` kann einen frischen gemeinsamen Save von geladenen Welten
und verbundenen Spielern erzeugen. Das ist keine Kopie eines veränderlichen
Live-Verzeichnisses ohne Serverkoordination und kein Wiederverwenden des Clone-Caches.

## Signatur und Eingaben

```java
CompletionStage<WorldSnapshotResult> snapshotWorldsAsync(
    List<World> worlds, Path snapshotPath
);
```

`worlds` ist eine nichtleere, duplikatfreie Liste geladener Welten dieses Servers.
Ihre Reihenfolge definiert `worlds/0`, `worlds/1` usw. Die Liste während des Aufrufs
nicht parallel verändern. Startup-Welten dürfen hier gelesen werden; ihr
Unload-Schutz bleibt bestehen. `snapshotPath` ist eine caller-eigene Staging-Wurzel.
Die Ausgabe-Kinder `level`, `worlds`, `players` dürfen noch nicht existieren.
Ein vorhandenes `runtime/` und andere unbeteiligte Kinder bleiben unangetastet.

Aufruf von jedem Thread; Completion-Callbacks haben keine allgemeine
Regionszuständigkeit. Quelle/Ziel dürfen nicht überlappen; Symlinks, Junctions,
Spezialdateien und unvollständige Speicher werden nicht als sichere Kopie akzeptiert.

## Ablauf und Speicherumfang

Alle geladenen Welten werden vorübergehend koordiniert angehalten, auch wenn nur
ein Teil kopiert wird: Level- und Player-Storage sind serverweit geteilt.
Verbundene Spieler werden auf ihren Besitzern gespeichert, laufende Speicherarbeit
beendet, Regionen/Chunk-Arbeit an Barrieren zusammengeführt und Chunks, Entities,
POI, SavedData und globale Metadaten geflusht. Interne Drain-Arbeit kann auch
bei Tick-Freeze laufen; normale Entity-Plugin-Tasks sind kein Ersatz dafür.

Seit Build 015 wird auch die native Welt-Vorgenerierung koordiniert: Vor dem
Lifecycle-Wechsel wird ihre Aufnahme neuer Arbeit für die beteiligten Welten
gesperrt und bereits zugelassene Arbeit einschließlich ihrer Schreibbestätigungen
und eigenen Ticket-Bereinigung abgewartet. Nach dem Snapshot wird die Aufnahme
wieder freigegeben. Die Signatur und Ergebnisstatus bleiben unverändert.
Gewöhnliches `/tick freeze` allein hält den nativen Pregenerator nicht an.

```text
snapshotPath/
  runtime/                 # caller-eigen; nicht von Tessera verändert
  level/level.dat          # erforderlich
  level/level.dat_old      # falls vorhanden
  level/data/
  level/datapacks/
  level/generated/         # falls vorhanden
  level/resourcepacks/     # falls vorhanden
  worlds/0/
  worlds/1/
  players/data/
  players/stats/
  players/advancements/
```

Die Weltkopien erhalten UUID-/Paper-Metadaten, vorhandene `level.dat`, `region`,
`entities`, `poi`, `data` und `datapacks`. Keine Umformung von Entity-UUID,
Position oder Motion. Session-Locks, fremde verschachtelte Dimensionbäume und
primäre Player-Verzeichnisse werden nicht in jeden Weltslot dupliziert.
Leere Player-Unterordner werden angelegt; MCA-Dateien werden strukturell validiert.

`level/` enthält gemeinsame Metadaten und sichere reguläre Root-Dateien, aber
keine `session.lock`, `dimensions/`, `players/` oder beliebige unbekannte Root-Ordner.
Fehlt Root-`level.dat`, scheitert die Operation mit `SAVE_FAILED`.

## Ergebnis und Fehler

```java
WorldSnapshotResult(
    WorldSnapshotResult.Status status,
    Path snapshotPath,
    String message,
    @Nullable Throwable cause
);
boolean successful();
```

Der Record stellt gleichnamige Getter bereit. `snapshotPath()` ist der normalisierte
absolute Zielpfad, **kein Erfolgsnachweis**. `successful()` gilt nur für `SUCCESS`.

| Status | Bedeutung |
| --- | --- |
| `SUCCESS` | Alle drei Ausgabeteile publiziert und Schutzphase abgeschlossen |
| `UNSUPPORTED`, `SERVER_STOPPING` | Dienst nicht verfügbar oder Serverstop |
| `INVALID_REQUEST` | Ungültige Liste/Pfade, Duplikate oder unsichere Eingabe |
| `SOURCE_NOT_LOADED`, `SOURCE_BUSY` | Quelle nicht aktiv oder inkompatible Operation, etwa Restore |
| `TARGET_EXISTS` | Kollision mit vorhandenem Ausgabeziel |
| `SAVE_FAILED`, `COPY_FAILED` | Flush oder validierte Kopie fehlgeschlagen |
| `TIMEOUT`, `CANCELLED` | Barrierezeit überschritten oder kooperativer Abbruch |
| `CLEANUP_FAILED` | Eigene partielle Ausgaben nicht vollständig entfernt |

Aufruf-/Future-Ausnahmen zusätzlich behandeln. Alle drei Ausgabeteile werden
zusammen vorbereitet, danach durch **drei** Verzeichnis-Moves publiziert; das
ist keine absturzatomare Dateisystemtransaktion. Konsumenten dürfen erst nach
Erfolg lesen. Bei Publikationsfehlern werden nur eigene publizierte Teile
zurückgenommen; fremde caller-eigene Daten bleiben erhalten.

## Timeout, Disable und Restore

Region-/Spielerbarrieren haben in der aktuellen Implementierung 30 Sekunden
Timeout. Bereits laufender Storage-Flush wird nicht hart unterbrochen. Ein
abgebrochenes Caller-Future ist deshalb **kein** Signal, Welten/Ordner nun zu
verändern. Tessera beendet Save/Cleanup und gibt Schutzbereiche kontrolliert frei.
Bei Plugin-Disable keine neuen Snapshots starten und keine Tickthreads blockieren.

Während nativer Restore-Arbeit wird ein neuer Snapshot als `SOURCE_BUSY`
abgewiesen. Eine öffentliche allgemeine Login-/Writer-Schrankenschnittstelle
existiert nicht. Wer zusätzlich externe Plugin-Stores oder komplette Welten
austauscht, muss deren Transaktion selbst koordinieren. Der
[Spieler-Restore](player-restore.md) ersetzt nicht diesen übergeordneten Weltvertrag.

## Beispiel

`sources` ist die zuvor zusammengestellte Liste; `snapshotRoot` ein validierter
Zielpfad. Auswertung ohne Blockieren und ohne Bukkit-Zugriffe im Callback:

```java
Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List.copyOf(sources), snapshotRoot)
    .whenComplete((result, failure) -> {
        if (failure != null) {
            plugin.getLogger().log(Level.SEVERE, "Snapshot future failed", failure);
        } else if (!result.successful()) {
            plugin.getLogger().warning(result.status() + ": " + result.message());
        } else {
            plugin.getLogger().info("Snapshot ready: " + result.snapshotPath());
        }
    });
```
