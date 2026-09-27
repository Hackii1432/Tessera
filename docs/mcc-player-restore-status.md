# Nativer Spieler-Restore – Build 010, unvollständiger Prüfstand

Stand: 27.09.2026. Ausgangspunkt: `ver/26.3.x`,
`6ededc28656e3adf1d2881d0e5e68854884353cf` (Tessera 26.3-009-alpha).
Minecraft/API 26.3, Java 25. Die Wolfs-KI-Korrekturen aus 009 bleiben erhalten.
MCC, MVE und Sinopia wurden für diesen Auftrag nicht geändert.

**Der vollständige Auftrag ist noch nicht erfüllt. Build 010 ist ein
unvollständiger Prüfbuild, kein funktionierender MCC-Seamless-Load.**
`CraftPlayerRestoreService.contractVersion()` bleibt `0`; Prepare und
Vorwärts-Apply sind weiterhin nicht verfügbar. Die neuen nativen Komponenten
sind kein Ersatz für die noch fehlende öffentliche Dateitransaktion.

## Unveränderte öffentliche Schnittstelle

```java
PlayerRestoreService Bukkit.getPlayerRestoreService();
int contractVersion();
CompletionStage<PlayerRestoreResult> prepareAsync(
    UUID operationId, Path sourcePlayers, Path rollbackPlayers, World fallbackWorld);
CompletionStage<PlayerRestoreResult> applyAsync(UUID operationId, boolean forward);
CompletionStage<PlayerRestoreResult> completeAsync(UUID operationId, boolean committed);
boolean isRestoreTeleport(PlayerTeleportEvent event, UUID operationId);
```

Nur `PREPARED`, `APPLIED`, `ROLLED_BACK` und `COMPLETED` sind Erfolgsstatus.
Die vorhandenen Fehlerstatus bleiben unverändert. Der öffentliche Dienst
meldet bei Prepare `UNSUPPORTED`, verändert dabei keine Dateien und installiert
keine native Schranke. Rollback/Complete false können diesen wirkungslosen
Versuch abschließen. Das ist kein erfolgreicher Online-Restore.

Der Teleport-Scope ist jetzt mit dem nativen Komponentenpfad verbunden:
Er gilt ausschließlich für die konkrete Ereignisinstanz, Operation und den
Dispatch-Thread. Ein beliebiger Spieler-/Zeitfenster-Marker wird nicht verwendet.

## Tatsächlich angebundene native Komponenten

- Gezählt zugelassene Zugriffe in `PlayerDataStorage`, `PlayerList`,
  `ServerStatsCounter` und `PlayerAdvancements`; Generationsprüfung vor dem
  Schreiben verhindert Writes veralteter Cache-Instanzen nach einem Wechsel.
- `PrepareSpawnTask` behält eine Speicherzulassung bis einschließlich seiner
  eingereihten Enderperlenarbeit. Neue Vorbereitungen warten nicht blockierend
  in der Konfigurationsphase auf eine geöffnete Schranke.
- Bereits zugelassene Runtime-Snapshots übertragen ihre Zulassung in ihre
  tatsächlichen Owner-Saves. Neue Snapshots werden bei geschlossener
  Restore-Schranke mit `SOURCE_BUSY` abgewiesen.
- Die interne Restore-Queue läuft auf dem aktuellen Entity-Owner, prüft ihn
  nach jedem Auftrag erneut und wartet zurückgegebene Unteraufgaben ab.
  Caller-Cancellation hebt die intern besessene Arbeit nicht auf. Die bereits
  vorhandene Snapshot-Tick-Schranke wird nicht umgangen.
- Eine geschlossene native Schranke unterbindet Spieler-Ticks und eingehende
  Gameplay-Pakete. Vanilla-Tick-Freeze allein tut das für Spieler nicht.
  Verbindungspflege, Transferbestätigungen und Client-Load laufen weiter.
- `NativePlayerRestoreState` prüft kopierte NBT-/JSON-Daten mit Datenfixierung
  und einer abgetrennten Vorschauinstanz vor dem Ersetzen eines Spielers.
  Identität, Zielwelt und endliche Zielposition werden geprüft. Die Quelle
  wird nicht als Arbeitskopie verwendet.
- Inventare und Endertruhe werden unter Beibehaltung ihrer Wrapper geleert
  und ersetzt. Rezepte, Attribute, Effekte, Fähigkeiten, Spielmodus und PDC
  werden nicht bloß additiv eingelesen. Der native Spieler, die Verbindung
  und der Entity-Scheduler bleiben dieselben Instanzen.
- Statistik-Maps werden ersetzt und entfernte Werte explizit als Null an den
  Client gesendet. Fortschritte ersetzen Triggerregistrierungen und senden
  auch bei leerem Zielzustand einen echten Client-Reset ohne Reward-Replay.
- Restore-Teleports dispatchen ein operationsgebundenes Bukkit-Ereignis;
  Veto oder Umleitung führen zu einem Fehler. Die Stage berücksichtigt die
  echte Client-Bestätigung, nicht nur das Einreihen des Transfers.
- Der Perlen-Ladepfad liefert nun eine Stage über seine Region-Unteraufgaben.
  Dekodierung/World-Add erfolgen auf der Zielregion. Ersetzte eigene Perlen
  werden auf ihrem jeweiligen Owner entfernt. Der Komponentenpfad enthält
  außerdem einen Fahrzeug-Ladepfad; dessen vollständige Abnahme steht aus.

## Verbleibende native Freigabeblocker

1. **Die öffentliche Backend-Transaktion fehlt weiterhin.** Es gibt noch
   keine durchgängige Prepare/Apply/Complete-Verknüpfung aus validierter
   Quelle, frischem Rollback-Store, dauerhafter Publikation und Rebind aller
   Online-/Offline-Generationen. Minecraft 26.3 verwendet hier tatsächlich
   `<level>/players/{data,stats,advancements}`.
2. **Disconnect vor bzw. während des Rollback-Backups ist nicht abgesichert.**
   Ein bei geschlossener Schranke abgewiesener Logout-Save muss transaktional
   als unveränderlicher Endzustand übernommen oder anderweitig sicher
   abgeschlossen werden. Bloßes Überspringen des Writers genügt nicht.
   Auch der Legacy-Login-Event-Pfad vor `PrepareSpawnTask` braucht eine
   durchgehende Zulassung. Deshalb wird die Schranke nicht öffentlich aktiviert.
3. **Abbruch und Shutdown während eines nativen Cross-Region-Transfers:**
   Der vorhandene Entity-Teleportpfad kann bei Shutdown die Platzierung
   übernehmen, ohne den normalen Callback auszuführen. Ein Timeout auf der
   äußeren Future würde dann keinen echten Drain beweisen. Der Komponentenpfad
   löst dieses gesamte Lebenszyklusproblem noch nicht; Freigabe einer Schranke
   allein aufgrund eines solchen Timeouts wäre unsicher.
4. **Vollständige Vorprüfung und Recovery von Anhängen:** Fahrzeuge,
   Schulterentities, Perlen, UUID-Kollisionen und Weltverweise müssen als eine
   zusammengehörige, rollbackfähige Operation geprüft werden. Der derzeitige
   Komponentenpfad kann nach Teilmutation fehlschlagen und verlangt dafür eine
   übergeordnete Transaktion, die noch fehlt. Bereits tote Spieler werden
   derzeit abgewiesen statt vollständig in den Restore integriert.
5. **Dauerhafte Dateipublikation und Entscheidung:** Die bestehenden
   Dateihilfen benötigen weiterhin eine verifizierte plattformspezifische
   Dauerhaftigkeitsstrategie. Java-Verzeichnis-`force(true)` scheitert in
   dieser Windows-Umgebung mit `AccessDeniedException`. Ein erfolgreicher
   Datei-Rename allein ist kein geprüfter Crash-Recovery-Nachweis.
   Welt-Lifecycle-Locks, MCC-Entscheidung und Wiederanlauf müssen gemeinsam
   angebunden werden; eine alte Tessera-Operation darf nicht später eigenmächtig
   nach einer MCC-Recovery fortgesetzt werden.

Diese Punkte sind Implementierungslücken in Tessera, **keine fehlenden
Nutzerpfade oder externen Testartefakte**. Vertrag 1 ist daher nicht freigegeben.

## Eigene native Tests

`smoke-tests/native-player-restore/run.mjs` startet ausschließlich einen neuen
disponiblen Server unter `build/native-restore-smoke-<timestamp>`, auf einem
freien Loopback-Port, mit dem separaten Testplugin und zwei echten
Offline-Protokollclients. Produktive Welten und vorhandene Saves werden nicht
verwendet. Die Clients rendern kein Vanilla-Fenster, bedienen aber Login,
Konfiguration, Keepalive, Chunk-Batches und die echten 26.3-Teleportbestätigungen.

Der Test erreicht native Entity-Transfers, Statistik-/Fortschritts-Resets,
Spieler-NBT und alle drei Snapshot-Schreiber. Er prüft getrennte Regionen und
Dimensionen, wiederholte Ersetzung, Inventar-/Endertruhen-Wrapperidentität,
XP, Gesundheit, Hunger, Attribute, Effekte, Spielmodus, PDC, Rezepte,
entfernte Statistikwerte, frische Defaults, Teleport-Veto und erneute Saves.
Die Snapshot-NBT-Dateien werden gelesen und ihre XP-Werte geprüft. Empfangene
Client-Reset-Pakete und Login-/Kick-/Disconnect-Zähler stehen im Ergebnis-JSON.

Wichtig: Der Test ruft den nativen **Komponentenpfad** auf. Er gibt ausdrücklich
`publicTransactionAccepted: false` und `contractVersion: 0` aus. Ein Test dieses
Pfades ist **keine** Abnahme des öffentlichen Prepare/Apply/Complete-Vertrags.
Auch erneutes Einspielen eines In-Memory-Zustands ist kein nachgewiesener
Rollback einer veröffentlichten Dateitransaktion.

## Build und Wiederholung

```powershell
.\gradlew.bat buildTessera :test-plugin:jar --console=plain --max-workers=2 --no-parallel
node smoke-tests/native-player-restore/run.mjs "<Java-25-Verzeichnis>/bin/java.exe"
```

Die native Serverarbeit wird über eigene Minecraft-/Server-Feature-Patches
gesichert; der Build wendet sie erneut an. Die ausführbare Prüf-JAR ist
`build/libs/tessera-server-26.3.build.010-alpha.jar`.
Abschließende Build-/JAR- und Prüfnachweise stehen in
[BUILD-26.3-010.md](BUILD-26.3-010.md).

## MCC-/MVE-Folgeabnahme – nicht durchgeführt

MCC 0.7.5 erkennt hier weiterhin Vertrag 0. Es wurde kein neuer API-Pfad und
kein erzwungener Reconnect als Seamless-Erfolg eingeführt. Bei künftig
vollständig eingehaltenem Vertrag 1 sollte der vorhandene MCC-Adapter genügen;
das ist mit diesem Prüfbuild noch nicht gemeinsam nachgewiesen.

Offen bleiben die komplette MCC-/MVE-Integration einschließlich wiederholtem
Load/Resave, echtem Rollback und freigegebenen Logins; MVE-Vanilla-/Lifetime-
Statistiktrennung, Primary-World-/Baseline-Bindung; NORMAL/SINGLE_BIOME und
Single Structure samt Nether/End nach Reset und neuer Chunk-Generierung;
geöffnetes MCC-Backpack sowie TAB-/LuckPerms-Zusammenspiel. Native
Komponententests ersetzen diese Plugin-Abnahme nicht.
