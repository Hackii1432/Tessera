# Paper-Beta-Integration – Tessera 26.3-011-beta

## Grundlage und Umfang

Arbeitsbasis: Branch `ver/26.3.x`, Root-Commit
`3a6460634b2bf4b3b98a9fcf77e54eb30d616813` plus die Änderungen dieses Updates.
Es wurde kein neuer Root-Commit und kein Push erstellt. Commits in den generierten
Arbeitsrepositories dienen ausschließlich dem vorhandenen Patch-Export.

Verglichen wurden Paper `a15fed9c16a5cc93e4ff38d6e2135623e2dc9daa` und
[`c27ca36bf1d9d705e260f1cfb866c97de1ac537d`](https://github.com/PaperMC/Paper/commit/c27ca36bf1d9d705e260f1cfb866c97de1ac537d).
Paper Build 134 wurde am 28.09.2026 als Beta veröffentlicht. Die Integration ist
selektiv, kein vollständiger Rebase: `paperRef` bleibt die ursprüngliche
Import-Provenienz, Minecraft/Mache bleiben `26.3` / `26.3+build.1`.

## Zuordnung der 18 neuen Commits

| Paper-Commit | Behandlung in Tessera/Sinopia |
| --- | --- |
| [08ca088](https://github.com/PaperMC/Paper/commit/08ca088b5d33826a2ed6f1032a59cdf93bd9ea13) | Übersprungene eingehende Decoder-Pakete werden im bestehenden Paketlimit und Empfangszähler erfasst. |
| [d289ae1](https://github.com/PaperMC/Paper/commit/d289ae1dfc37dac795e5f14ffdcc01556d7e8747) | Obsolete-Markierungen/Javadoc der klassischen Command-APIs übernommen; keine Command-API entfernt. |
| [a091961](https://github.com/PaperMC/Paper/commit/a0919613466972b253eec2b4935cf82ce3c876bb) | Mob-Friedlich-Override einschließlich NBT und Bukkit-Adapter wieder angebunden. |
| [f686260](https://github.com/PaperMC/Paper/commit/f6862603ddc15b1d2a58eabe28695e23fea5446b) | Lore-Equals/Hash berücksichtigt nur die ursprünglichen Zeilen. |
| [b23b725](https://github.com/PaperMC/Paper/commit/b23b725834b24526800f12bb22e47df85dfe811c) | Absicherung bereits durch den lokalen Vanilla-DFU-Fallback vorhanden; nicht überschrieben. Native Konvertergrenze bleibt 26.2. |
| [640efee](https://github.com/PaperMC/Paper/commit/640efeeabe35fbdff2b33864ee8585dc8efccc7e) | `isChunkGenerated` verwendet für den bereits blockierenden Ladevorgang BLOCKING-Priorität; Folia-Weiterleitung erhalten. |
| [5cbb5d6](https://github.com/PaperMC/Paper/commit/5cbb5d6fa0e7b05395194695d0171b2fc2b86459) | Leafpile 1.2.4 mit BOM/common/concurrentutil/converter; Tessera ergänzt profiler. Kein behaupteter Scheduler-Algorithmuswechsel. |
| [d3ee2cf](https://github.com/PaperMC/Paper/commit/d3ee2cfc67eeaec1f4ae625db22332c2d2f85b57) | Vollständiges Entity-Chunk-Laden nutzt bereits vorhandene NBT-Daten vor `runPostLoad`; Owner-Prüfung erhalten. |
| [f4eb9c2](https://github.com/PaperMC/Paper/commit/f4eb9c2e6337e33dd9f286eeb058bc52e7ed7aa1) | `ChunkMap.chunkScanner` liest asynchron über Moonrise; Future meldet I/O-/Visitor-Fehler. |
| [8c95c89](https://github.com/PaperMC/Paper/commit/8c95c89a45fdef92019b9d9d5e8e5fc5f726934a) | Prioritätsunterscheidung in Sinopia übernommen; Tesseras generischer `getChunkFuture` bleibt ausdrücklich gesperrt. |
| [0fdc088](https://github.com/PaperMC/Paper/commit/0fdc08858f78b03c1df8033fedf90117e8c7d5a9) | Upstream-Patchzusammenfassung; Inhalte bereits oben enthalten, nicht doppelt angewendet. |
| [c0f1d03](https://github.com/PaperMC/Paper/commit/c0f1d039bb66856b3e8cf1cb011a7163a991e0ad) | Paper-eigene Runner-/Publishing-Infrastruktur nicht übernommen. |
| [bfbe49c](https://github.com/PaperMC/Paper/commit/bfbe49cfa992622b7e043e252537d07ce71f0174) | Fill-Publishingtask vom Konfigurationscache ausgeschlossen. |
| [f83a0ba](https://github.com/PaperMC/Paper/commit/f83a0bae4f1a18a6d714ba7c4b7d06051b368502), [e5aa18b](https://github.com/PaperMC/Paper/commit/e5aa18b3189fef92c2fbcff8ab5fe9beafd02152) | Direkt von fill-gradle 1.0.12 auf 1.0.14 aktualisiert. Keine Veröffentlichung ausgeführt. |
| [643a11a](https://github.com/PaperMC/Paper/commit/643a11a413c3f6afb19f0ada2db43c78ae3f2607) | Root/Sinopia `channel=BETA`, Tessera `tesseraBuildChannel=beta`, Buildnummer 011. |
| [13eb8f2](https://github.com/PaperMC/Paper/commit/13eb8f29cadf07143bf05a7bb009197128864108), [c27ca36](https://github.com/PaperMC/Paper/commit/c27ca36bf1d9d705e260f1cfb866c97de1ac537d) | Paper-Sponsor und CI-Badge nicht in Tesseras README kopiert. |

## Zwei zusätzliche API-Lücken

- [ace932e](https://github.com/PaperMC/Paper/commit/ace932ed4e72bc7843cc4af1fa877da4ca8b2f1e): `TreeType.POPLAR` und Baumgenerator-Zuordnung ergänzt. Rot/orange/gelb sowie die bisherige farbspezifische Wachstums-Eventzuordnung bleiben als Sinopia-Verhalten bestehen.
- [9838b40](https://github.com/PaperMC/Paper/commit/9838b40808139861ee05ad164c719c3dba68b112): SulfurCube `swallow`, `setEquipped`, `getEquipped` ergänzt. Der Craft-Adapter löst den aktuellen, threadgeprüften Handle auf; die Equipment-Wrapper tun dies ebenfalls. Der native Schluckpfad prüft nach Drop-/Equipment-Callbacks Owner, Welt und Removal erneut.

Öffentlicher Vertrag und Grenzen, insbesondere nicht-atomare Callback-Nebenwirkungen:
[Entity-/Baum-API](api/entity-and-tree-compatibility.md).

## Dauerhafte Patchschichten und Regionsmodell

- Sinopia-Minecraft-Patch **0047** enthält die allgemeinen NBT-/Chunk-, Netzwerk-, Mob- und Lore-Korrekturen. API-/CraftBukkit- und Buildänderungen liegen direkt in der versionierten Sinopia-Basis.
- Folia-Basispatch **0001** ist an den neuen Scanner und die geänderte Chunk-Future-Signatur angepasst. Tracking bleibt in `RegionizedWorldData`; der generische Future-Aufruf bleibt gesperrt.
- Tessera-Minecraft-Patch **0048** sichert den Schluckpfad nach Plugin-Callbacks ab.
- Tessera-Minecraft-Patch **0049** berücksichtigt den gespeicherten Friedlich-Override vor der NBT-Entity-Erzeugung. Der echte Unload-/Reload-Test fand diese zusätzliche Lücke im übernommenen Paper-Override: Ohne Ergänzung wurde der Zombie noch vor `Mob.load` verworfen. Nur `LOAD` mit explizitem `FALSE` darf die Schwierigkeitssperre umgehen; Feature-Prüfungen bleiben erhalten.
- Tessera-Serverpatch **0037** enthält sechs neue Regressionstests für Lore, Paketzählung, Scanner-Abschlüsse/Fehler, Entity-Chunk-Ownership und die Folia-Future-Sperre sowie zwölf parametrisierte Fälle für beide NBT-Erzeugungspfade, Override-Werte, Feature-Sperren und Spawn-Gründe.
- Der Gradle-Dateipatch bindet Leafpiles zusätzliches Profiler-Modul ein.
- Native Restore-/Snapshot-Schreibschranken, Generatorbindungen, Wolfskorrekturen und Datenfixer bleiben erhalten. Keine globale Ersatz-Ausführung für fremde Entity-/Chunk-Zugriffe eingeführt.

Der ältere große Region-Dateicache-Umbau ist nicht Teil dieses Updates. Es wird
kein gemessener TPS-/MSPT-Gewinn und keine vollständige Paper-/Plugin-Parität behauptet.

## Reproduzierbare Prüfungen

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel --no-configuration-cache
node --test scripts/validate-api-docs.test.mjs scripts/validate-changelogs.test.mjs
node scripts/validate-api-docs.mjs
node scripts/validate-changelogs.mjs
.\gradlew.bat -I scripts/check-api-examples.init.gradle.kts :folia-api:checkApiDocumentationExamples --no-configuration-cache --console=plain
node smoke-tests/paper-beta/run.mjs "C:/Program Files/Java/jdk-25.0.3/bin/java.exe" build/libs/tessera-server-26.3.build.011-beta.jar
node smoke-tests/native-player-restore/run.mjs "C:/Program Files/Java/jdk-25.0.3/bin/java.exe" build/libs/tessera-server-26.3.build.011-beta.jar
```

Java-Pfad an die eigene Umgebung anpassen. Die Smoke-Runner verwenden ausschließlich
markierte, isolierte neue Testwelten; keine produktiven Saves. Ihre EULA-/Loopback-
Hinweise beachten. Der native Restore-Runner verwendet echte verbundene Protokollclients,
aber keinen visuell bedienten Vanilla-Client.

## Durchgeführte Prüfungen am 28.09.2026

- **Vollständiges `buildTessera`: bestanden**, einschließlich erneuter Anwendung aller Patchschichten, Tests, Checkstyle-/Bad-Call-Prüfungen und ausführbarer Paperclip-JAR. Finale Ausgabe: `build/paper-beta-final-build.log`, `BUILD SUCCESSFUL in 7m 35s`.
- Server-Testbericht: **10.171 Einträge, 0 Fehler, 87 übersprungen**; API-Testbericht: **529 Einträge, 0 Fehler, 2 übersprungen**. Die Gradle-Berichte zählen auch Suite-/Container-Einträge. Die API-Tests waren im finalen Lauf unverändert/up-to-date; der vollständige Server-Test wurde nach Patch 0049 erneut ausgeführt. Alle 18 neu ergänzten Testfälle bestanden.
- Build-/Git-Schutztests: **22 Prüfungen bestanden**, `build/paper-beta-buildsupport.log`.
- Dokumentationsvalidator-Tests: **57 bestanden**. API-Validierung: **19 Artikel, 49 Links**, bestanden. **9 vollständige Java-Beispiele** gegen die tatsächliche API kompiliert, `build/paper-beta-doc-examples.log`.
- Neuer 011-Changelog und seine lokalen Links separat erfolgreich geprüft. Der Gesamtvalidator über die historische Release-Sammlung scheitert weiterhin an bereits vorhandenen unquotierten YAML-Strings in `docs/builds/26.2/0.0.1.md`. Historische Releases wurden nicht verändert und diese Gesamtprüfung wird nicht als bestanden ausgewiesen.

### Native API- und Persistenzprüfung

Fixture: `build/paper-beta-smoke-paper-beta-1790618623576/`, Loopback-Port `62114`.
`result.json` meldet `passed: true`, Exitcode `0`, kein Timeout; vollständige
Ausgabe in `runner.log` und `build/paper-beta-smoke-final.log`.

In parallel angeforderten Regionen um Chunk `[0, 0]` und `[128, 0]` bestanden:

- tatsächliche Region-Ownership und `isChunkGenerated`;
- Friedlich-Override: `FALSE` überlebt, `TRUE` despawnt trotz Persistenz;
- SulfurCube-Baby/Erwachsener, Einzelgegenstand aus Eingabekopie, Ablehnung desselben Item-Typs, Austausch mit genau einem Drop, direkter Slot, Leerzustand und isolierte Getter-Kopie;
- Entfernung im `EntityDropItemEvent`: kein anschließender Equipment-Austausch am entfernten Cube;
- `POPLAR`, `RED_POPLAR`, `ORANGE_POPLAR`, `YELLOW_POPLAR` durch den realen Baumgenerator mit Block-Prädikat;
- gespeichertes Entladen und Wiederladen der Welt auf Friedlich mit denselben Zombie-/Cube-UUIDs, gespeichertem Override und BODY-Item, ohne zusätzliche Zombies/Cubes.

Die Vorläufe sind ausdrücklich keine bestandenen Abnahmen: Die erste Fixture
versuchte eine von Moonrise während `EntityAddToWorldEvent` gesperrte Entfernung.
Sie verwendet nun das vorher ausgeführte `EntityDropItemEvent`, ohne die
Abbruch-Assertion abzuschwächen. Der zweite Lauf fand den echten Verlust des
gespeicherten Zombies beim Laden; Patch 0049 behebt diesen Fall, der finale Lauf
prüft ihn unverändert. Keine Cross-Region-Fehler, Region-Tick-Abbrüche oder
Entity-Chunk-NBT-Invarianzfehler im erfolgreichen Lauf.

### Native Online-Restore-Regression

Fixture: `build/native-restore-smoke-1790618644913/`, Loopback-Port `62126`.
Nachweis: `native-restore-result.json`, `native-restore-evidence/checks.txt`
und `runner.log`; zusammengefasste Ausgabe: `build/paper-beta-native-restore.log`.
Öffentlicher Vertrag **1**, echte `prepare/apply/complete`-Transaktionen akzeptiert,
Runner-Exitcode **0**.

| Protokollclient | Logins | Kicks | Disconnects | Transferbestätigungen | Stat-Nullwert-Resets | Advancement-Resets |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| RestoreOne | 1 | 0 | 0 | 38 | 8 | 12 |
| RestoreTwo | 1 | 0 | 0 | 34 | 8 | 12 |

Die Verbindungszähler gelten bis zum erfolgreichen Testabschluss vor dem
kontrollierten Server-Shutdown. Geprüft wurden wiederholtes Load/Resave, frischer
Rollback-Store, unveränderte Quelle, idempotente Ergebnisse, echter Rollback,
Transfer-Veto, frischer Spielerzustand ohne Save-Eintrag, Admission-/Writer-Drain
bei Cancellation, echte Statistik-Schreib- und Windows-Publikationsfehler,
verbundene tote/schlafende Spieler sowie erhaltene Wüsten-/Nether-Generatorbindungen
einschließlich neuer Chunks. Der abschließende Snapshot gelang nach freigegebenem
Gameplay-Freeze ohne Reconnect.

### Artefakt

- Datei: `build/libs/tessera-server-26.3.build.011-beta.jar`
- Größe: **55.381.020 Bytes**
- SHA-256: `95fd761ab974ae9e0fe7dee8311b61048eaa890e138c5ef9bd78d2c08dff4d45`
- Beide erfolgreichen nativen Läufe protokollieren genau diese Prüfsumme.
- Quellbasis: Root-Commit `3a6460634b2bf4b3b98a9fcf77e54eb30d616813` plus die hier beschriebenen, noch uncommitteten Änderungen; Sinopia-Snapshot `bb9e607b9fc98a1e7264cc4e32a2a3211806419f`. Der Root-Commit allein enthält dieses Update noch nicht.

### Prüfgrenzen

Kein gemeinsamer Lauf mit MCC/MVE/TAB/LuckPerms, kein visuell bedienter
Vanilla-Client, kein Langzeit-/Lastbenchmark und kein vollständiger neuer
Shutdown-/Recovery-Matrixlauf für 011. Die angebotenen zusätzlichen Smoke-Modi
sind nicht automatisch durch diese beiden Läufe abgedeckt. Der öffentliche
Restore-Vertrag bleibt unverändert; für diese API-Integration wird kein neuer
MCC-Pfad eingeführt. Der tatsächliche Plugin-Stack muss separat geprüft werden.

Die Windows-Testumgebung protokolliert außerdem OSHI-/Perflib-Diagnosen zu ihren
lokalen Performance-Countern; diese verhinderten die Serverstarts und Tests
nicht. Es wurden dafür keine Windows-Registry- oder Systemeinstellungen verändert.
