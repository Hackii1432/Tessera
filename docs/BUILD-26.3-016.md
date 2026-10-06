# Build 016 – Paper-Korrekturen mit Folia-Anpassungen

Stand: 06.10.2026. Tessera `26.3-016-beta`, Minecraft/API `26.3`, Java 25.
Die [21 fachlichen Paper-Backports](PAPER-INTEGRATION-2026-10-06.md) sind
implementiert und als dauerhafte Sinopia-/Tessera-Patches gesichert.
MCC, MVE und Horizons wurden nicht bearbeitet. Keine Minecraft-, Gradle-,
Paperweight-, Leafpile- oder DataConverter-Migration.

## Quellstand und erneute Patch-Anwendung

- Branch: `ver/26.3.x`.
- Root-Basiscommit: `e803eff86ba2c36ae35f8e7dd7521f42da19b864`, **zusätzlich** die
  lokalen Build-016-Änderungen. Der Basiscommit allein enthält Build 016 nicht.
  Kein Commit oder Push im Hauptrepository; Feature-Commits ausschließlich in
  den generierten Patch-Arbeitsrepos.
- Lokaler Sinopia-Snapshot: `cebdb10d9418432a18b920f7f2e902a8d03a43f4`.
- Nach vollständiger erneuter Anwendung: Minecraft-HEAD
  `aed3c6d0277c8bad6d2648c430050b80d67616a4`, Server-HEAD
  `4713db5e648474d21d1d0e8aee45f6702e58bdf0`.
- [Sinopia-Minecraft-Patch 0048](../sinopia/paper-server/patches/features/0048-Backport-October-Paper-correctness-fixes-and-invocat.patch),
  [Tessera-Minecraft-Patch 0055](../folia-server/minecraft-patches/features/0055-Keep-October-Paper-damage-attribution-on-the-owning-.patch)
  und [Tessera-Server-Patch 0044](../folia-server/paper-patches/features/0044-Guard-October-Paper-passenger-spawns-and-API-dragon-.patch).
  Normale Sinopia-API-/Adapterquellen und die Aufruf-Scope-Hilfe sind direkt versioniert.

Der Rebuild hat zwölf ältere Sinopia-Feature-Diffs mechanisch normalisiert,
darunter großflächige CRLF-/Ganzdatei-Diffs. Nach den bisherigen 47 Features
ergibt sowohl die aufbewahrte alte Arbeitskopie als auch die neue Basis vor
Feature 0048 exakt den Git-Quellbaum
`53c6698cce5b3a1f4e2acdb17787403580a6b6f7`. Die starke Verkleinerung dieser
Patchdateien entfernt somit keine bisherigen Funktionen. Folia-Konflikte beim
geschützten DamageSource-Feld und beim Respawn-Kristallstatus wurden unter
Erhalt beider Funktionsschichten aufgelöst. Weitere Patchänderungen sind
notwendige Basis-Indizes/Zeilenoffsets und der ersetzte Snapshot-ThreadLocal-Umbau.

| Datei | SHA-256 |
| --- | --- |
| Sinopia-Patch 0048 | `cc09966b5160a9f0c70831d7f9811bdb121f862060d93b52b3cddfb6893e3e0c` |
| Minecraft-Patch 0055 | `20c26bc64738bd858495468ff285f9994640001e664f7c1296ce2e63613c9723` |
| Server-Patch 0044 | `86ed162e15b65d9e4e77692abfd3ccb6fc843e8669105c8debf864dd3cc00096` |

## Vollständiger Build und Qualitätsprüfung

Ausgeführt unter Java 25.0.3 auf Windows 11, Gradle 9.8.0:

```powershell
.\gradlew.bat :folia-server:rebuildPaperServerFeaturePatches buildTessera --console=plain --no-configuration-cache --max-workers=2 --no-parallel
```

Ergebnis: **BUILD SUCCESSFUL**, 14 Minuten 23 Sekunden. `buildTessera` hat alle
Patchschichten erneut angewendet und `test`/`build` mit vorhandenen Checkstyle-
und Bad-Call-Prüfungen vollständig ausgeführt. Bereits vor diesem finalen Lauf
wurden die Minecraft-Features und die Sinopia-Patches über den bestehenden
Rebuild-/Capture-Workflow exportiert. Ein normaler Folge-Build verwendet weiterhin
`.\gradlew.bat buildTessera` aus dem Root.

- Server-Suite: **10.227 Fälle**, 87 bestehende Skips, **0 Fehler/Failures**.
- API-Suite: **529 Fälle**, 2 bestehende Skips, **0 Fehler/Failures**.
- Darin 13 neue Regressionen: echte NMS-Eimer-Platzierung mit gemocktem Level,
  Scope-Abbruch/Reentrancy/Parallelität, Bundle-Cache in beiden Reihenfolgen und
  parallelen Obfuscation-Stufen, immutable Post-Effects-Paket, Registry-Identität,
  Chatrenderer, Instrument-Metadaten, parallele Snapshot-Flags, deaktivierte
  Spam-Schwellen, Game-Master-Codecs und geprüfte Command-Exception.
- **10 vollständige API-Beispiele** gegen die gebaute API mit Java 25 kompiliert.
- Build-/Patch-Schutz: **22 Checks bestanden**.
- Dokumentationsvalidator-Testreihen: **57 Tests bestanden**; API-Metadaten,
  Artikel-/Ankerlinks und Syntax des nativen Runners geprüft.
- Neue Changelog-Metadaten und 68 lokale Links in Release, README, Buildbericht,
  Integrationsbericht und Workflow geprüft. Der Gesamt-Changelog-Validator bleibt
  durch den unveränderten historischen Eintrag `docs/builds/26.2/0.0.1.md`
  eingeschränkt (unquotierte YAML-Strings/altes Abschnittsschema); historische
  Releases wurden nicht nebenbei umgeschrieben.
- Vorhandene Tests und Assertions wurden nicht abgeschwächt. Die anfänglichen
  Compile-Probleme betrafen die neue Test-Fixture und wurden behoben.

Logs: `build/paper-october-buildTessera.log`,
`build/paper-october-api-examples.log`, `build/paper-october-buildsrc-tests.log`
und `build/paper-october-doc-tests.log`.

Beim optionalen Patch-Rebuild meldet Paperweight das bestehende binäre
`logo.png` als nicht textuell lesbar; der separate binäre Logo-Feature-Patch
0002 bleibt angewendet, ohne zusätzlichen Datei-Patch. Javadoc-/Deprecation-
Warnungen bleiben vorhanden. Die Headless-Serverstarts zeigen die bestehende
Windows-OSHI/Perflib-Diagnose, ohne den Test zu verhindern. Dies ist keine
Behauptung einer vollständig warnungsfreien Konsole.

## Finale ausführbare Datei

```text
build/libs/tessera-server-26.3.build.016-beta.jar
```

Größe: **55.609.701 Bytes**. SHA-256:

```text
4d35acc43b3af40a91784e744d3a2c09ee5a69b309a1307e7a3611edca3b173f
```

## Native Oktober-Abnahme auf dieser JAR

[Runner und Prüfvertrag](../smoke-tests/paper-october/README.md), frische isolierte
Welt, Loopback-Port `52390`, vier Regions-Threads, zwei Offline-Protokollclients.
Keine produktiven Welten oder Nutzer-Saves als Fixtures verwendet.

| Prüfung | Tatsächlich geprüftes Ergebnis |
| --- | --- |
| Eigentümer | Chunks `0,0` und `128,0` besitzen zwei verschiedene tatsächliche Tick-Regionen. |
| Blockzustände | Live-/Snapshot-Flags und Inventar-Isolation, plus Goldblock-Instrument, auf beiden Besitzern korrekt. |
| Todeszuordnung | Nativer ArmorStand-Kill hält die Schadensquelle nach dem Entfernen durch die regionslokale Zeitstempelkorrektur abrufbar. |
| Shelf-Kodierung | Native `getUpdateTag` erzeugt tatsächlich obfuskierte Paper-Repräsentation; persistente NBT behält Diamanten und verschachtelte Bundle-Inhalte. Netzwerk-zuerst und Disk-zuerst geprüft. |
| Passagiere | Neue Wurzel, bereits gültiger Passagier und unregistrierter verschachtelter Passagier werden korrekt aufgenommen; pro UUID exakt eine Entity. |
| Persistenz | Tatsächlicher Runtime-Welt-Unload mit Save und anschließender Reload erhält die exakten verschachtelten Bundle-Inhalte und Entity-Anzahl. |
| Verbindungen | 2 Logins, 0 Kicks, 0 Disconnects bis zur bestätigten Abnahme; anschließend absichtlicher Shutdown. |

Ergebnis: **bestanden**, Exitcode 0, kein Timeout, keine Cross-Region-Ausnahme,
kein Region-Tick-Ausfall und keine ConcurrentModificationException.
Rohdaten: `build/paper-october-1791301650498/result.json`, `runner.log` und
`october-checks.txt`. Der erste Versuch wurde durch die in der neuen Fixture
nicht explizit ausgeschaltete Whitelist abgewiesen; der korrigierte Runner
verwendet eine neue isolierte Testwelt, keine Änderung am Server-Backend.

## Bestehende Tessera-Funktionen auf der finalen JAR

### Pregen unter Freeze und nach Neustart

Der unveränderte [native Pregen-Runner](../smoke-tests/native-pregeneration/README.md)
wurde zusätzlich auf dieser JAR ausgeführt. Beide Phasen bestanden mit Exitcode
0, je 2 Logins, 0 Kicks und 0 Quits vor der Abnahme. Der absichtliche Serverneustart
zwischen den Phasen trennt selbstverständlich deren Verbindungen.

- Berechtigung, Dimensions-Aliase/-Keys, Completion, ungültige Auswahl und
  Isolation je Sender; laufendes Ziel trotz späterer Auswahländerung erhalten.
- Unter Freeze 81 Overworld- und 4 Nether-Ziele, Spieler in getrennten Dimensionen;
  keine Freigabe von Gameplay. Holder bei `1024,1024` tatsächlich entladen.
- Wiederholung überspringt 81 gespeicherte `FULL`-Chunks; Pause/Resume/Cancel und
  Moduswechsel funktionieren weiterhin.
- Eigene Generatorbindung und Diamantmarker der Runtime-Dimension erhalten.
- Echte Save- und Checkpoint-Fehler injiziert; nach Cleanup/Repair erfolgreich
  fortgesetzt. Erwartete Fehlerlogs wurden inhaltlich geprüft, nicht pauschal ignoriert.
- Snapshot/Unload bei aktivem Job, drainierter Shutdown, pausierter Neustart und
  explizite Revalidierung von 1.089 Zielen; 2 bereits gespeicherte Ziele erkannt.

Rohdaten: `build/native-pregeneration-1791301713881/result.json`, Loopback-Port
`52431`, beide Serverlogs und Prüftexte daneben.

### Nativer Spieler-Restore

Der vorhandene Runner `smoke-tests/native-player-restore/run.mjs` im Modus
`native-restore-transaction` hat den öffentlichen Vertrag **1** erfolgreich
ausgeübt: Prepare/Apply/Complete, wiederholtes Resave, frischer Rollback-Store,
Idempotenz, unveränderte Quelle und genaue Spielerzustände. Die native Abnahme
auf dieser JAR enthält außerdem Transfer-Veto/Rollback, frische Spieler ohne
Save-Eintrag, Writer-Drain trotz Caller-Cancellation, echte Statistik-Flush- und
Windows-Dateipublikationsfehler, Wiederherstellung verbundener toter/schlafender
Spieler sowie erhaltene vorbereitete Desert-/Nether-Generatorbindungen für neue
Chunks. Die abschließende Runtime-Snapshot-Aufnahme funktioniert erneut.

| Client | Logins | Kicks | Disconnects vor Ergebnis | Teleportbestätigungen | Statistik-Nullresets | Advancement-Resets |
| --- | --- | --- | --- | --- | --- | --- |
| RestoreOne | 1 | 0 | 0 | 37 | 8 | 12 |
| RestoreTwo | 1 | 0 | 0 | 34 | 8 | 12 |

Ergebnis: Exitcode 0, `publicTransactionAccepted=true`,
`nativeComponentChecksPassed=true`. Rohdaten:
`build/native-restore-smoke-1791301836557/native-restore-result.json` und
`native-restore-evidence/checks.txt`, Loopback-Port `52500`.
Dies ist die Wiederholungsprüfung des nativen Vertrags, keine vollständige neue
MCC-/MVE-Pluginintegration oder erneut ausgeführte gesamte Crash-/Recovery-Matrix.

Eine kompakte, versionierte [Zusammenfassung aller drei nativen Läufe](test-evidence/26.3-016/native-regressions.json)
enthält die geprüfte JAR-Prüfsumme und die konkreten Ergebnisse.

## Prüfgrenzen und Betrieb

- Die Shelf-Netzwerkrepräsentation wird im echten nativen Encoderpfad geprüft,
  nicht visuell in einem Vanilla-Client. Die zwei verbundenen Clients bleiben
  während dieser Fixture in der Standardwelt; die getrennten Zielregionen sind
  durch den tatsächlichen Regionsbesitzer nachgewiesen.
- Die gesamte sichtbare API-Drachen-Respawn-Sequenz, interaktive TTY-Darstellung,
  sämtliche Datapack-/Plugin-Kombinationen und der produktive MCC-/MVE-/TAB-/
  LuckPerms-/Horizons-Stack wurden damit nicht vollständig abgenommen.
- JLine 4.4.6 verwendet Java-FFM statt JNI. Java 25 kann ohne explizite Native-
  Access-Freigabe eine Restricted-Method-Warnung ausgeben; optionaler JVM-Schalter
  `--enable-native-access=ALL-UNNAMED` vermeidet diese Warnung. Die Headless-Starts
  funktionieren; eine Änderung eigener Startskripte wird nicht automatisch vorgenommen.
- Die neuen APIs verleihen keinem Plugin beliebigen Off-thread-Zugriff. Registry-
  Cache-Sicherheit ist keine allgemeine Async-Freigabe; Spawns, Arena und Kristalle
  brauchen ihre tatsächlichen Besitzer. Die neuen Unload-Optionen behalten die
  bisherigen Defaults `50` und `0.05`.
- Der Bundle-Fix rekonstruiert keine schon verlorenen Inhalte. Vor dem Einsatz
  auf SMP ein vollständiges Backup erstellen und mit dem eigenen Plugin-Stack
  testen; ein Beta-Build ist keine pauschale Produktionsgarantie.

Die [Changelog](builds/26.3/0.0.16.md) beschreibt ausschließlich die tatsächlich
implementierten Änderungen.
