# Build 018 – Drachenei-Schutz und Betriebsfixes

Stand: 08.10.2026. Tessera `26.3-018-beta`, Minecraft/API `26.3`, Java 25.
Die Drachenei-Ausnahme ist implementiert, als dauerhafte Patches gesichert,
erneut angewendet und auf der finalen ausführbaren JAR nativ geprüft.

Die folgenden ursprünglichen Nachweise gelten für die JAR vom 08.10.2026.
Der [Disconnect-Nachtrag vom 11.10.2026](#nachtrag-11102026--disconnect-lücke)
dokumentiert die danach neu gebaute 018-beta-JAR mit eigenem Hash und eigener Abnahme.

Die [Changelog 0.0.18](builds/26.3/0.0.18.md) fasst auch die bereits nach dem
ursprünglichen Build-017-Eintrag ergänzten TPS-/Pregen-, Whitelist- und
Konsolenshutdown-Fixes zusammen. Ihre früheren nativen Nachweise beziehen sich
auf die jeweils dort dokumentierte 017-JAR. Sie wurden nicht pauschal als neue
native Abnahme auf Build 018 übernommen.

## Implementierter Umfang

- `minecraft:dragon_egg` ist unabhängig von der aktivierten Dupe-Option von
  Tesseras zusätzlicher Endportal-Quell-Nachbearbeitung ausgeschlossen.
- Die Prüfung gilt im vorgezogenen Portal-Tick, beim Erfassen und beim
  Abschließen eines Quell-Snapshots sowie beim älteren synchronen
  Force-Tick-Duplizierungsflag. Auch ein bereits vorhandener Ei-Snapshot wird
  vor Veröffentlichung verworfen und die Referenz geleert.
- Gewöhnliche Fallphysik, Platzieren, Droppen und normale Endportaltransfers
  bleiben möglich, in beiden Transferrichtungen. Erreicht das Ei vor seinem
  gewöhnlichen Portaltransfer den Boden, landet es wie im regulären Folia-Pfad
  am Ursprung. Vorhandene Eier werden nicht gelöscht.
- Andere Falling-Block-Typen behalten ihre bisherige optionale Duplizierung.
  Kein Eingriff in Ankunftshöhe, Geschwindigkeit, Portalrotation, Button-Drops,
  Hopper-Aufnahme oder die bestehenden Ownership-/Abbruchprüfungen.
- Keine neue Konfigurationsoption, kein Upstream-/Abhängigkeitswechsel und
  keine Änderung an MCC-/MVE-Produktcode oder an der Sinopia-Basis.

Der Schutz betrifft diesen Endportal-Duplizierungspfad, keine beliebigen
Duplikationen durch Plugins oder andere Spielmechaniken. Der Thread-/Event-
Vertrag steht in der [Endportal-Referenz](api/events.md#optionale-endportal-nachbearbeitung-fallender-blöcke).

## Quellstand und dauerhafte Patches

- Branch: `ver/26.3.x`.
- Root-Basiscommit: `014c29a29d687c8d2c5b33e1ee2987f19234af3d`, **zusätzlich**
  die lokalen Build-018-Patches, Testfixture- und Dokumentationsänderungen.
  Der Basiscommit allein ist nicht der vollständige Quellstand dieses Builds.
  Kein Commit oder Push im Hauptrepository.
- Sinopia-Snapshot unverändert: `cebdb10d9418432a18b920f7f2e902a8d03a43f4`.
- Nach der finalen erneuten Patch-Anwendung: Minecraft-HEAD
  `31e2dbdbc08282f3b84a4a9fd24ddf64bce8f2fa`, Server-HEAD
  `9d995c79a55a9ef2cca991e6b5af5f9998478e3e`.
- [Minecraft-Patch 0060](../folia-server/minecraft-patches/features/0060-Exclude-dragon-eggs-from-unsafe-End-portal-duplicati.patch):
  SHA-256 `eedacf44d913fa5265e6876ad381e735b5baa7fb61b3cca652e6ffeff567776d`.
- [Server-Testpatch 0049](../folia-server/paper-patches/features/0049-Test-dragon-egg-exclusion-from-End-portal-duplicatio.patch):
  SHA-256 `87fc845697a38756569adda78b12d4daddad93d88808a77b3074c26a22b47509`.

Die Feature-Commits wurden ausschließlich in den generierten Arbeitsrepositories
angelegt und über den bestehenden Tessera-Rebuild-Workflow exportiert. Der
anschließende normale Build hat alle Patchschichten erneut angewendet;
beide generierten Arbeitsquellen sind danach sauber. Vorherige Gameplay-,
Spieler-Restore-, NBT-, Whitelist-, TPS-/Pregen- und Shutdown-Patches bleiben
enthalten.

## Build und Qualitätsprüfungen

Ausgeführt auf Windows 11 mit Java 25.0.3 und Gradle 9.8.0:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.3'
.\gradlew.bat :folia-server:rebuildMinecraftFeaturePatches :folia-server:rebuildPaperServerFeaturePatches --console=plain --no-configuration-cache --max-workers=2 --no-parallel
.\gradlew.bat buildTessera --console=plain --no-configuration-cache --max-workers=2 --no-parallel
.\gradlew.bat -I scripts/check-api-examples.init.gradle.kts :folia-api:checkApiDocumentationExamples --console=plain --no-configuration-cache --max-workers=2 --no-parallel
```

Der finale **gewöhnliche** `buildTessera`-Lauf ist **BUILD SUCCESSFUL** in
4 Minuten 24 Sekunden. Vorhandene Tests, Checkstyle und Bad-Call-Prüfungen
bleiben aktiv. Die Server-Suite wurde im vorangegangenen kompilierten
018-Durchlauf frisch ausgeführt; der finale Standardlauf verwendet ihren
unveränderten Gradle-Up-to-date-Stand. API- und Checkstyle-Helfertests wurden
ebenfalls regulär aus dem unveränderten Stand übernommen.

| Prüfung | Ergebnis |
| --- | --- |
| Server-Suite | 10.335 Fälle, 87 bestehende Skips, 0 Fehler/Failures |
| API-Suite | 529 Fälle, 2 bestehende Skips, 0 Fehler/Failures |
| Checkstyle-Helfer | 3 Fälle, 0 Fehler/Failures |
| Endportal-Nachbearbeitung | 56 Fälle, 0 Skips, 0 Fehler/Failures |
| Vollständige Java-API-Beispiele | 10 Klassen separat gegen die tatsächliche API kompiliert |
| API-Dokumentationsvalidator | 35 Tests bestanden; 19 Artikel und 52 Artikel-/Ankerlinks gültig |
| Changelog-/API-Validator-Tests zusammen | 57 bestanden, keine Skips oder Fehler |
| Neue Release-/Build-/README-Links und Nachweis | 44 lokale Artikel-/Ankerlinks und tatsächliche Patch-/JAR-/Fixture-Hashes geprüft |

Die 56 Endportal-Regressionen erhalten die bisherigen sechs Sicherheitsfälle.
Hinzu kommen alle 23 registrierten Falling-Block-Typen mit aktivierter und
deaktivierter Option sowie Prüfungen für das Erfassen und Verwerfen von
Drachenei-Snapshots einschließlich veränderter Entity-/Snapshotzustände.
Die privaten Produktionsmethoden wurden dafür nicht öffentlich gemacht.

Ein anfänglicher **kombinierter** Aufruf von `buildTessera` und der zusätzlichen
API-Beispielaufgabe führte nach dem erfolgreichen inneren Build zu Gradles
impliziter Abhängigkeitsdiagnose zwischen äußerem API-Compile und Patch-Aufgaben.
Der vorgeschriebene normale Build und der separate Beispielcheck sind danach
beide erfolgreich. Keine Produktimplementierung oder Qualitätsprüfung wurde
zur Umgehung dieses Aufrufproblems geändert.

Die globale Changelog-Validierung hat weiterhin den unveränderten historischen
Formatfehler in `docs/builds/26.2/0.0.1.md`. Die neue Changelog, der Buildbericht,
die README und ihre lokalen Artikel-/Ankerlinks werden zusätzlich separat
geprüft; historische Releases bleiben unverändert.

## Finale ausführbare JAR

```text
build/libs/tessera-server-26.3.build.018-beta.jar
```

Absoluter Buildpfad:
`C:\Users\hunte\IdeaProjects\Tessera\build\libs\tessera-server-26.3.build.018-beta.jar`.
Größe: **55.630.386 Bytes**. SHA-256:

```text
832b3557a8fc949db6ca71e7d0ef92f9df8a883b7fe10485558747c3dc1fc741
```

Nach den folgenden nativen Tests wurde nicht neu gepackt. Der finale
Dateihash stimmt mit allen drei nativen Ergebnisdateien überein.

## Native Abnahme auf der finalen JAR

Der [Endportal-Runner](../smoke-tests/end-portal-duplication/README.md) startet
drei isolierte Testserver mit freien Loopback-Ports und eigenen Wegwerfwelten.
Native Falling-Block-Entities kollidieren mit echten Endportalblöcken; kein
gemocktes Portal und kein erzwungenes `onGround`-Flag.

| Fixture | Option | Port | Bestandene Fälle |
| --- | --- | --- | --- |
| `end-portal-dupe-false-1791413796280` | `false` | 58485 | 46 |
| `end-portal-dupe-true-1791413885632` | `true` | 50634 | 46 |
| `end-portal-flight-true-1791413860538` | `true` | 49901 | 20 |

Alle **112 Fälle bestanden**. Alle drei Server: regulärer Stop, Exitcode **0**,
kein Timeout; Runner ebenfalls erfolgreich. Kein Cross-Region-Threadfehler,
Region-Tick-Ausfall, Watchdog-Ausfall oder ungeplanter Portal-Testfehler.

### Dracheneier und Duplizierungsregression

- Die bisherigen 34 Nicht-Ei-Fälle pro Einstellung bleiben erhalten: Sand,
  roter Sand, Kies, Betonpulver und Ambosse, zwei getrennte tatsächliche
  Regionsbesitzer, wiederholte Durchläufe und beide Transferrichtungen.
- Pro Einstellung zusätzlich zwölf Drachenei-Fälle: Eintritt/Rückreise,
  jeweils zwei weit entfernte Quellregionen und jeweils Landung, gewöhnlicher
  Transfer aus der Luft sowie tatsächlicher Item-Drop.
- Für jedes Ei exakt ein verbliebener Block beziehungsweise Item, keine
  zusätzliche Quell-Nachbearbeitung und keine zurückgelassene Falling-Block-
  oder Item-Entity. Erfolgreiche gewöhnliche Ei-Transfers behalten UUID und
  Bukkit-Wrapper.
- Mit `true` duplizieren die bisherigen gültigen Nicht-Ei-Landefälle weiterhin
  jeweils einmal am Ursprung und einmal am Ziel. Mit `false` entsteht keine
  zusätzliche Kopie. Portal-/Platzierungs-/Drop-Vetos, Umleitung, Entfernung
  durch Listener und Owner-Prüfungen werden weiter tatsächlich geprüft.

### Flugrichtung, Buttons und Hopper

Die unveränderte separate Flugfixture prüft fünf Materialien in vier
Quellflugrichtungen. Tatsächliches Standard-Endziel `(100.5, 50, 0.5)`,
Obsidianboden Y=48/Oberfläche Y=49, erhaltene UUID und Wrapper, native
Geschwindigkeitsrotation von Quell-Yaw 0 zu Ziel-Yaw 90 und Weiterflug sind
bestanden. Kein künstlicher Schub und keine Nach-Teleport-Korrektur durch die
Fixture. Unterstützte Buttons erzeugen echte Item-Drops, Hopper nehmen jeweils
genau ein Item auf, am Ziel wird kein Block platziert und keine markierte
Entity bleibt zurück. Die Quellkopie bleibt genau einmal erhalten.

Ausgeführte Befehle nach dem Build:

```powershell
$env:TESSERA_SMOKE_MODE = 'end-portal-duplication'
node smoke-tests/end-portal-duplication/run.mjs "$env:JAVA_HOME/bin/java.exe" build/libs/tessera-server-26.3.build.018-beta.jar
$env:TESSERA_SMOKE_MODE = 'end-portal-flight'
node smoke-tests/end-portal-duplication/run.mjs "$env:JAVA_HOME/bin/java.exe" build/libs/tessera-server-26.3.build.018-beta.jar
Remove-Item Env:TESSERA_SMOKE_MODE
```

## Nachweise, Bereinigung und Prüfgrenzen

Der [kompakte maschinelle Nachweis](test-evidence/26.3-018/end-portal-dragon-egg.json)
enthält Quellstand, Patch-/JAR-Hashes, konkrete Fixture-Ergebnisse und Grenzen.
Originale `result.json`, `runner.log` und Prüftexte liegen nach hashgeprüfter
Archivierung unter `build/reports/native-fixtures/<Fixture-Name>/`. Die dortige
JSON-Datei behält den ursprünglichen, inzwischen bereinigten Fixture-Pfad.
Der 56-Fälle-JUnit-Nachweis liegt daneben als
`end-portal-continuation-018.xml`; Buildlogs unter `build/reports/build-logs/`.

Die drei ausschließlich hierfür angelegten, markierten Testserververzeichnisse
wurden nach erfolgreichem Stop entfernt: **666.730.866 Bytes**, rund **636 MiB**.
Keine produktiven Welten, vorhandenen Nutzersaves, Arbeitsquellen oder finale
JAR gelöscht. Die Wegwerfwelten sind nur durch erneutes Ausführen der Runner
reproduzierbar; ihre kompakten Ergebnis-/Logdateien bleiben archiviert.

In allen drei Windows-Läufen meldete OSHI den bestehenden Perflib-009-
Zählerfehler; außerdem bleiben JDK-/Unsafe-Hinweise und die absichtliche
Offline-Mode-Diagnose der isolierten Fixtures sichtbar. Dies ist keine
Behauptung einer vollständig warnungs- oder fehlerfreien Konsole. Kein
`Terminal has been closed` im gewöhnlichen, gepipeten Stop dieser drei Server;
dies ersetzt keinen erneuten Windows-Native-Terminal-Stresstest.

Keine verbundenen Vanilla-Clients oder visuelle Rendering-Abnahme; kein
vollständiger Kolben-/Slime-/Honey-Duper und keine Garantie identischen
Redstone-Timings aller Konstruktionen. MCC/MVE/TAB/LuckPerms und die früheren
Spieler-Restore-, Whitelist-, TPS-/Pregen- und Terminal-Integrationstests wurden
nativ nicht auf dieser 018-JAR wiederholt. Ihre Implementierungen und die
vorhandenen Unit-Regressionen bleiben enthalten; historische Nachweise gelten
jeweils für ihren eigenen Hash.

Für das Update Server normal stoppen, JAR ersetzen und vollständig neu starten.
`unsupported-settings.allow-unsafe-end-portal-teleportation: true` bleibt die
bisherige Voraussetzung für die Duplizierung anderer Gravity-Blöcke; Dracheneier
sind ohne zusätzliche Einstellung davon ausgenommen.

## Nachtrag 11.10.2026 – Disconnect-Lücke

Buildnummer und Kanal bleiben `26.3-018-beta`. Der Fix betrifft den nativen
Verbindungsabbau, nicht die Ursache einer überlasteten Internetverbindung.
Keine Änderung an Keepalive-Grenzen, Konfiguration, Gameplay, öffentlicher
Plugin-API, MCC-/MVE-Produktcode oder Sinopia-Basis.

### Ursache und Korrektur

Paper markiert akzeptierte Disconnects mit
`Connection.handleConnectionDisconnectOnNextTick`. Folias regionale und globale
Verbindungsloops verwenden jedoch nicht Papers äußere Tick-Schranke. Solange ein
Disconnect-Paket noch auf seinen Schreibabschluss wartet, konnte deshalb der
Packet-Listener weiter Keepalives prüfen, weitere Kicks auslösen und denselben
Timeout wiederholt protokollieren.

- Die gemeinsame `Connection.tick()` berücksichtigt die Markierung nun für
  Listener-Ticks. Wartende Pakete werden weiterhin geflusht; Channel-Abschluss
  und Cleanup laufen weiterhin auf dem tatsächlichen Regions-/Global-Besitzer.
- Die Markierung ist `volatile` und wird erst nach einem akzeptierten Kick-Event,
  aber vor dem Versand des Disconnect-Pakets veröffentlicht. Ein abgebrochener
  `PlayerKickEvent` lässt die Verbindung aktiv und lesbar.
- Spätere direkte/queued Disconnect-Anfragen und verspätete Keepalive-Antworten
  lösen keinen weiteren Kick aus. Beide `disconnectSafely`-Überladungen verwenden
  dieselbe Schranke. Der zuerst akzeptierte Grund bleibt erhalten.
- Auch ein akzeptierter Kick innerhalb eines schon laufenden Kick-Events wird
  nach dessen Rückkehr nicht durch den äußeren Request überschrieben.
- Die Timeout-Meldung wird einmal beim tatsächlichen Akzeptieren protokolliert,
  nicht bereits vor dem abbrechbaren Event. Ein gewöhnlicher Client-Disconnect
  ohne Timeout wird weiterhin als `Disconnected` verarbeitet.

### Quellstand und reproduzierbare Patches

Root-Basiscommit: `c159a4e973747dbe787648d569826124a7b3c308`, **zusätzlich** die
lokalen Änderungen dieses Nachtrags. Kein Commit oder Push im Hauptrepository.
Der bestehende Patchworkflow wurde erneut ausgeführt; der normale `buildTessera`
hat anschließend alle Patchschichten erneut angewendet. Die generierten
Minecraft-/Server-Arbeitsquellen sind danach sauber.

- [Minecraft-Patch 0061](../folia-server/minecraft-patches/features/0061-Stop-accepted-disconnect-ticks-while-draining-native.patch),
  SHA-256 `e429a71bea2ed6fdb2d3b33d0a78455caa5a80815471b682b84afc429cd9e453`.
- [Server-Testpatch 0050](../folia-server/paper-patches/features/0050-Test-accepted-disconnect-idempotence-and-native-queu.patch),
  SHA-256 `492bce1a1f5fccf4ec43199b53ca41ba999d57244548a30a262c5ea4a281b317`.
- Erneut angewendeter Minecraft-HEAD: `d6629832a46f5f52b8fa992536a0f770d42d0b85`.
- Erneut angewendeter Server-HEAD: `9ec850dac2fcf5e1fc83a1cb86c815a60ae294f0`.

### Build und native Abnahme

Der normale `buildTessera` mit den oben angegebenen Java-/Gradle-Versionen und
unveränderten Qualitätsprüfungen ist erfolgreich: **8m 23s**. Frisch ausgeführte
Server-Suite: **10.346 Fälle**, 87 bestehende Skips, 0 Fehler/Failures. Darin alle
**11 neuen Disconnect-Regressionen**, ohne Skips. Die unveränderten API- und
Checkstyle-Helfersuiten verwenden ihre regulären Gradle-Up-to-date-Ergebnisse:
529 Fälle/2 bestehende Skips und 3 Fälle/0 Skips, jeweils ohne Fehler/Failures.
Checkstyle und Bad-Call-Prüfungen bleiben aktiviert. Zusätzlich 57 bestehende
Dokumentationsvalidator-Tests bestanden; API-Referenz unverändert gültig.

Der zusätzliche Reentranz-Test wurde zuerst gegen den unvollständigen Fix
ausgeführt und schlug mit zwei statt einem Disconnect-Paket fehl. Nach der
Korrektur bestehen alle elf Regressionen und der vollständige Build.

Der [native Runner](../smoke-tests/disconnect-lifecycle/README.md) wurde zweimal
auf derselben finalen JAR ausgeführt, jeweils mit drei Gameplay-Protokollclients
und einem Konfigurationsclient, zwei getrennten tatsächlichen Regionen und
aktivem Gameplay-Freeze. Verzögerte native Netty-Schreibabschlüsse von 2,2 Sekunden
prüfen die Zwischenphase vor dem physischen Verbindungsabbau. Die verkürzte
Keepalive-Grenze von 3 Sekunden gilt ausschließlich für die Wegwerf-JVMs.

| Lauf | Loopback-Port | Ergebnis |
| --- | --- | --- |
| `disconnect-lifecycle-1791721719660` | 50211 | 37 native Assertions bestanden, Exit 0 |
| `disconnect-lifecycle-1791721775434` | 54444 | 37 native Assertions bestanden, Exit 0 |

Beide Läufe: je Client genau 1 Login und 1 Verbindungsende. `DisconnectOne`,
`DisconnectTwo` und `ConfigDrop` erhalten jeweils genau 1 Disconnect-Paket;
`NormalQuit` erhält keines. Der erste Plugin-Kick bleibt tatsächlich abgebrochen,
der folgende akzeptierte Kick behält seinen ursprünglichen Grund. Nur die zwei
absichtlich nicht mehr antwortenden Clients erzeugen jeweils eine Timeout-Zeile.
Der normale Client endet mit `Disconnected`, ohne Kick-Event. Gameplay-Quit-Events
jeweils genau einmal; alle Spieler und ihre nativen Verbindungsregistrierungen
vollständig entfernt. Kein Watchdog-, Ownership-, Region-Tick- oder Cleanup-Fehler.

Die vier vorherigen nativen Fehlversuche betrafen die Testfixture: fehlende
Whitelist-Deaktivierung, Prüfung der ungenutzten Paper-Verbindungsliste statt
Folias Register, Warten auf den terminalen Konfigurations-ACK statt echte
Keepalives sowie eine Prüfung vor Abschluss des asynchronen Quit-Cleanup.
Diese Fixture-Ursachen wurden korrigiert; Assertions nicht abgeschwächt.
Ihre vorhandenen Logs/Ergebnisse bleiben als fehlgeschlagene Vorläufe archiviert.

### Neue ausführbare JAR, Nachweise und Grenzen

Pfad bleibt `build/libs/tessera-server-26.3.build.018-beta.jar`.
Neu gebaute Größe: **55.630.260 Bytes**. SHA-256:

```text
77ac6d8fd776d73bfba0288f33cd8e90a3438ee3414177e18f3c3c2ff0010456
```

Nach den beiden nativen Läufen nicht neu gepackt. Dieser Hash ersetzt den
oben dokumentierten alten 018-Hash ausschließlich für den Disconnect-Nachtrag.
Der [kompakte Nachweis](test-evidence/26.3-018/disconnect-lifecycle.json) hält
Quellstand, Hashes, Zähler, Vorläufe und Grenzen fest. Originale Ergebnisse,
Prüftexte und Logs unter `build/reports/native-fixtures/<Fixture-Name>/`,
JUnit-Nachweis als `build/reports/native-fixtures/disconnect-lifecycle-018.xml`,
Build-/Diagnoselogs unter `build/reports/build-logs/disconnect-*.log`.

Nach Prozessprüfung und hashgeprüfter Archivierung wurden ausschließlich die
sechs hierfür angelegten, markierten Wegwerfserver entfernt:
**1.096.159.398 Bytes**, rund **1,02 GiB**. Ihre Welten sind durch den Runner
reproduzierbar, nicht als vollständige Weltbackups archiviert. Nutzer-Debugdump,
produktive Welten/Saves, Arbeitsquellen und finale JAR wurden nicht gelöscht.

Protokollclients, keine Rendering-/Vanilla-Client-Abnahme und kein vollständiger
Test des externen Pluginstacks oder einer real ausgelasteten Internetverbindung.
Die früheren Endportal- und anderen nativen Tests wurden auf dieser neuen JAR
nicht erneut ausgeführt; die vollständigen Unit-Regressionen bleiben enthalten.
Windows meldet in den Fixtures weiterhin den bestehenden OSHI/Perflib-009-Fehler
sowie JDK-Hinweise; keine Behauptung einer vollkommen fehlerfreien Konsole.
Reale Netzwerk-Timeouts bleiben möglich. Zum Anwenden normal stoppen, diese
neu gebaute JAR ersetzen und vollständig neu starten.
