# Tessera 26.3-010-alpha – tatsächlicher Build- und Prüfstand

Stand: 27.09.2026. **Unvollständiger Prüfbuild, kein freigegebener
MCC-Seamless-Load-Fix.** Der öffentliche Restore-Vertrag bleibt `0`.
Die native Zustandsersetzung ist integriert und mit verbundenen Clients
geprüft; die öffentliche Prepare/Apply/Complete-Dateitransaktion fehlt noch.

## Quellen und Artefakt

- Branch: `ver/26.3.x`.
- Ausgangsbasis: `6ededc28656e3adf1d2881d0e5e68854884353cf` (009).
- Exakter Quellcommit der geprüften JAR:
  `369e367b71deca62d2bef6018b669a3c22195f8d`.
- Laufzeitkennung im Serverlog: `26.3-010-369e367`.
- Minecraft/API `26.3`, Java `25.0.3`, Kanal `alpha`.
- Gradle `9.8.0`, Paperweight `2.0.0-beta.24`, Mache `26.3+build.1`
  unverändert übernommen. Sinopia und die Wolfs-KI-Patches aus 009 unverändert.
- Ausführbare JAR, 66.485.307 Bytes:
  `C:\Users\hunte\IdeaProjects\Tessera\build\libs\tessera-server-26.3.build.010-alpha.jar`.

SHA-256 der gebauten und tatsächlich gestarteten Datei:

```text
9f2229dc5c7760af9df5e4b5f162013434125309b6f8710a4dd72c1a7af0a4e6
```

Dieser Bericht und die archivierten Nachweise wurden nach dem Build ergänzt;
sie ändern den oben genannten Quellcommit der JAR nicht.

## Vollständiger Build

Im Tessera-Checkout ausgeführt, nicht nur vorgeschlagen:

```powershell
.\gradlew.bat buildTessera :test-plugin:jar --console=plain --no-daemon --max-workers=2 --no-parallel
```

Ergebnis: **BUILD SUCCESSFUL in 8m 2s**, Exitcode `0`.
Log: `build/native-restore-full-build.log`.
Enthalten waren die erneute Patch-Anwendung, Server-Tests, Build-Artefakte,
die vorhandenen Checkstyle-Aufgaben und `scanJarForBadCalls` für API und Server.
Die unveränderten API-/Checkstyle-Testtasks nutzten im Gesamtbuild zunächst
gültige Up-to-date-/Cache-Ergebnisse. Sie wurden anschließend ausdrücklich
erneut ausgeführt:

```powershell
.\gradlew.bat :folia-api:test --rerun :paper-checkstyle:test --rerun --console=plain --no-daemon --max-workers=2 --no-parallel
```

Auch dieser Lauf war erfolgreich, Exitcode `0`, Dauer 16 Sekunden.
Log: `build/native-restore-additional-tests.log`.

| Bereich | Erfasste Tests | Fehler/Failures | Übersprungen |
| --- | ---: | ---: | ---: |
| Server | 10.150 | 0 | 87 |
| API | 529 | 0 | 2 |
| Checkstyle-Modul | 3 | 0 | 0 |

Die zehn zusätzlichen Unit-Testfälle prüfen Store-Zulassungen und deren
Scope-Reihenfolge, die Owner-Queue einschließlich Unteraufgaben/Retirement
sowie die native Paketannahme bei geschlossener Schranke. Vorhandene
Assertions wurden nicht abgeschwächt. Diese Unit-Tests ersetzen keine native
Abnahme der öffentlichen Transaktion.

## Dauerhafte Patches und Wiederanwendung

- `folia-server/minecraft-patches/features/0046-Connect-native-player-restore-components-without-ena.patch`
- `folia-server/paper-patches/features/0035-Add-owner-drained-native-restore-components-and-admi.patch`

Export über die bestehenden `rebuildMinecraftFeaturePatches`- und
`rebuildPaperServerFeaturePatches`-Tasks. `buildTessera` hat die Patches danach
erneut angewendet. Die Git-Tree-IDs stimmen vor Export und nach Wiederanwendung
exakt überein:

| Arbeitsquellen | Git-Tree-ID |
| --- | --- |
| Minecraft, vollständiger Quellbaum | `3cabe701f98f7da0a3b6a6b2d90a795696da8593` |
| Paper-Server, `src` | `e6ee3616fe757557a0ccbfda5e9367a684c847e6` |

## Live-Test auf der finalen JAR

Ausgeführt mit Java 25:

```powershell
node smoke-tests/native-player-restore/run.mjs "C:/Program Files/Java/jdk-25.0.3/bin/java.exe"
```

Der Runner legte ausschließlich neue, eigene Testwelten unter
`build/native-restore-smoke-1790504791743` an. Er verwendete den freien
Loopback-Port `61912`, Offline-Protokollclients und nur das separate
Tessera-Testplugin. Keine produktiven Welten, kein MCC-/MVE-Produktcode.
Ergebnis: `NATIVE_RESTORE_EXIT 0 componentsPassed true publicTransactionAccepted false`.

| Client | Logins | Kicks | Disconnects | Empfangene Teleports | Stats-Pakete mit Nullwerten | Advancement-Resets |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| RestoreOne | 1 | 0 | 0 | 8 | 3 | 6 |
| RestoreTwo | 1 | 0 | 0 | 7 | 3 | 6 |

Messzeitraum: Login bis Abschluss aller Testassertions, vor dem beabsichtigten
Stoppen des eigenen Testservers. Es gab keinen Reconnect im Testablauf.
Die Clients verarbeiten echte Login-/Konfigurations-/Keepalive- und
Transferbestätigungs-Pakete; sie rendern kein Vanilla-Fenster.

Tatsächlich erreicht und geprüft wurden:

- Zwei Spieler mit getrennten Ownern in unterschiedlichen Regionen/Dimensionen;
  wiederholte native Transfers bei eingefrorenem Gameplay.
- NBT-Datenfixierung und Zustandsersetzung mit unveränderter Quelle, stabiler
  Spielerinstanz und erhaltenen Inventar-/Endertruhen-Wrappern.
- Inventar, XP, Gesundheit/Hunger, Attribute, Effekte, Spielmodus/Fähigkeiten,
  PDC und Rezepte; Entfernen zuvor vorhandener additiver Werte.
- Statistik-Ersetzung einschließlich entfernter Nullwerte am echten Client
  sowie tatsächlich empfangene Fortschritts-Reset-Pakete.
- Frischer Zustand ohne gespeicherten Eintrag und anschließendes erneutes
  Einspielen des ursprünglichen In-Memory-Zustands.
- Ein auf Ereignis und Operation begrenzter Teleport-Scope und ein echtes Veto.
- Eigene schwebende Enderperle: Ersetzung auf dem Ziel-Owner, genau eine
  registrierte Perle nach Restore und keine Perle nach frischem Zustand.
- Tatsächliche native Schreiber für alle drei Stores; normales `saveData`
  konnte bei geschlossener Testschranke keine der zuvor gelesenen Dateien ändern.
- Echte Runtime-Snapshots vor und nach dem Restore, einschließlich gelesener
  komprimierter Spieler-NBT-Dateien, korrekter XP und beider JSON-Store-Dateien.
  Ein zusätzlicher Snapshot wurde während der Schranke mit `SOURCE_BUSY` abgewiesen.

Der Live-Lauf meldete keine Regionszugriffs-Ausnahme. Er enthält einen
Startfehler der OSHI-Abfrage beschädigter Windows-Performance-Counter und
eine anfängliche `moved too quickly`-Warnung des Protokollclients. Diese wurden
nicht ausgeblendet; die Windows-Registrierung wurde nicht verändert.
Build/Javadoc sowie JOML/JLine geben außerdem die vorhandenen Warnungen aus.

Archivierte Nachweise:

- [Native Client-Ergebnisse mit Paketereignissen](test-evidence/26.3-010/native-components.json)
- [Build, Testzahlen, Hashes und Patch-Wiederanwendung](test-evidence/26.3-010/build.json)
- Lokales vollständiges Live-Log: `build/native-restore-final-live.log`.
- Testwelten, Snapshot-Dateien und weiteres Log im oben genannten Fixture-Verzeichnis.

## Nicht erfüllt / keine Freigabe für Vertrag 1

Die öffentliche Schnittstelle verwendet weiterhin `UnavailableBackend`.
Prepare und Vorwärts-Apply liefern `UNSUPPORTED`. Die Komponententests rufen
den nativen Einzelspielerpfad auf, **nicht** eine implementierte öffentliche
Dateitransaktion. Erneutes Laden eines In-Memory-Zustands ist kein Beleg für
Commit/Rollback veröffentlichter Spieler-Stores.

Es fehlen insbesondere die gemeinsame Store-Publikation mit frischem
Rollback-Backup und generationssicherem Rebind, das Erfassen von Logout-Daten
während der Schranke, durchgehende Legacy-Login-Zulassung, das nachweisliche
Beenden laufender Transfers bei Shutdown sowie dauerhafte plattformübergreifende
Recovery. Auch vollständige Attachment-Vorprüfung, Fahrzeuge/Schulterentities,
tote Spieler/laufende Respawns und Generator-/Biombindungen sind nicht vollständig
nativ abgenommen. Ein Timeout dürfte diese Arbeit nicht bloß als beendet melden.

Das sind noch nicht gelöste Implementierungslücken in Tessera, keine
fehlenden Nutzerpfade. Eine grundsätzlich unlösbare technische Schranke wurde
nicht nachgewiesen. Der vollständige Implementierungsauftrag ist daher mit
diesem Prüfstand ausdrücklich **nicht abgeschlossen**.

Der konkrete MCC-0.7.5-/MVE-1.5.00-/TAB-/LuckPerms-Stack wurde nicht geprüft.
MCC erkennt hier weiterhin Vertrag 0 und kann damit den nativen Seamless-Pfad
nicht nutzen. Es wurde kein neuer API-Pfad eingeführt; eine MCC-Produktänderung
ist für den unveränderten Zielvertrag 1 nicht vorgesehen. Erforderlich sind
zuerst die vollständige Tessera-Transaktion und anschließend die gemeinsame
Plugin-Abnahme. Deren genaue offene Prüfliste steht im
[Restore-Status](mcc-player-restore-status.md).
