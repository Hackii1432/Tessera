# Nativer Spieler-Restore – Tessera 26.3-010-alpha

Stand: 27.09.2026. Die native Serveranbindung implementiert Vertrag **1**.
Die öffentliche Transaktion wurde auf eigenen isolierten Servern mit verbundenen
Protokollclients geprüft. Der komplette MCC-/MVE-Stack ist separat zu prüfen.
Quellcommit, finale JAR und Prüfprotokoll: [Build 010](BUILD-26.3-010.md).

## Unveränderter API-Vertrag

```java
PlayerRestoreService Bukkit.getPlayerRestoreService();
int contractVersion(); // 1 auf dem konfigurierten Tessera-Server
CompletionStage<PlayerRestoreResult> prepareAsync(
    UUID operationId, Path sourcePlayers, Path rollbackPlayers, World fallbackWorld);
CompletionStage<PlayerRestoreResult> applyAsync(UUID operationId, boolean forward);
CompletionStage<PlayerRestoreResult> completeAsync(UUID operationId, boolean committed);
boolean isRestoreTeleport(PlayerTeleportEvent event, UUID operationId);
```

MCC 0.7.5 entdeckt den Vertrag über seinen bestehenden Pfad. Eine neue
MCC-Schnittstelle ist nicht vorgesehen. Das ersetzt nicht den erneuten gemeinsamen
Integrationslauf mit dem konkreten Plugin-Stack.

## Transaktion und Zuständigkeit

- Prepare prüft getrennte Pfade, die Stores `data`, `stats`, `advancements`,
  NBT/JSON, Datenversionen, Weltverweise und Entity-Identitäten. Die Rollback-Wurzel
  darf nicht existieren; die Quelle bleibt unverändert.
- Bereits zugelassene Logins und Writer werden abgewartet. Aktuelle Spielerzustände
  werden auf ihren Besitzern aufgenommen und als synchronisierter frischer
  Rollback-Store publiziert. Erst danach wird `PREPARED` gemeldet.
- Vorwärts-Apply publiziert die vorbereiteten Stores und ersetzt die verbundenen
  Spieler auf ihren tatsächlichen Besitzern. Spieler ohne Save-Eintrag erhalten
  frische Vanilla-Zustände in der expliziten Fallback-Welt.
- Fahrzeuge, Schulterentities, Perlen, Unteraufgaben und die konkrete
  Client-Transferbestätigung gehören zum Abschluss. Spielerinstanz, Verbindung,
  Entity-Scheduler und Inventar-Wrapper bleiben erhalten.
- Rückwärts-Apply wartet vorausgehende Arbeit ab und stellt den frischen
  Rollback-Store einschließlich verbundener Spieler wieder her. Ein vor Prepare
  abgebrochener Bezeichner wird später nicht erneut gestartet.
- Complete gibt die Schranke nur für die passende angewendete Entscheidung und
  nach dem Writer-Drain frei. **Vor Complete(true) muss MCC seine Commit-Entscheidung
  dauerhaft sichern.** Gegenläufige späte Requests werden zurückgewiesen;
  Wiederholungen liefern die vorhandene Entscheidung.

Kein Plugin darf diese Futures synchron auf Regions-, Global- oder Netzwerkthreads
abwarten. Caller-Cancellation beendet nur die Sicht des Callers. Nach Fehler oder
Timeout bleibt die Schranke bis zur ausdrücklichen Recovery/Completion erhalten;
ein Plugin-Disable öffnet sie nicht pauschal.

## Native Implementierung

- `NativePlayerRestoreBackend` besitzt Transaktion, I/O-Arbeit und Welt-/Snapshot-Koordination.
- `PlayerStoreFence` zählt Login-/Writer-Zulassungen. Generationen verhindern
  spätes Zurückschreiben alter Spieler-, Statistik- und Fortschrittsmanager.
- Die interne Owner-Queue läuft unabhängig von Gameplay-Ticks innerhalb der
  Regions-/Snapshot-Sicherungen. Threadschutzprüfungen bleiben aktiv.
- `NativePlayerRestoreState` ersetzt NBT-Zustand, Rezepte, Statistiken,
  Fortschritts-Trigger und Client-Caches. Entfernte Werte werden nicht additiv behalten.
- `NativeRestoreAttachments` prüft und ersetzt gespeicherte Entity-Graphen.
- `RestoreStoragePlatform` erzwingt Datei-Synchronisation und POSIX-Verzeichnis-
  Synchronisation beziehungsweise nicht überschreibende Windows-Write-through-
  Umbenennungen. Fehlgeschlagene Publikation erhält die betroffenen Bäume.
- Vorbereitete Weltgeneratoren/Biombindungen werden nicht durch globale Einstellungen ersetzt.

Neue gewöhnliche Logins und Player-Store-Zugriffe sind während der Transaktion
gesperrt. Verbindungspflege, Restore-Arbeit und Transferbestätigungen laufen auch
bei MCC-Tick-Freeze weiter. Neue Runtime-Snapshots werden kontrolliert als
`SOURCE_BUSY` abgelehnt.

## Shutdown und Recovery

Verbleibende native Wartestufen werden erst nach dem Anhalten von Regions- und
Chunk-Arbeit beendet. I/O-Arbeit und Lock-Akquisition werden ebenfalls abgewickelt.
Es gibt keinen als beendet gemeldeten, später weiterlaufenden nativen Transfer.
Auch noch in der Konfiguration wartende Login-Zulassungen werden nach dem
nativen Stillstand freigegeben. Fehlt zu diesem Zeitpunkt noch ein vollständiger
Rollback-Store, werden erfasste Logout-/Shutdown-Zustände vor dem Fehlerabschluss
dauerhaft im aktiven Store gesichert. Tatsächlich noch laufende Scheduler-Worker
führen dagegen nicht zu einer falschen Drain-Bestätigung oder Schrankenfreigabe.

Nach Neustart werden alte Transaktionen **nicht automatisch nachgespielt**.
Die offline getroffene Welt-/Store-Entscheidung gehört weiterhin zum MCC-Journal.
Erhaltene ursprüngliche Player-Store-Bäume und Rollback-Daten unterstützen diese
Recovery. Prozess-Neustarttests simulieren keine defekten Datenträger oder
Hardware-Stromausfälle.

## Eigene native Abnahme

Die Runner verwenden neue, markierte Verzeichnisse unter `build`, freie
Loopback-Ports und ein separates Testplugin. Kein produktiver Save ist eine Fixture.

```powershell
node smoke-tests/native-player-restore/run.mjs "C:/Program Files/Java/jdk-25.0.3/bin/java.exe"
$env:NATIVE_RESTORE_MODE = 'native-restore-races'
node smoke-tests/native-player-restore/run.mjs "C:/Program Files/Java/jdk-25.0.3/bin/java.exe"
Remove-Item Env:NATIVE_RESTORE_MODE
node smoke-tests/native-player-restore/recovery.mjs "C:/Program Files/Java/jdk-25.0.3/bin/java.exe"
```

Die Hauptprüfung verlangt Vertrag exakt `1`, zwei verbundene Clients,
exakten Zustand, Wiederholung/Resave, echten Rollback und keinen Reconnect.
Sie umfasst leere Saves, entfernte Einträge, Fahrzeuge/Perlen, Writer-/Request-Rennen,
echte I/O-Fehler, Offline-Cache-Generationen und neue Zielwelt-Chunks.

Der separate Race-Test erzeugt absichtlich genau einen Disconnect/Rejoin und
einen fehlenden Transfer-ACK. Der Recovery-Runner stoppt und startet eigene Server
vor/nach Commit, während Apply und während eines vor Prepare zugelassenen,
noch nicht abgeschlossenen Logins; seine offline Dateiauswahl simuliert die
**Aufgabe des Callers**, nicht MCC-Produktcode.

Die Clients verarbeiten echte Protokollantworten, rendern aber kein Vanilla-Fenster.
Die native Laufzeitabnahme erfolgte auf Windows mit Java 25, nicht zusätzlich auf Linux.

## Offene gemeinsame MCC-/MVE-Abnahme

Der genaue Stack MCC 0.7.5, MVE 26.3-1.5.00, TAB 6.2.0 und LuckPerms 5.5.85
war für diese eigenen Läufe nicht vollständig als ausführbarer Stack vorhanden.
MVE-JARs waren verfügbar; die gefundenen MCC-JARs waren ältere Versionen.
MCC-/MVE-Produktcode wurde nicht geändert oder ersetzt.

Noch gemeinsam auszuführen, nicht als native Tests mitgezählt:

- MCC-Save/Load über mehrere Regionen/Dimensionen, wiederholter Load/Resave,
  Rollback und freigegebene Logins; erfolgreicher Load ohne Kick/Reconnect.
- MVE: Vanilla-Stats zurücksetzen, Lifetime-Stats erhalten; Primary-World-Bindung
  und Baseline nach wiederholtem Load prüfen.
- Echte MCC-Resets `NORMAL ↔ SINGLE_BIOME` und Single Structure einschließlich
  Nether/End; gespeicherte Generatoren müssen für neue Chunks erhalten bleiben.
- Geöffneten MCC-Backpack vor dem Snapshot schließen und Inhalte nach Load
  zusammen mit dem Spielerinventar prüfen.

Die gebaute JAR und nativen Nachweise stehen für diese Folgeabnahme zur Verfügung.
Ein erzwungener Reconnect ist kein erfolgreicher Seamless-Load.
