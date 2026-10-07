# Build 018 – Drachenei-Schutz und Betriebsfixes

Stand: 08.10.2026. Tessera `26.3-018-beta`, Minecraft/API `26.3`, Java 25.
Die Drachenei-Ausnahme ist implementiert, als dauerhafte Patches gesichert,
erneut angewendet und auf der finalen ausführbaren JAR nativ geprüft.

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
