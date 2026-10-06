# Build 015 – Native Welt-Vorgenerierung

Stand: 05.10.2026. Minecraft/API `26.3`, Java 25, Tessera `015-beta`.

## Umsetzung

`/pregen` verwendet den bestehenden regionsbewussten Chunk-Scheduler und
Moonrise-Worker/I/O statt eines Plugin-Generierungsloops. Administratoren wählen
die Dimension ausdrücklich mit `overworld`, `nether`, `end` oder einem geladenen
Namespaced-Dimensionsschlüssel; Auswahl und laufender Job sind getrennt. Ordner-
namen beziehungsweise Dimensionstypen werden nicht als eindeutiges Ziel verwendet.
Planung und Status zeigen den Dimensionsschlüssel. Bestehende Job-/Checkpoint-
Identitäten bleiben unverändert. Details und
Beispiele stehen in [der Befehlsreferenz](world-pregeneration.md).

Die Arbeit fordert `FULL`, nicht zusätzliche Entity-Ticking-Tickets an. Native
Generierung und Plugin-Callbacks behalten die Generator-/Biombindung der Zielwelt.
Die Aufnahme ist CPU-/lastabhängig begrenzt, MCA-lokal und bei sehr hohem Heap-
Verbrauch gesperrt; die Gesamtfläche hat innerhalb gültiger Minecraft-Koordinaten
keine künstliche Chunk-Anzahl-Grenze.

Generierung läuft unter `/tick freeze`, ohne Gameplay per Tick-Step fortzusetzen.
Neue, nur von der Vorgenerierung benötigte Holder können ihre eigenen Übergangs-
und Unload-Retry-Tickets in der Besitzerregion nach realer Zeit bereinigen.
Normale `UNKNOWN`-/Cooldown-Tickets und Spieler-/Plugin-Tickets werden nicht
pauschal abgelaufen gelassen. Ownership-Prüfungen bleiben aktiv.

Vor Fortschrittsbestätigung wird auf die tatsächlich eingereihten nativen
Chunk-/Entity-/POI-Schreibvorgänge und die eigenen Load-Ticket-/Task-Referenzen
gewartet. Das bedeutet nicht, dass sämtliche Hintergrundarbeit der ganzen Welt
beendet oder eine atomare Welttransaktion entstanden ist. Pause/Abbruch drainieren
zugelassene Arbeit; Abbruch entfernt keine Weltdateien.

Start/Wiederaufnahme öffnen die Aufnahme erst nach erfolgreicher Checkpoint-
Publikation. Nach Fehler/Neustart wird die ganze Fläche explizit revalidiert.
Runtime-Snapshot, Template-Sicherung und Unload erwerben dafür eine Admission-
Lease. Beim Shutdown wird die Aufnahme vor Folias erstem Scheduler-Halt geschlossen
und auf den noch laufenden Besitzern drainiert. Jobhistorie behält keine starken
Referenzen auf abgeschlossene oder entladene Weltinstanzen.

## Quellstand und Patches

- Branch: `ver/26.3.x`.
- Root-Basiscommit: `962d445b1a001708e7e4ed7bbd22c6c6feb18c4b`, **zusätzlich**
  die lokalen Build-015-Änderungen. Der Basiscommit allein enthält diese nicht.
- [Minecraft-Patch 0054](../folia-server/minecraft-patches/features/0054-Add-native-region-safe-pregeneration-and-freeze-awar.patch).
- [Server-Patch 0043](../folia-server/paper-patches/features/0043-Add-pregeneration-command-checkpoints-and-lifecycle-.patch).
- Nach erneuter Anwendung: generierter Minecraft-HEAD
  `2ebeb6d3b9f1fc790c075a9d19db6f49c624fbd4`, Server-HEAD
  `d3ad05f069b7d922e6844e5582415f7a773f4128`.
- Feature-Commits nur in den generierten Patch-Arbeitsrepos; kein Commit oder
  Push im Tessera-Hauptrepository. Bestehende Patches wurden fachlich nicht geändert.
- Kein Sinopia-/Upstream-Upgrade, keine MCC-/MVE-/Horizons-Produktänderung und
  keine neue öffentliche Bukkit-Pregenerator-API.

| Datei | SHA-256 |
| --- | --- |
| Minecraft-Patch `0054` | `72d8fa95fe9267e5c4f6280003acd20a00c7135d69d6a4a5505db8d25fa73530` |
| Server-Patch `0043` | `f7301057a9323d8c6c494be58e1a9eb4167a166a1a8fd2479a0f4931537e5054` |
| `gradle.properties` | `0235908e1a81de77ab574c8a6178652b25780feddb2cf5686fa9af78e5bede40` |

## Build und Qualitätsprüfung

Der finale vollständige Aufruf unter Java 25.0.3 auf Windows 11 war:

```powershell
.\gradlew.bat buildTessera --console=plain --no-configuration-cache --max-workers=2 --no-parallel
```

Ergebnis: **BUILD SUCCESSFUL**, 7 Minuten 43 Sekunden. `buildTessera` hat die
gesicherten Patches erneut angewendet und anschließend `test` und `build`
einschließlich vorhandener Checkstyle- und Bad-Call-Prüfungen ausgeführt.

- Server-Testbericht: 10.214 Fälle, 87 bestehende Skips, 0 Fehler/Failures.
- API-Testbericht: 529 Fälle, 2 bestehende Skips, 0 Fehler/Failures. Die unveränderte
  API-Suite wurde im finalen Gradle-Lauf aus dem gültigen Cache übernommen.
- Darin sechs neue Geometrie-/Checkpoint-Tests: negative Koordinaten, MCA-Grenzen,
  exakter Cursor/Count, Wiederaufnahme, Long-Zählung ohne Flächenmaterialisierung,
  ungültige Eingaben und sichere Dateipublikation.
- Zusätzlich drei Dimensionsauswahl-Tests: Standard-Aliase und exakte Keys statt
  Ordnernamen/Environment; ungültige, ungeladene und mehrdeutige Keys; passende
  Tab-Vorschläge ausschließlich für geladene Dimensionen.
- Neun vollständige API-Beispiele im ursprünglichen Build-015-Prüflauf gegen die
  gebaute API mit Java 25 kompiliert; die Dimensionskorrektur ändert keine API.
- API-Metadaten/Links geprüft; beide Dokumentationsvalidator-Testreihen mit 57
  bestandenen Tests. Keine Java-Beispiele oder vorhandenen Assertions abgeschwächt.
- Die neue Changelog-Metadatenstruktur und ihre Linkziele wurden einzeln geprüft.
  Der Gesamtlauf des Changelog-Validators bricht bereits beim unveränderten
  historischen `docs/builds/26.2/0.0.1.md` ab (unquotierte YAML-Strings und altes
  Abschnittsschema). Historische Einträge wurden nicht nebenbei umgeschrieben.

Buildlog: `build/pregen-dimension-buildTessera.log`.

## Abnahme auf der finalen Paperclip-JAR

Ausführbare Datei im Checkout:

```text
build/libs/tessera-server-26.3.build.015-beta.jar
```

Größe: **55.446.207 Bytes**.

SHA-256:

```text
ad9957ad8d65b0409acf31f8738ee8ec2243545e85f511914b307acfeb5863b6
```

[Nativer Runner](../smoke-tests/native-pregeneration/README.md), frische isolierte
Welt, Loopback-Port `64056`, vier Regions-Threads, ein nativer Generation-Worker
und ein I/O-Worker. Zwei echte Offline-Protokollclients blieben je Lauf verbunden.
Es wurden ausschließlich eigene Fixtures verwendet, keine Produktionswelten.

| Prüfung | Tatsächlich geprüftes Ergebnis |
| --- | --- |
| Berechtigung/Auswahl | Nicht-OP abgewiesen, OP zugelassen; Standard-Aliase/Keys/Tab-Vorschläge, Ablehnung von Ordnernamen und ungeladenen Dimensionen; getrennte Auswahl und gemeinsamer Zustand von `/pregen` und `/tessera:pregen`; 49 Overworld-Ziele trotz anschließender End-Auswahl |
| Freeze/Dimensionen | 81 Overworld- und 4 Nether-Ziele parallel mit Spielern in getrennten Dimensionen/Regionen; kein Unfreeze |
| Ticket-Bereinigung | Neu erzeugter Holder bei Chunk `1024,1024` unter Freeze tatsächlich entladen |
| Bestehende Daten | Wiederholung: 0 neu erzeugt, 81 gespeicherte `FULL` übersprungen |
| Steuerung | Moduswechsel, Pause, Resume und Cancel; Pause/Cancel bestätigen 0 In-Flight-Arbeit |
| Generatorbindung | Runtime-Dimension per exaktem Dimensionsschlüssel ausgewählt; eigene Noise-Implementierung und gespeicherter Diamantblock bei Y=60 erhalten |
| Native Save-Ablehnung | Chunk-`mustNotSave` auf dem Besitzer injiziert; Job 7 meldet Fehler nach Cleanup; erfolgreiche Revalidierung aller 4 Ziele |
| Checkpoint-Ablehnung | Pfad von Job 8 durch fixture-eigenes leeres Verzeichnis blockiert; Resume schlägt ohne neue Aufnahme fehl und erhält alten Fortschritt; nach Reparatur 81 Ziele erfolgreich |
| Runtime-Unload | Aktiver Job vor Storage-Close drainiert; abgebrochener Job nicht auf Ersatzwelt übertragen |
| Snapshot | Laufender Job vor Lifecycle-Wechsel koordiniert; Snapshot erfolgreich |
| Aktiver Shutdown | Nether-Job 11 mit zugelassener Arbeit gestoppt; beim Neustart pausiert, 2 Ziele gespeichert, 0 In-Flight |
| Neustart/Resume | Job 10 startet nicht automatisch; expliziter Fast-Resume revalidiert 1.089 Ziele, davon 2 bereits vollständig gespeichert |
| Persistenz | Gespeicherte Chunks ohne erneute Generierung geladen, einschließlich Chunk `4096,4096` nach Neustart |
| Verbindungen | Pro Lauf 2 Logins, 0 Kicks und 0 Quits vor Ergebnis; der absichtliche Serverneustart trennt selbstverständlich die Verbindungen zwischen den Läufen |

Beide Läufe: Exitcode 0, keine Test-Timeouts, Region-Tick-Ausfälle oder Cross-
Region-Ausnahmen. Die beiden konkret injizierten Fehler wurden erwartet und
inhaltlich geprüft; sie sind kein pauschaler Freibrief für andere Fehlerlogs.
Windows-OSHI/Perflib-Startmeldungen sind Umgebungsdiagnosen und werden nicht als
„komplett fehlerfreie Konsole“ ausgegeben.

Rohdaten: `build/native-pregeneration-1791230327751/result.json`, beide Serverlogs
und `pregen-checks.txt`/`pregen-recovery-checks.txt` daneben.
Eine kompakte Zusammenfassung ist [versioniert](test-evidence/26.3-015/native-pregeneration.json).
Gesamtlog der korrigierten JAR: `build/pregen-dimension-server.log`.

## Bestehende Runtime-Welt-Matrix

Zusätzlich wurde der gebaute Mojang-mapped **Bundler des ursprünglichen Build-015-
Pregenerator-Stands vor der Dimensionsauswahl-Korrektur** mit der unveränderten
fachlichen Matrix gestartet. Diese Zusatzmatrix verwendet weder den finalen
Paperclip-Launcher noch die nachträglich korrigierte Befehlsauswahl; sie wird
deshalb getrennt als vorheriger Lifecycle-Nachweis ausgewiesen. Die korrigierte
finale JAR wird mit der nativen verbundenen Client-Fixture darüber geprüft.

```powershell
.\smoke-tests\runtime-world-lifecycle\run.ps1 -KeepRuns -RunDirectory <frischer-Pfad-unter-smoke-tests/runtime-world-lifecycle/build/>
```

- `full`: PASS, 298 Assertions, darunter Create/Load/Save-Reload, 2/4/16 Clones,
  No-Save-Unload, Windows-Dateilöschung und 20 Lifecycle-Zyklen.
- `stop-create`, `stop-clone`, `stop-unload`: erwartetes `STOP_REQUESTED`, ordentlicher
  Exit und bestandene bestehenden Log-/Shutdown-Prüfungen.
- Verzeichnis: `smoke-tests/runtime-world-lifecycle/build/runs-pregen-015-1791228282417/`.
- Gesamtlog: `build/pregen-release-lifecycle.log`.
- Ohne Clients in dieser Zusatzmatrix bleiben ihre interaktiven Scoreboard-/
  Spielerprüfungen explizit ausgelassen; die Pregenerator-Abnahme darüber verwendet
  dagegen zwei verbundene Clients.

## Grenzen

- Keine vollständige Abnahme von MCC, MVE, Horizons, Chunky oder anderen
  Produktionsplugins. Deren Code wurde nicht geändert.
- Keine visuelle Vanilla-Clientprüfung, kein Langzeit-/Großwelt-Durchsatzbenchmark,
  keine simulierte volle Festplatte, kein harter Stromausfall und kein Linux-
  Serverlauf. Die Save-Ablehnung ist ein nativer Chunk-Veto-Test, kein Disk-full-Test.
- Checkpoint-Publikation ist nicht atomar mit allen Regiondateien. Native
  Schreibbestätigung ist keine allgemeine Power-loss-/Verzeichnis-fsync-Garantie.
- Fremde Tickets bleiben fremd; beliebige blockierende Plugin-Generatoren werden
  nicht gewaltsam unterbrochen. Lifecycle-/Shutdown-Timeouts dürfen keine falsche
  erfolgreiche Bereinigung melden.
- Generator-/Biomquellen-Klassen, Seed und Weltidentität werden verglichen, aber
  nicht sämtliche privaten Plugin-Generator-Einstellungen serialisiert.
- Chunky-Jobs werden nicht importiert. Bestehende Chunks werden nicht neu erzeugt;
  native Nachbarabhängigkeiten können angrenzende Teilchunks erzeugen.

[Changelog](builds/26.3/0.0.15.md) · [Befehlsreferenz](world-pregeneration.md)
