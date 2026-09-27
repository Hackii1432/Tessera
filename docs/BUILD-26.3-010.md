# Tessera 26.3-010-alpha – Build und native Abnahme

Stand: 27.09.2026. **Nativer Restore-Vertrag 1 implementiert und auf der finalen
JAR mit verbundenen Protokollclients abgenommen.** Dieser Stand ersetzt den
früheren unvollständigen Prüfbuild mit Vertrag 0. Die gemeinsame Abnahme mit dem
konkreten MCC-/MVE-/TAB-/LuckPerms-Stack bleibt ausdrücklich offen.

## Quellen und ausführbare Datei

- Branch: `ver/26.3.x`.
- Ausgangsbasis: `6ededc28656e3adf1d2881d0e5e68854884353cf` (009).
- Exakter Quellcommit der gebauten JAR: `a767a0ab30a65ad7faa7f693a544506bd7c694c9`.
- Laufzeitkennung: `26.3-010-a767a0a`.
- Minecraft/API `26.3`, Java `25.0.3`, Kanal `alpha`.
- Gradle `9.8.0`, Paperweight `2.0.0-beta.24`, Mache `26.3+build.1` unverändert.
- Sinopia und die Wolfs-KI-Korrekturen aus 009 unverändert; kein MCC-/MVE-Produktcode geändert.
- JAR: `build/libs/tessera-server-26.3.build.010-alpha.jar`, 66.524.064 Bytes.
- Auf diesem Rechner: `C:\Users\hunte\IdeaProjects\Tessera\build\libs\tessera-server-26.3.build.010-alpha.jar`.

SHA-256:

```text
fb1f59a9b7875f3aba3773321032d29235be6ba67886c5a66811c0864429fe75
```

Der nachträgliche Dokumentations-/Nachweiscommit ändert nicht den oben genannten
Quellcommit der JAR. Alle nachstehend finalen Live-Läufe protokollieren dieselbe Prüfsumme.

## Vollständiger Build und Tests

Tatsächlich ausgeführt:

```powershell
.\gradlew.bat buildTessera :test-plugin:jar --console=plain --no-daemon --max-workers=2 --no-parallel
```

**BUILD SUCCESSFUL in 8m**, Exitcode `0`.
Log: `build/native-restore-final-full-build.log`.
Enthalten: erneute Patch-Anwendung, Server-Test-Suite, ausführbare Paperclip-JAR,
Checkstyle und `scanJarForBadCalls` für API und Server.

Unveränderte API-/Checkstyle-Testtasks nutzten im Gesamtbuild ihre gültigen
Up-to-date-Ergebnisse. Anschließend wurden beide ausdrücklich erneut ausgeführt:

```powershell
.\gradlew.bat :folia-api:test --rerun :paper-checkstyle:test --rerun --console=plain --no-daemon --max-workers=2 --no-parallel
```

**BUILD SUCCESSFUL in 19s**, Exitcode `0`.
Log: `build/native-restore-final-additional-tests.log`.

| Bereich | Erfasste Tests | Failures/Errors | Übersprungen |
| --- | ---: | ---: | ---: |
| Server | 10.153 | 0 | 87 |
| API | 529 | 0 | 2 |
| Checkstyle-Modul | 3 | 0 | 0 |

Vorhandene Assertions wurden nicht abgeschwächt. Die isolierten Unit-Tests
werden nicht als Ersatz für die folgenden nativen Server-/Clienttests ausgegeben.

## Dauerhafte Patches und Wiederanwendung

Die Komponenten aus `0046` (Minecraft) und `0035` (Paper-Server) werden durch
diese abschließenden Feature-Patches angebunden:

- `folia-server/minecraft-patches/features/0047-Complete-native-restore-login-admission-client-trans.patch`
- `folia-server/paper-patches/features/0036-Implement-native-player-restore-contract-one-with-du.patch`

Export über den vorhandenen Gradle-Patchworkflow, danach Wiederanwendung durch
`buildTessera`. Die Quellbäume vor Export und nach Wiederanwendung sind identisch:

| Quellbaum | Git-Tree-ID |
| --- | --- |
| Minecraft, vollständig | `c681fed63bbea4e72b7a5d0d0a7edeb9f1a4ab23` |
| Paper-Server, `src` | `fb6f679a76f2150b40febe1274f218a4a45d8832` |

Exportlog: `build/native-restore-final-export.log`. Der bestehende
File-Patch-Exporter meldete die fehlende Datei `src/main/resources/logo.png`;
der Feature-Export und Gesamtbuild waren erfolgreich. Beide Quellbaumvergleiche
bestätigen, dass die Java-Änderungen vollständig reproduziert wurden.

## Finale native Server-/Clientabnahme

Alle Runner starteten eigene markierte Testverzeichnisse unter `build` mit
freien Loopback-Ports, Java 25, echten verbundenen Offline-Protokollclients und
dem separaten Tessera-Testplugin. Keine produktiven Welten oder Saves wurden benutzt.

### Öffentliche Transaktion

Fixture: `build/native-restore-smoke-1790511055163`, Port `63670`.
Log: `build/native-restore-final-transaction.log`.
Ergebnis: Exitcode `0`, `publicTransactionAccepted: true`, Vertrag exakt `1`.

| Client | Logins | Kicks | Disconnects | Teleports | Stats-Pakete mit Nullwerten | Advancement-Resets |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| RestoreOne | 1 | 0 | 0 | 39 | 8 | 12 |
| RestoreTwo | 1 | 0 | 0 | 35 | 8 | 12 |

Messzeitraum: Login bis Abschluss der Assertions, vor dem beabsichtigten
Stoppen der eigenen Server. Kein Reconnect im erfolgreichen Hauptlauf.

Tatsächlich erreichte native Pfade:

- Zwei Spieler über getrennte Regionen/Dimensionen; öffentliche
  Prepare/Apply/Complete-Transaktion bei eingefrorenem Gameplay.
- Wiederholter Load und Resave, unveränderte Quelle, frischer vollständiger
  Rollback-Store und echter Rückwärts-Apply publizierter Dateien.
- Exakter Spielerzustand einschließlich Inventar/Endertruhe, XP, Gesundheit,
  Hunger, Attribute/Modifikatoren, Effekte, Spielmodus, PDC, Rezepte, Statistiken
  und Fortschritte; entfernte additive Einträge und Client-Caches zurückgesetzt.
- Stabile Spielerinstanz, Verbindung, Inventar-/Endertruhen-Wrapper; echte
  Teleportbestätigungen und auf Operation/Ereignis begrenzte Teleport-Scopes.
- Gespeichertes Boot samt Inventar, Schulterentity und genau eine eigene Perle.
- Fehlender Save-Eintrag: frischer Zustand in der expliziten Fallback-Welt.
- Wiederherstellung eines aktuell toten beziehungsweise schlafenden Spielers.
- Teleport-Veto, konkurrierende/späte Requests, idempotente Wiederholungen,
  verzögerte Writer sowie Cancellation der Caller-Futures.
- Native Save- und Publikationsfehler durch echte Windows-Dateisperren;
  Schranke blieb geschlossen, unzulässiger Commit wurde abgelehnt,
  anschließender Rollback und Complete funktionierten.
- Online-/Offline-Statistikzugriffe und veraltete Cache-Generationen;
  Runtime-Snapshot-Koordination einschließlich kontrolliertem `SOURCE_BUSY`.
- Vorbereitete Wüsten-/Rotsand- und Nether-Weltbindung; ein nach den Restores
  neu erzeugter Zielchunk behielt seine eigene Generierung statt globaler Defaults.
- Abschließender echter Runtime-Snapshot nach Freigabe des Tick-Freeze.

### Absichtliche Verbindungs-/Transferfehler

Fixture: `build/native-restore-smoke-1790511058499`, Port `63672`.
Log: `build/native-restore-final-races.log`. Exitcode `0`.

RestoreOne: 1 Login, 0 Kicks, 0 Disconnects.
RestoreTwo: 2 Logins, genau 1 absichtlich ausgelöster Kick und 1 Disconnect.
Dieser Fehlerlauf ist ausdrücklich **kein Seamless-Erfolgsnachweis**.

Geprüft: Disconnect während des Restore-Ereignisses, kanonischer Offline-Zustand,
bis Complete wartender Login, danach erfolgreiche Zulassung ohne Perlen-Duplikat,
tatsächlich vorenthaltener Client-ACK mit Timeout, nativer Rollback und erneutes Save.

### Shutdown-/Neustartmatrix

Log: `build/native-restore-final-recovery.log`. Exitcode `0`.
Fünf Phasen jeweils mit anschließendem Prozessneustart bestanden:

1. Nach Prepare.
2. Nach Apply, vor Commit.
3. Während Apply mit ausstehender echter Client-Transferbestätigung.
4. Nach bestätigtem Commit.
5. Prepare wartet auf einen bereits zugelassenen, in der Konfiguration
   angehaltenen dritten Client.

In jedem Prozess hatten die beiden Spielclients bis zum kontrollierten Stop
jeweils 1 Login, 0 Kicks und 0 Disconnects. Ein absichtlicher Serverneustart
wird nicht als unterbrechungsfreier Seamless-Lauf gezählt.

Vor Commit simuliert der Runner die **offline Recovery-Entscheidung des Callers**
durch Auswahl des frischen Rollback-Stores. Tessera spielt nach dem Boot keine
alte Transaktion darüber. Nach Commit bleibt der publizierte Stand bestehen.
Im wartenden Login-Fall gab es noch keinen fertigen Rollback-Store:
der native Shutdown publizierte die neuesten ungespeicherten Spielerwerte
(XP 111 statt zuletzt gespeicherter 999), bevor Prepare `SERVER_STOPPING` meldete.

Nach jedem Neustart: korrekter gewählter Store, keine verbliebene Loginsperre,
kein verzögertes Replay, erfolgreicher neuer vollständiger Restore und Resave.

## Nachweise und bekannte Prüfgrenzen

- [Build, Testzahlen, Hashes, Patchvergleich](test-evidence/26.3-010/build.json)
- [Öffentliche Transaktion und Client-Paketereignisse](test-evidence/26.3-010/native-transaction.json)
- [Absichtliche Verbindungsfehler und ACK-Timeout](test-evidence/26.3-010/native-races.json)
- [Fünf Recovery-Läufe mit Prozess-/Clientnachweisen](test-evidence/26.3-010/native-recovery.json)
- [Historischer Komponentenlauf mit Vertrag 0, nicht die finale Abnahme](test-evidence/26.3-010/historical-native-components.json)

Die Protokollclients verarbeiten echte Konfigurations-, Keepalive-, Transfer- und
Reset-Pakete, rendern aber kein Vanilla-Fenster. Native Laufzeitabnahme auf
Windows/Java 25; kein zusätzlicher Linux-Live-Lauf und kein simulierter
Hardware-Stromausfall oder defekter Datenträger. Die plattformspezifische
Publikation ist implementiert; diese Grenzen sind keine vorgetäuschten Tests.

Logs enthalten die vorhandene OSHI-Meldung zu beschädigten Windows-
Performance-Countern sowie vereinzelte anfängliche `moved too quickly`-
Warnungen der Testclients. Keine Regionszugriffs-Ausnahme oder fehlgeschlagene
native Assertion wurde in den finalen Läufen gefunden. Die Windows-Registrierung
wurde nicht verändert.

Der vollständige externe Stack MCC 0.7.5, MVE 26.3-1.5.00, TAB 6.2.0 und
LuckPerms 5.5.85 wurde **nicht ausgeführt**. Verfügbare MVE-JARs allein ersetzen
diesen Stack nicht; die gefundenen MCC-JARs hatten ältere Versionsstände.

Bei Einhaltung des unveränderten Vertrags 1 benötigt MCC grundsätzlich keine
neue Schnittstelle und soll den nativen Pfad selbst erkennen. Die offene
gemeinsame Prüfliste – MCC Load/Resave/Rollback, MVE Lifetime-/Vanilla-Stats,
Weltbindungen nach MCC-Reset und geöffnetes MCC-Backpack – steht im
[Restore-Status](mcc-player-restore-status.md).
