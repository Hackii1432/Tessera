# MCC-Spieler-Restore: unvollständiger Implementierungsstand

Ausgangsbasis: Tessera 26.2-017, `85a77ed`. Ausschließlich Tessera wurde
bearbeitet. MCC und MVE wurden weder geändert noch gemeinsam getestet.

**Der vollständige MCC-Live-Load ist nicht implementiert. „Load ohne Kick“
ist nicht fertig und nicht für MCC 0.6.1 freigegeben.** Der öffentliche
Dienst meldet `contractVersion() == 0`. Eine Versionsprüfung auf exakt `1`
im MCC-Adapter muss damit weiterhin den bisherigen Pfad auswählen.
Insbesondere darf das bloße Vorhandensein der neuen Methoden keinen Live-Load
aktivieren. Es gibt keinen Schalter, der diese Sperre übergeht.

## Vorhandene API

```java
// org.bukkit.Bukkit
public static PlayerRestoreService getPlayerRestoreService();

// org.bukkit.Server (Instanzmethode)
PlayerRestoreService getPlayerRestoreService();

// io.papermc.paper.world.PlayerRestoreService
int contractVersion();
CompletionStage<PlayerRestoreResult> prepareAsync(
    UUID operationId, Path sourcePlayers, Path rollbackPlayers, World fallbackWorld);
CompletionStage<PlayerRestoreResult> applyAsync(UUID operationId, boolean forward);
CompletionStage<PlayerRestoreResult> completeAsync(UUID operationId, boolean committed);
boolean isRestoreTeleport(PlayerTeleportEvent event, UUID operationId);

// io.papermc.paper.world.PlayerRestoreResult, öffentlicher Record
UUID operationId();
PlayerRestoreStatus status();
boolean successful();
String message();
```

`PlayerRestoreStatus` ist öffentlich. Exakt `PREPARED`, `APPLIED`,
`ROLLED_BACK`, `COMPLETED` ergeben `successful() == true`. Die weiteren Werte
sind `UNSUPPORTED`, `INVALID_REQUEST`, `CONFLICT`, `BUSY`, `CANCELLED`,
`VALIDATION_FAILED`, `PLAYER_BUSY`, `SAVE_FAILED`, `TRANSFER_FAILED`,
`COMPLETE_FAILED`, `SERVER_STOPPING` und ergeben immer `false`.

Der gegenwärtige Dienst gibt für gültiges Prepare `UNSUPPORTED` zurück.
Er installiert keine native Schranke, liest/kopiert keine Stores und
überträgt keine Spieler. Rollback und Complete false können diesen
wirkungslosen Versuch sowie eine unbekannte, vor Prepare abgebrochene
Operation abschließen. Vorwärts-Apply und Complete true können keinen Erfolg
erreichen. `isRestoreTeleport` liefert immer `false`, da kein nativer Transfer
aktiviert ist. Nach einem fehlgeschlagenen Prepare bleibt die Operation im
Koordinator bis zum Rollback/Complete logisch aktiv; das ist keine Gameplay-
oder Speicher-Schranke.

UUIDs müssen nicht null sein; null wird synchron zurückgewiesen. Fehlende
Prepare-Pfade oder die fehlende Fallback-Welt ergeben `INVALID_REQUEST` mit
der angeforderten UUID. Ergebnisse, Status, Diagnosen und Stages sind ansonsten
nicht null und öffentlich reflektierbar. Es gibt keine NMS-Typen in der API.

Die vorgeschlagenen v1-Signaturen wurden übernommen. `0` kennzeichnet
ausdrücklich eine **nicht verfügbare** Implementierung und ist keine neue
v1-Semantik. Es wurde kein Vertragsfehler nachgewiesen, der eine abweichende
v1-Implementierung rechtfertigen würde.

## Implementierte und isoliert geprüfte Bausteine

Diese Bausteine sind noch nicht mit den nativen Spielerdatenpfaden verbunden:

- `PlayerRestoreCoordinator`: gleiche Requests teilen ihre Arbeit; andere
  Parameter derselben UUID werden abgewiesen. Rollback setzt sofort eine
  Abbruchmarkierung und wartet vorangegangene Backend-Arbeit ab. Unbekannte
  Rollbacks hinterlassen eine Abbruchmarkierung bis Serverneustart. Bereits
  abgeschlossene Entscheidungen sind wiederholbar. Eine bestätigte Entscheidung
  wird vor Complete festgeschrieben; ein Complete-Fehler erlaubt nur eine
  Wiederholung derselben Entscheidung. Fehlgeschlagene Rollbacks können erneut
  versucht werden. Caller-Futures sind vom internen Arbeitsabschluss getrennt.
- `PlayerStoreFence`: nicht blockierende Schreibzulassung mit gezählten
  laufenden Schreibern und Generationen. Alte Cache-Generationen können nach
  einem Store-Wechsel auch nach Öffnung der Schranke keinen Writer erwerben.
  Die tatsächlichen Vanilla-Save-/Logout-Pfade erwerben diese Leases noch nicht.
- `PlayerRestoreFiles`: getrennte, neue Kopierziele; Schutz vor überlappenden
  Pfaden, Links und speziellen Dateien; vollständige Kopie der drei Ordner;
  `force(true)` der kopierten Dateien; explizit einzuspeisende Verzeichnis-
  Dauerhaftigkeit. Publikation erhält die alten Verzeichnisse unter einem
  eindeutigen Namen. Scheitert die Publikation, wird die Rückbenennung versucht;
  scheitert auch diese, bleiben beide Bäume erhalten und beide Fehler werden
  gemeldet. Keine Methode löscht Original-, Backup- oder Snapshot-Daten.
- `RestoreTeleportScope`: Berechtigung nur für die konkrete Ereignisinstanz,
  Operation und den Dispatch-Thread, mit Bereinigung auch nach einer Exception.
  Kein spielerweites Zeitfenster. Der öffentliche Dienst verwendet diesen
  Baustein noch nicht, da die Transfer-Anbindung fehlt.

Backend-Stages müssen sämtliche Unteraufgaben einschließlich Cleanup umfassen.
Ein Koordinator allein kann aus einer nie abgeschlossenen oder falsch definierten
Backend-Stage keinen sicheren Abschluss herstellen. Es gibt keine automatische
Abbruchfrist, die schreibende Arbeit von ihrer Schranke trennt.

## Noch erforderliche native Implementierung

1. Native Schranke für alle Player-Store-Schreiber, laufende sowie bereits
   zugelassene Logins, Logout, Autosave und explizite `Player.saveData()`-Aufrufe
   integrieren. Auch Offline-Statistikmanager benötigen eine Generation.
   Laufende Schreibarbeit muss enden, bevor der frische Rollback-Store versiegelt
   werden darf. Snapshot- und Welt-Lifecycle-Operationen müssen koordiniert werden.
2. Eigene Entity-/Region-/Global-Aufgaben für den Restore einführen, die bei
   MCC-Tick-Freeze weiterlaufen. Gameplay einfrieren, Verbindungspflege und
   Transferbestätigungen weiter bedienen. In-flight Respawn/Transfer und
   Disconnect auf ihren tatsächlichen Besitzern behandeln.
3. Alle Quell-NBT-/JSON-Daten streng und vollständig mit Datenfixierung prüfen,
   sämtliche Weltverweise aufgelöst prüfen, den lebenden Zustand erfassen und
   die Quelle unverändert lassen. Reine Datei-/Ordnerprüfung reicht nicht.
4. Vollständigen Spielerzustand mit echten Vanilla-Defaults für fehlende Daten
   ersetzen. Die bestehenden Load-Pfade reichen dafür nachweislich nicht:
   `CraftEntity.readBukkitValues` ergänzt PDC mit `putAll`;
   `AttributeMap.apply` ersetzt nur angegebene Attribute;
   `ServerRecipeBook.loadUntrusted` ergänzt die bekannten Rezepte;
   `ServerStatsCounter.parse` ergänzt die Statistikmap. Die bestehenden
   `ServerPlayer`-Statistik-/Fortschrittsreferenzen werden beim Teleport erhalten.
   Diese Stellen wurden hier analysiert, noch nicht geändert.
5. Fortschritte einschließlich Triggern und leerem Client-Reset, vollständige
   Statistik-Resets einschließlich clientseitiger Nullwerte, Rezepte, Inventare,
   Attribute, Effekte, Fähigkeiten, Respawn, PDC und übrige serialisierte Daten
   erneuern. Bei Ersatz nativer Instanzen sämtliche Bukkit-, Inventar-,
   Scheduler-, Verbindungs- und Tracking-Referenzen nachführen. Insbesondere
   `CraftHumanEntity.setHandle` aktualisiert bisher nicht den finalen
   Endertruhen-Wrapper; ungeprüfter Instanztausch wäre ebenfalls unvollständig.
6. Fahrzeuge, Schulterentities und Enderperlen einschließlich möglicher
   Weltkopien, UUID-Kollisionen und mehreren Regionen ohne Verlust/Duplikate
   wiederherstellen. `ServerPlayer.loadAndSpawnEnderPearls` liefert derzeit
   keinen Stage-Abschluss für die eingereihten Region-Aufgaben. Diese müssen
   vollständig in Apply/Rollback eingehen.
7. Native Teleport-Ereignisse mit dem konkreten Scope auslösen, Vetos beachten,
   Weltwechsel, Tracking, Chunk-Zulassung und sämtliche erforderlichen
   Client-Pakete konsistent abschließen. Es ist noch keine Ereignisreihenfolge
   für einen implementierten Restore verfügbar. Join/Quit dürfen dabei nicht
   künstlich ausgelöst werden.
8. Verifizierte Plattform-Dauerhaftigkeit für Verzeichniseinträge implementieren.
   Der lokale Java-25-Probeaufruf
   `FileChannel.open(directory, READ).force(true)` scheitert unter Windows mit
   `AccessDeniedException`. Die Dateikopier-Tests speisen einen Test-Callback für
   Verzeichnis-Flush ein und belegen **keine** Crash-Dauerhaftigkeit. Es wird kein
   erfolgreicher PREPARED/APPLIED-Status aus einer solchen Testannahme abgeleitet.
9. Den nativen Backend erst nach Erfüllung aller Garantien mit dem öffentlichen
   Dienst verbinden und die Vertragsversion auf `1` setzen.

## Tests und Grenzen des Nachweises

Gezielte API-Tests prüfen öffentliche Signaturen und reflektierte
Ergebniszugriffe, exakt vier Erfolgsstatus und Nicht-null-Ergebnisse. Die
Servertests prüfen verzögertes Prepare/Apply, Rollback vor Arbeitsbeginn,
Caller-Timeout/Cancellation, konkurrierende Entscheidungen, idempotente
Wiederholungen, falsche Parameter, Complete-Fehler nach Commit und erneuten
Abschluss, Writer-Drain, alte Cache-Generationen sowie Kopier-/Rename-/Flush-
Fehler. Die Ereignistests prüfen ausschließlich den isolierten Scope.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.3'
.\gradlew.bat :folia-api:test --tests io.papermc.paper.world.PlayerRestoreApiTest `
  :folia-api:checkstyleMain :folia-api:checkstyleTest `
  :folia-server:test --tests org.bukkit.support.suite.NormalTestSuite `
  --offline --no-daemon --console=plain
```

Abschließender Lauf: API-Compile und Server-Compile erfolgreich, drei API-Tests
und 25 neue Servertests ohne Fehler; die vollständige `NormalTestSuite` war
ebenfalls erfolgreich. Beide API-Checkstyle-Berichte enthalten null Verstöße.
Die 25 Servertests verteilen sich auf Dienst (2), Koordinator (11), Dateipfade
(7), Schreibschranke (3) und Teleport-Scope (2). Lokale XML-Berichte liegen in
`folia-api/build/test-results/test` und `folia-server/build/test-results/test`;
das abschließende Build-Protokoll liegt in `build/player-restore-tests.log`.

Die Änderungen werden als API-Patch `0012` und Server-Patch `0022` geliefert.
Es gibt keinen neuen Minecraft-Patch und kein freigegebenes Server-Artefakt.
Beide Serien wurden erfolgreich erneut angewendet; der anschließende
Git-Vergleich ihrer gesamten `src`-Bäume gegen die getesteten Quell-Commits
ergab jeweils keine Änderung. Protokolle: `build/player-restore-apply-api.log`
und `build/player-restore-apply-server.log`.
Die alten Patchdateien wurden inhaltlich und bytegleich erhalten. Beim
kombinierten Rebuild meldete Gradle 9.4.1 einen bereits im Task-Graph liegenden
fehlenden Abhängigkeitsbezug zwischen `checkoutPaperRepo` und
`filterPaperServerFromPaper`; getrennte Aufrufe liefen erfolgreich. Für diesen
Stand nur die betroffenen Serien einzeln bearbeiten:

```powershell
.\gradlew.bat rebuildPaperApiPatches --offline --no-daemon --console=plain
.\gradlew.bat rebuildPaperServerPatches --offline --no-daemon --console=plain
.\gradlew.bat applyPaperApiPatches --offline --no-daemon --console=plain
.\gradlew.bat applyPaperServerPatches --offline --no-daemon --console=plain
```

**Nicht durchgeführt und nicht bestanden gemeldet:** echte Vanilla-Client-
Save/Load-Zyklen ohne Verbindungswechsel; Vollzustandsvergleich nach Autosave
und erneutem Login; Online-/Offline-/Erstbeitrittsfälle in mehreren Dimensionen,
Regionen und MAB-Welten; Disconnect während eines nativen Transfers; native
Speicher-/Transfer-/Complete-Fehler; Crash vor/nach MCC-Commit;
NORMAL/SINGLE_BIOME-World-Resets mit neuen Chunks und Neustart;
MVE-Lifetime-/Baseline-Abnahme ohne Join/Quit. Der bestehende Snapshot-Smoke-Test
ist kein Ersatz für diese Restore-Tests.

## Wiederanlauf

Der jetzige Dienst öffnet und hält keine native Restore-Schranke und besitzt
keine dauerhaften Restore-Transaktionen. Ein Serverneustart verwirft nur seine
wirkungslosen, im Speicher gehaltenen Operationseinträge. Er spielt nichts
über ein MCC-Recovery zurück.

Für den noch zu implementierenden nativen Backend gilt weiterhin der Auftrag:
Plugin-Ausfall darf eine gehaltene Schranke nicht automatisch öffnen.
Wiederanlauf erfolgt durch Fortsetzen derselben Operation und Entscheidung
oder durch kontrollierten Serverstopp und MCC-Offline-Recovery beim Neustart.
Tessera darf hierbei keine eigene alte Transaktion nachträglich ausführen.
