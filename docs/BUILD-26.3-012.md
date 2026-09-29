# Build 012 – Offline-Spielermetadaten beim nativen Restore

Stand: 29.09.2026. Minecraft/API 26.3, Java 25, Tessera `012-beta`.

## Quellstand und Umfang

- Branch: `ver/26.3.x`.
- Basiscommit: `47b63d6bb1dc5db99ddd1cab5000c49baaf682e0`.
- Darüber liegt der neue Server-Patch
  `folia-server/paper-patches/features/0038-Preserve-offline-player-identity-and-metadata-throug.patch`.
- Zusätzlich: nativer Patch
  `folia-server/minecraft-patches/features/0050-Queue-snapshot-barriers-after-releasing-region-enume.patch`
  und Server-Testpatch
  `folia-server/paper-patches/features/0039-Test-snapshot-region-barrier-ticket-lock-ordering.patch`.
- Kein neuer Commit oder Push im Tessera-Hauptrepository. Die neue Implementierung
  liegt als lokale, dauerhafte Patchänderung vor; der Basiscommit allein enthält
  sie noch nicht. Der interne Feature-Commit des generierten Patch-Arbeitsrepositories
  ist kein Tessera-Release-Commit.
- MCC, MVE und Sinopia-Produktcode unverändert; keine Upstream-Migration.

## Ausführbares Artefakt

- Datei: `build/libs/tessera-server-26.3.build.012-beta.jar`.
- Größe: **55.381.119 Bytes**.
- SHA-256: `c6443ecc2684b343f478e85ed18be927e577f1dff63e95a00a9c25238f5cd814`.
- Vollständiger finaler `buildTessera`-Durchlauf: **BUILD SUCCESSFUL**, 9 Minuten
  22 Sekunden; Log `build/build-012-beta-final.log`.
- Alle neuen Feature-Patches wurden exportiert und in diesem Build erneut
  angewendet. Die nachfolgenden Serverprüfungen verwenden genau diese JAR,
  nicht den früheren Zwischenbuild ohne Snapshot-Lock-Korrektur.

Da kein neuer Root-Commit erstellt wurde, wird die Quellbasis durch den oben
genannten Commit **plus** folgende lokale Dateien eindeutig eingegrenzt:

| Datei | SHA-256 |
| --- | --- |
| Server-Patch `0038` | `1d759c724af9e758846b101715acc53192bfb79ad479ac704a0988492c1477e7` |
| Minecraft-Patch `0050` | `258bbd2685be2c792819d1249dea406687761254f9a89848db82ce07bf2f38e7` |
| Server-Testpatch `0039` | `7da1b4ad52eea24490b0ea7ecf24a0dc193242efda1b7c038750f106ec87ab56` |
| `gradle.properties` | `12162183984a1f99a1fafd3fd5df11c1eba745eeecb817fb833303755d2f6e24` |

Die Anzeige `47b63d6` im JAR-Manifest ist die Basisrevision und kein Nachweis,
dass diese Änderungen bereits im Tessera-Git veröffentlicht sind.

## Korrektur

Der alte Preflight lud vorhandene Spielerdateien in einen losgelösten
`ServerPlayer` und machte dessen neu serialisierte Ausgabe zum persistenten
Datensatz. Dadurch wurden Vorschau-Identität und künstliche Login-/Seen-Zeiten
gespeichert; unbekannte Tags konnten verschwinden.

Bestehende Dateien werden jetzt nativ geladen/validiert, aber ihre gespeicherte
Fassung bleibt eine datenversionskonvertierte Kopie der Quelle mit den vorgesehenen
Positions-/Weltprüfungen. Keine Neuserialisierung einer Offline-Vorschau. Nur
ein tatsächlich verbundener Teilnehmer ohne Quell-Save benötigt frische native
Gameplay-Defaults. Verbundene Spieler werden weiterhin auf ihrem Owner aufgenommen,
übertragen und regulär gespeichert; die vorhandene Login-Sitzung bleibt erhalten.

Vorhandene Offline-JSON-Dateien bleiben bytegleich; fehlende Dateien bleiben
fehlend. Storekopien und vorbereitete NBT-Overlays erhalten die Dateizeit für
Bukkits historischen Rückfallwert. Die normalen Writer ändern ihre reguläre
Speichersemantik nicht. Transaktions-Fence, Generationen, Publish, Commit,
Rollback und Restore-Vertrag `1` bleiben bestehen.

Ein Wiederholungslauf zeigte außerdem eine bereits vorhandene Blockierung in
`RegionizedServer.awaitWorldRegionTasks`: `computeForAllRegions` hielt einen
nicht wiedereintrittsfähigen Read-Lock, während `task.queue()` für ein neues
Chunk-Ticket denselben Lock schreibend benötigte. Der Fix sammelt unter dem
Read-Lock nur unveränderliche Chunk-Koordinaten und reiht die Barrieren danach
über die vorhandene regionsauflösende Queue ein. Jeder Barrier-Future bleibt
Teil des Gesamtabschlusses; Einreihfehler bleiben Fehler. Der Test bildet
die Read-/Write-Lock-Abhängigkeit mit einem echten `StampedLock` nach und
schlägt bei falscher Reihenfolge unmittelbar fehl, statt den Testworker aufzuhängen.

## Tatsächlich ausgeführte Prüfungen

Windows, JDK `25.0.3`, Gradle `9.8.0`, eigene flache Testwelten, automatisch
gewählte Loopback-Ports und verbundene Protokollclients. Keine Produktionsdaten.
Der kompakte [maschinelle Nachweis](test-evidence/26.3-012/restore-metadata.json)
enthält Artefakthashes, Zähler und die lokalen Rohdatenpfade. Die Rohdaten unter
`build/` sind lokale Testartefakte und werden nicht als Serverquellen versioniert.

| Prüfung | Ergebnis / Umfang |
| --- | --- |
| Finaler Build und Patch-Wiederanwendung | Bestanden; neue Patches dauerhaft enthalten |
| Server-JUnit | 10.177 Testfälle, davon 87 übersprungen, 0 Fehler/Failures |
| API-JUnit-Auswertung | 529 Testfälle, davon 2 übersprungen, 0 Fehler/Failures; im finalen Build als unverändert wiederverwendet |
| Neue gezielte JUnit-Fälle | 3 Image-Tests, 1 Datei-Zeit-Test, 2 Regionsbarrieren-Tests bestanden |
| Build-/Git-Schutzmechanismen | 22 reale Fixture-Prüfungen bestanden |
| Dokumentationsvalidatoren | 57 Tests bestanden; API: 19 Artikel, 49 Links; 9 vollständige API-Beispiele kompiliert |
| Negativkontrolle mit Build 011 | Derselbe Metadaten-Test scheiterte am vollständigen Offline-NBT-Vergleich; bestätigt die Regression |
| Native Metadaten-Matrix | Wiederholtes Prepare/Apply/Complete, Resave, Rollback, Abmeldung während Prepare und unveränderte Quelle bestanden |
| Zusätzliche Snapshot-/Restore-Wiederholungen | Dieselbe native Metadaten-Matrix in zwei weiteren frischen Fixtures mit finaler JAR bestanden; kein erneuter Regionsbarrieren-Deadlock |
| Neustart ohne Clients | Offline-NBT, JSON, fehlende Felder/Dateien, Dateizeiten, Bukkit-Namen und Teilnehmermenge unverändert |
| Bestehende native Transaktionssuite | Vollständiger Spielerzustand, frische Defaults, Fahrzeuge/Schulterentity/Perle, Regionen-/Welttransfer und Generatorbindungen bestanden |
| Native Fehler-/Race-Suite | Transfer-Veto, echte Schreib-/Publish-Fehler, Cancellation, zugelassene Writer, wartender Login, fehlendes Client-ACK und Rollback geprüft; keine verbleibende Schranke |
| MVE-Integration für diesen Fehler | Zwei echte MVE-Neustarts und je zwei zusätzliche Refreshes ohne Login bestanden; persistentes Format 9 und gesamtes `lifetimePlayers`-Ledger unverändert |

Der Metadaten-Test vergleicht **das gesamte Offline-NBT**, nicht nur bekannte
Namensfelder. Die Quelle enthält unbekannte verschachtelte Erweiterungs-Tags,
historische Zeitwerte, PDC, zwei namenlose/cachelose UUIDs mit unterschiedlichen
fehlenden Compounds, reine Statistik-/Fortschrittseinträge und einen echten
Protokollspieler mit dem Namen `RestorePreview`. Vor Bukkit-Abfragen wird geprüft,
dass keine Vorschau im lokalen UUID-Profilcache oder Online-Register auftaucht.
Ein niemals gespeicherter Testteilnehmer erhält keine neue Datei.

### Verbindungszähler

Die Zähler gelten bis zum **anschließenden kontrollierten Testserver-Shutdown**.

| Lauf / Client | Logins | Kicks | Disconnects | Bedeutung |
| --- | ---: | ---: | ---: | --- |
| Öffentliche Transaktion / RestoreOne | 1 | 0 | 0 | Durchgehend verbunden; 38 Transfers, 8 Statistik-Resets, 12 Advancement-Resets |
| Öffentliche Transaktion / RestoreTwo | 1 | 0 | 0 | Durchgehend verbunden; 35 Transfers, 8 Statistik-Resets, 12 Advancement-Resets |
| Metadaten / RestoreOne | 1 | 0 | 0 | Durchgehend verbundener Kontrollspieler |
| Metadaten / RestoreTwo | 2 | 2 | 2 | Bewusst offline gesetzt, für Prepare-Race erneut verbunden und gezielt abgemeldet |
| Metadaten / RestorePreview | 1 | 1 | 1 | Echter Name, bewusst offline gesetzt |
| Race / RestoreOne | 1 | 0 | 0 | Durchgehend verbunden, Client-ACK gezielt zurückgehalten |
| Race / RestoreTwo | 2 | 1 | 1 | Genau eine absichtlich ausgelöste Abmeldung mit erneutem Login |
| Alle drei Neustartprozesse | 0 | 0 | 0 | Ein nativer Neustart und zwei MVE-Neustarts ohne Clients |

Die absichtlichen Abmeldungen sind Fehler-/Offline-Fixtures und werden nicht als
Seamless-Erfolg ausgegeben. Native Join-/Quit-Zähler der Metadaten-Matrix: exakt
4/3 tatsächliche Ereignisse, keine zusätzlichen Vorschau-Ereignisse.

### Tatsächlicher MVE-Prüfumfang

Verwendet wurden unverändert MVE `26.3-1.5.0.1-stable` und LuckPerms `5.5.85`.
Das eigens gebaute Testplugin greift lesend auf die echte MVE-Statistikverwaltung
zu und fordert deren bestehende Refresh-Funktion an; kein MVE-Produktcode wurde
geändert. HTTP und externe Integrationen waren in der eigenen Fixture deaktiviert.

Bukkit zählt exakt **5 vorhandene Spielerdateien**. MVE berücksichtigt zusätzlich
die **2 bereits in der Quelle vorhandenen JSON-only-UUIDs**, somit exakt **7**
bekannte Datensätze. Das ist MVE-Verhalten, kein durch Restore erfundener Spieler.
Die JSON-only-UUIDs bekommen weder eine Spielerdatei noch einen LastSeen-Wert.
Die unbekannten Namen werden nicht `RestorePreview`; der echte gleichnamige
Spieler bleibt dagegen erhalten. Die echten drei Testspieler besitzen den
nichtleeren Testzähler `deaths = 7`. Statistik-/Spielzeitwerte bleiben über
Refreshes stabil; im zweiten Prozess wird das vollständige persistierte
`lifetimePlayers` gegen den ersten Prozess verglichen.

### Vorherige Fehlversuche und Grenzen

- Build 011 scheiterte erwartungsgemäß am neuen NBT-Test
  (`build/restore-metadata-before-fix.log`).
- Ein Zwischenbuild mit Metadatenkorrektur blockierte bei einer weiteren
  Snapshot-Barriere nach Logout. Dieser reale Fehler wurde nicht ausgeblendet,
  sondern durch Patch `0050` und die gezielten Lock-Tests behoben.
- Die erste MVE-Fixture verwendete eine ältere, nicht Folia-fähige lokale
  LuckPerms-JAR. Die endgültige Abnahme verwendet nachweislich `5.5.85`.
- Die anfängliche MVE-Erwartung wurde an die tatsächliche Quellmenge angepasst:
  7 belegte NBT-/JSON-UUIDs statt 5 nur aus `.dat`. Es wird weiterhin die exakte
  Menge geprüft, nicht bloß eine Mindestzahl oder ein Erfolgslog.
- Ein finaler Vorlauf bestand die MVE-Assertions, konnte aber wegen MVE's
  überschriebenem `/stop` nicht regulär enden und zählt nicht als Abnahme.
  Der Teststarter verwendet jetzt `minecraft:stop`. Der akzeptierte Gesamtlauf
  startete in einer neuen Fixture; alle vier Prozesse endeten regulär mit Code 0.
- Windows-OSHI/Perflib meldet in dieser Umgebung fehlende Leistungszähler.
  Das ist keine Restore-Assertion. Die Meldung beim Patch-Export zum fehlenden
  `src/main/resources/logo.png` verhinderte weder Export noch Build.
- Der globale Changelog-Validator stößt weiterhin auf das unveränderte ältere
  `docs/builds/26.2/0.0.1.md` (`Use a double-quoted YAML string`). Der neue
  012-Eintrag und seine lokalen Links werden separat erfolgreich geprüft;
  historische Release-Dateien wurden nicht nebenbei verändert.
- Keine grafische Vanilla-Clientabnahme, kein Linux-Lauf und kein Stromausfalltest.
  Saubere Neustarts und die vorhandenen nativen Windows-Fehlerfälle wurden
  tatsächlich ausgeführt, nicht als Beleg für beliebige Hardwareausfälle ausgegeben.

## Reproduzierbare Prüfbefehle

Im Tessera-Checkout, Java 25 über `JAVA_HOME` auswählen:

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel --no-configuration-cache
node smoke-tests/native-player-restore/metadata.mjs "C:/Program Files/Java/jdk-25.0.3/bin/java.exe" build/libs/tessera-server-26.3.build.012-beta.jar
node smoke-tests/native-player-restore/run.mjs "C:/Program Files/Java/jdk-25.0.3/bin/java.exe" build/libs/tessera-server-26.3.build.012-beta.jar
```

Die Pfade zum Java-Executable an die eigene Umgebung anpassen. Die Runner
erstellen eigene markierte Fixtures unter `build`, verwenden freie Loopback-Ports
und übernehmen keine Produktionswelten. `metadata.mjs` startet die eigene Fixture
anschließend ohne Spieleranmeldung neu. Optional ergänzen die Umgebungsvariablen
`NATIVE_RESTORE_MVE_JAR` und `NATIVE_RESTORE_LUCKPERMS_JAR` zwei weitere Neustarts
mit den angegebenen unveränderten lokalen Plugin-JARs. Das isolierte MVE-Setup
verwendet LIFETIME und deaktiviert HTTP; keine Produktionskonfiguration wird kopiert.

## Reparatur und Integrationsgrenzen

Die Korrektur errät keine verlorenen Namen, Zeitwerte oder unbekannten Tags.
Der [Reparaturleitfaden](restore-player-metadata-repair.md) beschreibt Backup,
UUID-bezogenen Feldvergleich und kontrollierte Korrektur einschließlich bereits
übernommener MVE-Daten. Ein echter Spielername `RestorePreview` ist kein Fehlerbeweis.

Ein MVE-Refresh-Test nach Neustart ersetzt keine gemeinsame MCC-Welttransaktion.
MCC 0.7.5.5 wurde in dieser eigenen Umgebung nicht als ausführbarer Stack getestet;
die auffindbaren MCC-JARs sind ältere Versionen. MCC benötigt für den unveränderten
Vertrag 1 keinen neuen API-Pfad. Die konkrete Live-Kombination einschließlich
Backpack, Baselines, TAB und Challenge-Weltwechsel bleibt getrennt abzunehmen.
