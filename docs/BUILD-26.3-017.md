# Build 017 – Regionssichere NBT-Befehle

Stand: 06.10.2026. Tessera `26.3-017-beta`, Minecraft/API `26.3`, Java 25.
Der empfohlene Teilumfang von `/data` ist implementiert und nativ geprüft.
MCC-/MVE-Produktcode, Sinopia-Basis und Abhängigkeitsversionen wurden nicht geändert.

Dieser Bericht dokumentiert die ursprüngliche Datei vom 06.10.2026 mit dem unten
genannten Hash. Der [Nachtrag vom 07.10.2026](TPS-PREGEN-STATUS-2026-10-07.md)
beschreibt die danach gebaute 017-JAR mit TPS-Kapazität, kompakter
Pregen-Anzeige und deren eigener nativer Abnahme. Die damaligen NBT-/Restore-
Nachweise sind historische Ergebnisse, keine erneuten Tests der neuen Datei.
Die danach geprüfte 017-JAR mit korrigiertem Windows-Konsolenshutdown und ihrem
eigenen Hash ist im [Shutdown-Nachtrag](CONSOLE-SHUTDOWN-2026-10-07.md) dokumentiert.
Die neueste Datei enthält zusätzlich den unten dokumentierten
[Whitelist-Nachtrag](#whitelist-nachtrag-vom-07102026). Frühere native Abnahmen
gelten jeweils für ihren eigenen Hash, nicht automatisch für diese neue Datei.

## Implementierter Umfang

- `get` für Blockentities, Entities einschließlich Spieler und gemeinsamen Storage,
  mit Vanilla-Pfaden, Skalierung und tatsächlichen numerischen Ergebnissen.
- `merge`, `remove`, `modify` ausschließlich für geladene, regionslokale
  Blockentities und Storage. Vanilla-Listen-/Compound-Operationen sowie
  `value`, `from`, `string` und die 26.3-`compute`-Provider bleiben erhalten.
- Entity-Serialisierung und Block-NBT-Zugriff nur auf dem tatsächlichen Besitzer;
  entfernte Entities und ersetzte Blockentities werden vor dem Zugriff abgewiesen.
- Konsole/RCON: Zielauflösung vor Live-Selektorfiltern, Weiterleitung auf den
  Besitzer, erneute Prüfung bei Ownership-Wechsel, vollständiges Abwarten
  zusätzlicher Weiterleitungen außerhalb des Global-Ticks. Auch `minecraft:data`.
- Geschützte NBT-Selektoren einschließlich vorgeschalteter `execute`-Modifier;
  begrenzte Spieler-Suchen verwenden die lokale Spieler-Liste. Ein fremdes
  `@s` darf keine Live-Filter auswerten.
- Atomare Storage-Mutationen mit demselben Container-Lock für Encoding,
  abgetrennten NBT-Kopien und unveränderlichen Suggestion-Snapshots. Fehler
  publizieren keine Teiländerungen. Keine Mehrdokument-/Mehrwelttransaktion.
- `execute store result/success block|storage` prüft auch beim Callback den
  Zugriff; `execute if/unless data` verwendet die geschützten Accessors.

Bewusst **nicht** aktiviert: beliebige Entity-NBT-Änderungen, darunter
`execute store ... entity`, oder Kopien zwischen räumlicher Quelle und Ziel
auf verschiedenen Regionsbesitzern. Eine Storage-Quelle ist regionsübergreifend
lesbar; eine lokale räumliche Quelle kann in Storage kopiert werden.
Spieler-/Block-/Pluginquellen erhalten durch Dispatch keine zusätzliche Ownership.
Die [API-Referenz](api/commands.md#nbt-befehle) beschreibt Berechtigungen,
Lebenszyklus, Fehler und den erlaubten Kontext.

## Quellstand und dauerhafte Patches

- Branch: `ver/26.3.x`.
- Root-Basiscommit: `ff02f0ed13d2ed5c43cedc9f24b3ce2e9e08181a`, **zusätzlich**
  die lokalen Build-017-Änderungen. Dieser Basiscommit allein enthält Build 017
  nicht. Kein Commit oder Push im Hauptrepository.
- Sinopia-Snapshot unverändert: `cebdb10d9418432a18b920f7f2e902a8d03a43f4`.
- Nach finaler erneuter Anwendung: Minecraft-HEAD
  `c25926e902ddff4e4569ea77117d8d308fc27a6d`, Server-HEAD
  `d14265be5262bad35488c944392952a2886b09b0`.
- [Minecraft-Patch 0056](../folia-server/minecraft-patches/features/0056-Restore-region-owned-data-commands-and-atomic-comman.patch):
  `29a7661ad65ebe94666e2efb8af90a307feab3748f10ec25094e6abe75f51da2`.
- [Server-Testpatch 0045](../folia-server/paper-patches/features/0045-Test-owned-NBT-commands-and-atomic-shared-storage.patch):
  `f1037c27c7e93a587f46086724b3520c3b2289fd9247f7cbed608823e33e41f3`.

Die beiden Feature-Commits wurden nur in den generierten Arbeitsrepositories
angelegt, anschließend über die bestehenden Rebuild-Aufgaben exportiert und
mit `buildTessera` erneut angewendet. Generierte Arbeitsquellen sind danach sauber.
Frühere Gameplay-/Restore-/Paper-Korrekturen bleiben enthalten.

## Build und Qualitätsprüfungen

Ausgeführt unter Java 25.0.3, Windows 11, Gradle 9.8.0:

```powershell
.\gradlew.bat :folia-server:rebuildMinecraftFeaturePatches :folia-server:rebuildPaperServerFeaturePatches --console=plain --no-configuration-cache --max-workers=2 --no-parallel
.\gradlew.bat buildTessera --console=plain --no-configuration-cache --max-workers=2 --no-parallel
```

Finaler Standardlauf: **BUILD SUCCESSFUL**, 4 Minuten 42 Sekunden.
Alle Patchschichten erneut angewendet; vorhandene Test-, Checkstyle- und
Bad-Call-Prüfungen bleiben aktiv. Die vollständige Server-Testreihe wurde im
vorherigen kompilierten Durchlauf frisch ausgeführt und im identischen
Folgelauf regulär aus dem Gradle-Up-to-date-Stand übernommen.

| Prüfung | Ergebnis |
| --- | --- |
| Server-Suite | 10.241 Fälle, 87 bestehende Skips, 0 Fehler/Failures |
| API-Suite | 529 Fälle, 2 bestehende Skips, 0 Fehler/Failures |
| Neue NBT-Regressionen | 14 bestanden: Baum/Grammatik, Referenz-Isolation, fehlgeschlagene Publikation, 1.000 parallele Mutationen, Suggestions, Snapshot plus spätere Save-Arbeit, Owner-/Selektorprüfungen |
| Vollständige API-Beispiele | 10 Klassen mit Java 25 gegen die tatsächliche API kompiliert |
| Build-/Patch-Schutz | 22 Checks bestanden |
| Dokumentationsvalidator-Tests | 57 bestanden; API-Metadaten, Artikel-/Ankerlinks und Runner-Syntax geprüft |

19 API-Artikel mit 51 Artikel-/Ankerlinks und 62 lokale Repository-Links in
README, Workflow, Release und Buildbericht geprüft. Der veröffentlichte
JSON-Nachweis stimmt mit den tatsächlichen nativen Ergebnisdateien überein.

Ein anfänglicher kombinierter Aufruf `buildTessera :test-plugin:jar` erreichte
bereits erfolgreiche Tests/Qualitätsprüfungen im kompilierten Build, scheiterte
aber anschließend an Gradles impliziter Abhängigkeit zwischen äußerem
API-Compile und Patch-Aufgaben. Der vorgesehene **separate** `buildTessera`-Aufruf
ist erfolgreich und baut das Testplugin bereits intern mit. Keine Prüfung
deaktiviert und keine Assertions abgeschwächt. Ein früherer gefilterter Testlauf
leerte die bestehende JUnit-Pregen-Suite; die ungefilterte Gesamtsuite ist bestanden.
Die erste native Fixture verwendete eine ungültige 26.3-Compute-ID; die korrigierte
Fixture prüft einen echten `minecraft:constant`-Provider statt eines Placeholders.

Bestehende Javadoc-/Deprecation-/FFM- sowie Windows-OSHI-Warnungen bleiben erhalten.
Der binäre Logo-Patch bleibt trotz der bekannten Text-Rebuild-Diagnose angewendet.
Gesamt-Changelog-Validierung ist weiterhin durch den unveränderten historischen
Eintrag `docs/builds/26.2/0.0.1.md` eingeschränkt; Build 017 und seine Links sind
separat geprüft. Historische Releases wurden nicht umgeschrieben.

## Finale ausführbare JAR

```text
build/libs/tessera-server-26.3.build.017-beta.jar
```

Größe: **55.612.829 Bytes**. SHA-256:

```text
640d81ad7034809b4b9b0ccb9a7569b29ff1e8820c1160f9c7c8e91501860273
```

## Native Abnahme auf dieser Datei

[NBT-Runner](../smoke-tests/data-command/README.md), frische isolierte Fixture
`data-command-1791316812918`, Minecraft-Port `61360`, RCON-Port `61361`,
vier Regions-Threads, zwei verbundene Offline-Protokollclients.

| Prüfung | Tatsächlich bestanden |
| --- | --- |
| Besitzer | Chunks `0,0` und `128,0` in `tessera_smoke:data` auf zwei verschiedenen tatsächlichen Tick-Regionen |
| RCON | Acht reale Socket-Abfragen, Block-/Entity-/Storage-Lesen und Block-/Storage-Schreiben einschließlich namespaced Aliase; tatsächliche Antworten statt bloßer Task-Annahme |
| Freeze | Block-Merge/Get/Pfad/Skalierung/Set/Remove/lokales From sowie bounded/UUID-Entity-NBT-Abfragen auf beiden Besitzern bei angehaltener Simulation |
| Storage | Listenvarianten, Compound-Merge, Substring, Compute, lokale Blockquelle und echte `execute store`-/`if data`-Ergebnisse |
| Parallelität | 300 tatsächliche Befehlsmutationen von zwei Region-Threads; beide Felder bleiben exakt bei 150 |
| Ablehnung | Fremde/ungeladene Quellen, große regionsübergreifende und unbeschränkte NBT-Suchen, frühere Execute-NBT-Filter, Entity-Mutationen, fremdes gefiltertes `@s` und stale Block-Handle; Ziel unverändert |
| Spieler | Name-/`@s`-Abfrage, begrenzter Spieler-NBT-Filter auf dem Besitzer, tatsächlicher Nether-Transfer, korrektes Dimension-/Store-Ergebnis; fremder Spielerzugriff abgewiesen |
| Save | Tatsächlicher Level-Root-Snapshot; Runtime-Welt-Unload mit Save/Reload erhält beide Commandblock-NBT-Zustände |
| Neustart | Gemeinsamer CommandStorage erhält Marker `17017`, entfernte Felder bleiben entfernt und parallele Endwerte erhalten |

Beide Serverphasen: Exitcode **0**, jeweils **2 Logins, 0 Kicks, 0 Disconnects**
bis zum bestätigten Ergebnis. Der ausdrücklich beabsichtigte Neustart zwischen
den Phasen beendet deren Verbindungen; er wird nicht als Seamless-Load ausgegeben.
Kein Region-Tick-Ausfall, Cross-Region-Threadfehler, Command-Exception oder
ConcurrentModificationException im nativen NBT-Lauf.

### Bestehender Spieler-Restore als Regression

Zusätzlich der unveränderte Runner `smoke-tests/native-player-restore/run.mjs`
im Modus `native-restore-transaction`, Fixture `native-restore-smoke-1791316815656`,
Port `61370`, **derselbe JAR-Hash**. Ergebnis: Exitcode **0**, Vertrag **1**,
`nativeComponentChecksPassed=true`, `publicTransactionAccepted=true`.

Wiederholtes Prepare/Apply/Complete und Resave, frischer Rollback, Transfer-Veto,
frische Spieler, Writer-Drain/Cancellation, echte Statistik-/Windows-Publikationsfehler,
verbundene tote/schlafende Spieler, erhaltene Desert-/Nether-Generatorbindungen
und anschließender Runtime-Snapshot bestanden. Erwartete Fehler-Injektionen
werden als solche geprüft; dies ist keine Behauptung einer fehlerfreien Fehlerfall-Konsole.

| Client | Logins | Kicks | Disconnects vor Ergebnis | Teleports | Statistik-Nullresets | Advancement-Resets |
| --- | --- | --- | --- | --- | --- | --- |
| RestoreOne | 1 | 0 | 0 | 35 | 8 | 12 |
| RestoreTwo | 1 | 0 | 0 | 38 | 8 | 12 |

## Nachweise, Bereinigung und Prüfgrenzen

Der kompakte [maschinelle Nachweis](test-evidence/26.3-017/native-regressions.json)
enthält Hash, Testzahlen und konkrete native Ergebnisse. Nach der angeforderten
Bereinigung liegen Buildlogs unter `build/reports/build-logs/`, native JSON-/Log-/
Prüftext-Nachweise unter `build/reports/native-fixtures/<Fixture-Name>/`.
Die wegwerfbaren Welten, Serverkopien und alten JARs werden nicht als dauerhafte
Saves aufbewahrt. Aktueller Sinopia-Snapshot/-Arbeitsbereich, Quellen, Patches,
finale 017-JAR und kompakte Prüfnachweise bleiben erhalten.

Bereinigung abgeschlossen: **62 geprüfte Ziele**, **11.800.578.453 Bytes**
(rund **11,0 GiB**) entfernt, darunter sieben ältere JARs und drei saubere,
bereits exportierte Sinopia-Arbeitskopien. Keine produktiven Welten oder
uncommittierten Arbeitsquellen gelöscht. Die wegwerfbaren Fixtures sind nur
durch erneutes Ausführen ihrer Runner reproduzierbar; ihre kompakten
Ergebnis-/Logdateien wurden vor dem Löschen archiviert.

Protokollclients belegen Verbindungs-/Transferkontinuität, keine visuelle Vanilla-
Client-Abnahme. Die reale MCC-/MVE-/TAB-/LuckPerms-Integration wurde für diesen
Befehlsausbau nicht erneut ausgeführt. Pregen-/Sand-Duper-Gameplay wurde hier
nicht nativ wiederholt; deren vorhandene Tests und Patches bleiben unverändert.
Der Befehlsausbau benötigt keine neue MCC-Schnittstelle, ersetzt aber keine
Abnahme mit dem tatsächlich eingesetzten Plugin-/Datapack-Stack.

## Whitelist-Nachtrag vom 07.10.2026

Der synchrone Profilabruf bei `whitelist add/remove` konnte den Global-Tick
im HTTPS-Handshake blockieren. Fehlgeschlagene Dienstanfragen erschienen zudem
als `That player does not exist`. Cache und Netzwerk normalisierten den Namen
bereits vor diesem Fix; ein erfolgreicher Wiederholungsversuch mit anderem
Anfangsbuchstaben beweist deshalb keinen schreibweisenabhängigen Mojang-Endpunkt.

Unbekannte Namen werden jetzt durch zwei begrenzte Hintergrund-Worker mit
zehn Sekunden Frist vorbereitet. Die eigentliche Befehlsausführung erfolgt
anschließend im passenden Tick-Kontext, für Spieler auf dem aktuellen
Entity-Besitzer. RCON wartet außerhalb des Global-Ticks auf die endgültige
Antwort. Bekannte Namen sind case-insensitive; vorhandene Whitelist-Einträge
reichen für `remove` auch ohne Netzwerk aus. Bei `add` bestimmt ein alter
Whitelist-Name ohne gültigen Cache nicht ungeprüft eine neue Besitzer-UUID.
404/fehlende Profile bleiben echte Vanilla-Not-found-Fälle; Dienstfehler,
Rate-Limits und Timeout erhalten eine separate englische Fehlermeldung.
Keine negativen Cache-Einträge, keine spätere Whitelist-Mutation nach Timeout,
Disconnect oder abgewiesener Berechtigung. Paper-Profilhooks und
Whitelist-Vetos werden berücksichtigt. Der genaue Dispatch-/Funktionsumfang
steht in der [Befehlsreferenz](api/commands.md#whitelist-und-profilabfragen).

### Patches und Build

- Root-Basiscommit: `07c34c0257275c4dbc23c081d95e9538196c2916`, zusätzlich die
  lokalen Whitelist-Patches, Dokumentation und Testfixtures. Kein Root-Commit/Push.
- [Minecraft-Patch 0059](../folia-server/minecraft-patches/features/0059-Resolve-whitelist-profiles-off-tick-threads-with-acc.patch),
  SHA-256 `5d657c089f37fa53fd48c31117b919a74d85268edebc644809ff1ffc971e232c`.
- [Server-/Testpatch 0048](../folia-server/paper-patches/features/0048-Preserve-profile-hooks-and-test-nonblocking-whitelis.patch),
  SHA-256 `84c0b9b8a5e0f3e873d02ad7a1f79647be050c68be1b032c8c98766e942610be`.
- Nach erneuter Anwendung: Minecraft-HEAD `a44249c6b9c44b67f56301e0a0416a394e3b1a2e`,
  Server-HEAD `752c7e85fb7fa82d716bf41ac8834aa3f23b9c91`.
- Vollständiges `buildTessera`: **BUILD SUCCESSFUL**, 9 Minuten 26 Sekunden,
  Java 25.0.3/Gradle 9.8.0. Alle Patches erneut angewendet, vorhandene
  Qualitätsprüfungen aktiv. Server-Suite frisch: **10.285 Fälle**, 87 bestehende
  Skips, 0 Fehler/Failures. API-Suite unverändert/up-to-date: 529 Fälle,
  2 Skips, 0 Fehler; Checkstyle-Helfer: 3 Fälle, 0 Fehler.
- Whitelist-Regressionen: **19 bestanden**, einschließlich echter lokaler
  Authlib-HTTP-Abfragen mit mehreren Schreibweisen, 404/429/503, verzögerten
  Antworten, Abbruch eines gemeinsamen Requests, Ablauf ohne spätes Ergebnis,
  Cache-Expiry, falscher Antwortidentität, veraltetem Whitelist-Namen und Veto.
- API-Dokumentation: 19 Artikel/52 Links gültig; 35 Validator-Tests bestanden.
  Keine öffentliche Java-Signatur geändert, die vorhandenen Beispiele bleiben erhalten.

### Neueste ausführbare Datei und native Prüfung

`build/libs/tessera-server-26.3.build.017-beta.jar`, **55.630.688 Bytes**,
SHA-256 `262768301132e194c35aeb4cfff9cb84a4c6d676f03a92934782dab0edaa7196`.

Der [isolierte Runner](../smoke-tests/whitelist-command/run.mjs) startete genau
diese JAR auf freien Loopback-Ports mit zwei verbundenen Protokollclients,
vier Regions-Threads und einem lokalen HTTP-Profilserver. Der echte Authlib-
Client, Cache, Brigadier, Whitelist-Datei, Events und Scheduler wurden verwendet;
es wurde keine Produktwelt oder echte Spielerdatei als Fixture genutzt.

```powershell
node smoke-tests/whitelist-command/run.mjs 'C:/Program Files/Java/jdk-25.0.3/bin/java.exe' build/libs/tessera-server-26.3.build.017-beta.jar
```

**28 native Assertions und acht tatsächliche RCON-Socketantworten bestanden**:
kleingeschriebenes uncached Add, andere Schreibweise desselben UUID-Ziels,
namespaced Aliase/`execute`, case-insensitive Remove, echter HTTP 404/503,
erneuter Versuch ohne negativen Cache, tatsächlicher Socket-Read-Timeout und
ein verspäteter Plugin-Override nach abgelaufener Frist. Bei `tick freeze`
liefen während fünf-/zehnsekündiger Wartephasen weiterhin mindestens 70/140
Global-Ticks. Keine Watchdog-Warnung, Cross-Region-Ausnahme oder Command-Exception.
Ein Spieler wechselte während der Abfrage tatsächlich in den Nether und erhielt
die fortgesetzte Ausführung auf seinem aktuellen Besitzer. De-OP während einer
Abfrage verhinderte das Add. Ein anderer Client wurde **absichtlich** getrennt:
kein nachträglicher Eintrag; der verbleibende Client blieb verbunden.

Native Bilanz: **2 Logins, 1 absichtlicher Kick/Disconnect, 0 unbeabsichtigte
Verbindungsabbrüche**, normaler Shutdown mit Exitcode **0**. Auch echte stdin-
Konsoleneingabe wurde geprüft. Die Profile stammen aus der kontrollierten lokalen
HTTP-Fixture, nicht aus einer Verfügbarkeitsmessung des echten Mojang-Dienstes.
MCSM, euer tatsächlicher Plugin-Stack, MCC/MVE und eine neue visuelle Vanilla-
Client-/Windows-Terminal-Abnahme wurden auf dieser Datei nicht separat getestet.
Ihre früheren Nachweise werden nicht auf den neuen Hash übertragen.

Der [kompakte Nachweis](test-evidence/26.3-017/whitelist-command.json) verweist
auf die archivierten Rohdaten unter
`build/reports/native-fixtures/whitelist-command-20261007/` und die Buildlogs
unter `build/reports/build-logs/`. Buildnummer/Channel bleiben **017/beta**;
Minecraft/API, Java-Ziel, Sinopia, Abhängigkeiten und MCC-/MVE-Code unverändert.

Nach hashgeprüfter Archivierung wurden ausschließlich die eigene wegwerfbare
Testwelt, ihre Cache-/Plugin-Kopien und Spielerdaten entfernt: **181.830.534 Bytes**
(rund 173 MiB). Rohlogs, Prüftexte, JUnit-XML, UUID-Listen und Ergebnis-JSON bleiben
erhalten. Die gelöschte Fixture kann mit dem Runner neu erzeugt werden;
produktive Daten wurden nicht berührt.
