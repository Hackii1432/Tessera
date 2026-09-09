# Tessera 26.2-017 – Live-Snapshots, Portale und Respawn

## Bezugsstand

- Ausgang: Tessera 26.2-016, Commit `9b2f0ff`, Branch `ver/26.2.x`.
- Implementiert ausschließlich im Tessera-Repository; MCC, MVE und MVCE wurden
  nicht verändert.
- Mindestbuild für die neue Snapshot-API und die Gameplay-Korrekturen:
  **Tessera 26.2-017**.

## Zuordnung der Anforderungen

| Bereich | Ergebnis | Patch |
| --- | --- | --- |
| Frische Multiwelt-Snapshots | implementiert | API `0009`, Server `0019`, Minecraft `0027` |
| Nether-/End-Portalrouting | implementiert | Minecraft `0028` |
| Respawn-/Post-Respawnereignisse | implementiert | Minecraft `0029` |
| Verzögerter Ankerverbrauch | implementiert | Minecraft `0029` |
| Load-/Unload-Identität | bereits in 016 vorhanden, erneut geprüft | API `0006`–`0008`, Server `0009`–`0015`, Minecraft `0014`–`0017` |
| Loginvalidierung in zwei Phasen | bereits vorhanden | `ServerLoginPacketListenerImpl`, `ServerConfigurationPacketListenerImpl` |
| Structure-Postprocessing | bereits in 016 vorhanden | Minecraft `0025` |

## Öffentliche Snapshot-API

```java
CompletionStage<WorldSnapshotResult> snapshotWorldsAsync(
    List<World> worlds,
    Path snapshotPath
);
```

`WorldSnapshotResult` stellt `successful()`, `message()` und
`snapshotPath()` bereit. `snapshotPath()` ist immer der normalisierte absolute
angefragte Pfad. Zusätzliche Diagnose liefern `status()` und `cause()`.

Jeder Aufruf ist frisch. `cloneWorldAsync` und dessen
`templateSnapshots.computeIfAbsent(...)` bleiben ausschließlich für statische,
spielerfreie Vorlagen erhalten.

### Konsistenz- und Threadvertrag

1. Alle Quellen werden unter sortiert erworbenen World-Write-Locks geprüft.
2. Die komplette Weltgruppe wechselt atomar auf dem Global-Region-Thread von
   `ACTIVE` nach `SNAPSHOTTING`.
3. Normale Ticks und neue API-/Chunk-/Teleport-Aufnahmen stoppen. Bereits
   angenommene Region-, Chunk- und Paketworkqueues werden weiter abgearbeitet.
4. Barrieren werden hinter alle bekannten Regionsqueues gesetzt.
5. Verbundene Spieler werden über ihren übertragbaren EntityScheduler auf dem
   aktuellen Besitzerthread gespeichert: Player-NBT, Statistiken und
   Advancements. Ein Logout speichert vor der Scheduler-Stilllegung.
6. Weltleveldaten, Chunks, Entities, POIs und Region-Storage werden genau einmal
   über `flushRuntimeWorldSnapshot` gespeichert und vollständig geflusht.
7. Erst danach kopieren Lifecycle-I/O-Worker die Dateien. Kein Region- oder
   Global-Thread wartet synchron auf eine andere Region oder auf die Großkopie.
8. Nach Erfolg oder Fehler wechseln alle Quellen zurück nach `ACTIVE` und die
   World-Locks werden freigegeben.

Startup-Welten dürfen gelesen werden. Ihr Schutz vor Unload/Unregister bleibt
unverändert.

### Ausgabe und Dateivertrag

```text
snapshotPath/
  runtime/                 # bestehender MCC-Inhalt bleibt unverändert
  worlds/0/                # worlds.get(0)
  worlds/1/                # worlds.get(1)
  players/data/
  players/stats/
  players/advancements/
```

Gesichert werden insbesondere `region`, `entities`, `poi`, `data/minecraft`,
`data/paper`, PDC/Metadaten, `level.dat`, UUID-Daten und `datapacks`.
`session.lock`, fremde verschachtelte Dimensionen und world-lokale
Spielerordner werden nicht in einen Weltslot kopiert. Leere Player-Zielordner
werden angelegt. Entity-NBT, Position und Motion werden nicht umgeschrieben;
es gibt keinen zweiten Entity-Save.

Die Ausgabe entsteht zuerst in `.tessera-snapshot-<uuid>`. Erst nach kompletter
Kopie werden `worlds/` und `players/` veröffentlicht. Bereits vorhandene Ziele,
Quell-/Zielüberlappung, Symlinks, Junctions und Sonderdateien werden abgelehnt.

### Fehler und Abbruch

Öffentliche Statuswerte sind `SUCCESS`, `SERVER_STOPPING`, `INVALID_REQUEST`,
`SOURCE_NOT_LOADED`, `SOURCE_BUSY`, `TARGET_EXISTS`, `SAVE_FAILED`,
`COPY_FAILED`, `TIMEOUT`, `CANCELLED` und `CLEANUP_FAILED`.

Regions- und Playerbarrieren laufen nach 30 Sekunden in `TIMEOUT`. Ein bereits
gestarteter Region-Storage-Flush wird nicht hart unterbrochen; Tessera beendet
ihn und stellt erst danach die Welt wieder aktiv. Das gilt auch bei Abbruch des
vom Plugin gesehenen Futures. Teilziele werden entfernt und nie als Erfolg
gemeldet. `runtime/` gehört weiterhin ausschließlich MCC.

## Asynchrone Portale

Der Async-Netherpfad ruft jetzt vor Suche und Ablösen des Portalteilnehmers
`EntityPortalReadyEvent` und anschließend `PlayerPortalEvent` beziehungsweise
`EntityPortalEvent` auf. Die tatsächliche Event-Zielwelt, Suchradius,
Erzeugungsradius und `canCreatePortal` werden in die native regionsichere
POI-/Portalsuche übernommen.

Der End-Eingang verwendet ebenfalls das Bukkit-Portalziel vor Plattform und
Platzierung. Der End-Ausgang eines Spielers bleibt der Vanilla-Respawnpfad und
erhält `RespawnReason.END_PORTAL`, persönlichen Bett-/Anker-Vorrang und den
normalen Respawnvertrag.

Die Entity bleibt während der asynchronen Suche in ihrer Ursprungswelt. Erst
ein gültiges Ergebnis wird auf ihrem übertragenen Besitzer-Scheduler erneut
validiert und danach abgelöst. Abbruch, kein Portal bei `canCreate=false`,
Logout sowie eine fehlende, entladene oder unter demselben Key ersetzte
Zielwelt lassen die Entity unverändert zurück. Nach tatsächlicher Platzierung
wird genau ein `PlayerChangedWorldEvent` auf der Zielregion ausgelöst.

Vanilla-Dimensionsskalierung, Weltgrenze, Portalrelativposition, Cooldown,
Portalton/-ticket und Endplattform bleiben erhalten.

## Respawn und Anker

Der Folia-Asyncpfad löst jetzt genau ein `PlayerRespawnEvent` auf der aktuellen
Spielerregion aus. Übergeben werden Ursache, Bett-/Ankerflags und
Missing-Respawn-Block-Flag. Eine Plugin-Zieländerung wird vor dem Entfernen des
Spielers validiert und ist die tatsächlich verwendete Zielposition/-welt.

Nach dem Einsetzen des lebenden Spielers wird auf dessen Zielregion genau ein
`PlayerPostRespawnEvent` mit tatsächlicher Position ausgelöst. Erst danach läuft
der interne Completion-Callback. EntityScheduler-Aufgaben aus dem Post-Event
sind damit an den lebenden, übertragenen Scheduler gebunden.

Der von `findRespawnAndUseSpawnBlock` gelieferte verzögerte Anker-Callback wird
nur bei unverändertem Eventziel auf der Besitzerregion des Ankers ausgeführt.
Er liest beim Verbrauch den aktuellen Blockzustand, zieht höchstens eine
vorhandene Ladung ab und kann einen neueren Ladungsstand nicht mit einem alten
BlockState überschreiben. Bei geändertem Eventziel erfolgt kein Verbrauch.
Eine instanzgebundene, atomare Reservierung verhindert, dass parallele Respawns
dieselbe letzte Ladung auswählen. Abbruch, ungültiges Eventziel, Logout und
Scheduler-Stilllegung geben die Reservierung idempotent frei. Dies gilt für
manuellen und sofortigen Tod-Respawn sowie `END_PORTAL`.

## Bereits vorhandene Garantien und Grenzen

- `loadWorldAsync(NamespacedKey)` prüft den stabilen Key sowie
  `data/minecraft/world_gen_settings.dat` und `data/paper/metadata.dat` vor der
  Registrierung. Mehrere Kopien müssen unterschiedliche Offline-UUIDs besitzen;
  Tessera korrigiert Entity-UUID, Position oder Motion nicht.
- Ein erfolgreicher Load ist erst nach Registrierung, Spawnchunk-Vorbereitung,
  Aktivierung und `WorldLoadEvent` abgeschlossen.
- Ein erfolgreicher Runtime-Unload ist erst nach Regionsbarrieren,
  Storage-Flush/-Close und Entfernung aus allen drei Registrierungen
  abgeschlossen. Startup-Welten bleiben geschützt.
- `PlayerConnectionValidateLoginEvent` wird sowohl in der authentifizierten
  Loginphase als auch beim Abschluss der Konfigurationsphase ausgelöst.
- Der Snapshot-Playerflush bildet die beim Flush erfassten Verbindungen ab.
  Eine darüber hinausgehende allgemeine öffentliche Login-/Logout-Drain-API
  wurde nicht eingeführt; MCCs bereits vorhandene zweiphasige Austauschsperre
  bleibt für das Öffnen/Schließen des Austauschfensters verantwortlich.
- Nicht im Tessera-Workspace verfügbare MCC-Protokollclient-Fixtures können hier
  nicht als ausgeführt ausgewiesen werden. Die MCC-Live-Matrix bleibt daher ein
  erforderlicher Integrationsnachtest des erzeugten 017-Artefakts.

## Lokale Regressionen

Die Tessera-Tests prüfen:

- Ergebnisvertrag und Reflection-taugliche Snapshot-Signatur;
- Erhalt von `level.dat`, UUID und `data/paper/metadata.dat`;
- Ausschluss von `session.lock` und fremden Dimensionbäumen;
- Erzeugung leerer Playerverzeichnisse;
- vorhandene MCA-Strukturvalidierung und stabile Runtime-Identitäten;
- vollständiges erneutes Anwenden aller Patchserien;
- Kompilierung von API und Server inklusive der Minecraft-Pfade.

Der finale lokale Lauf von `RuntimeWorldApiTest` und `NormalTestSuite` bestand
mit **2.442 Servertests**. `applyAllPatches --rerun` und der Paperclip-Build
waren ebenfalls erfolgreich. Die ausgegebenen Compilerhinweise betreffen
vorhandene veraltete Paper-/Bukkit-APIs; es gab keine Compilerfehler.

Physische Nether-/Endreisen, echter Logout während des Dateiaustauschs,
Ankerkonkurrenz und harter Prozessabbruch benötigen weiterhin den beschriebenen
MCC-Integrationstest mit realem Server und Protokollclient.

## Anwenden, prüfen und bauen

Die Änderungen liegen vollständig in den Patchserien. Nach einem Checkout des
Branches genügt daher:

```powershell
./gradlew.bat applyAllPatches --no-daemon --console=plain
./gradlew.bat :folia-api:test --tests io.papermc.paper.world.RuntimeWorldApiTest :folia-server:test --tests org.bukkit.support.suite.NormalTestSuite --no-daemon --console=plain
./gradlew.bat createPaperclipJar --no-daemon --console=plain
```

Tatsächlich gebaut und lokal geprüft wurde:

```text
build/libs/tessera-server-26.2.build.017-stable.jar
SHA-256: e35939aaeeffa242e09f03cf124d89979cac3e36d492657a9b84d2561f243402
```

## MCC-Folgeauftrag

> Stelle die Tessera-Mindestversion auf 26.2-017. Ersetze die negative
> Snapshot-Capability-Erwartung durch eine optionale Reflection-Anbindung an
> `Bukkit.getRuntimeWorldManager().snapshotWorldsAsync(List<World>, Path)`.
> Werte am Ergebnis `successful()`, `message()` und `snapshotPath()` aus und
> veröffentliche MCCs Manifest/Slot nur bei `successful()==true`. Übergib alle
> Challenge-, Farm- und Arenawelten in verbindlicher Manifestreihenfolge und
> stelle vor dem Aufruf sicher, dass `snapshotPath/worlds` und
> `snapshotPath/players` fehlen; `snapshotPath/runtime` darf bereits den
> MCC-Checkpoint enthalten. Prüfe A/B-Frische, Player-NBT/Stats/Advancements,
> Kiste/Mob/Item/POI/PDC, exakte Entity-Position/Motion, Logout-Race sowie
> Wiederladen unter neuen Keys. Führe danach echte Nether-Hin-/Rückreisen,
> End-Ein-/Ausgang und Tod-/Bett-/Ankerrespawn aus und protokolliere Eventanzahl,
> Reihenfolge, Thread, tatsächlichen Welt-Key, Position und Ankerladungen.

## MVE-Folgeauftrag

> Behandle Tessera 26.2-017 als Plattform mit nativer regionsicherer
> Portalzielwahl. Verwende die normalen `PlayerPortalEvent`-/
> `EntityPortalReadyEvent`-Zuordnungen für die drei Runtime-Welten und entferne
> keinen nachträglichen Reparaturteleport aus einer Standarddimension. Halte
> den dauerhaften, idempotenten MVE-Weltersatz-/Epochenvertrag weiterhin getrennt
> von Tessera-Snapshots und MCCs Sessionjournal. Teste insbesondere, dass eine
> Zugangssperre nur beabsichtigte Reisen abbricht und keine Zielzuordnung auf
> `minecraft:the_nether` oder `minecraft:the_end` zurücksetzt.
