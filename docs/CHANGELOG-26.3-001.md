---
version: 0.0.1
title: "26.3-rc-2 – Sinopia, Paper-Backports und Fehlerkorrekturen"
description: "Tessera Build 001 – 26.3-rc-2-Portierung, integrierte Sinopia-Basis, Paper-Backports und regionssichere Fehlerkorrekturen"
date: 2026-09-14
minecraftVersion: "26.3"
status: alpha
breaking: false
tags:
  - Tessera
  - First alpha build
  - Sinopia
  - Paper-Backports
  - Bugfixes
releaseUrl: "https://github.com/Hackii1432/Tessera/"
downloadUrl: "https://home.mosaikdev.com"
---

## Beschreibung

### Portierung auf Minecraft 26.3-rc-2

- Tessera auf Minecraft `26.3-rc-2` und die Plugin-API-Version `26.3` umgestellt. Der aktuelle Build wird als `001-alpha` geführt.
- Build-Konfiguration und Patch-Kontexte an Java 25, Paperweight `2.0.0-beta.23` und Mache `26.3-rc-2+build.2` angepasst.
- Klassen-, Methoden- und Signaturkonflikte zwischen der neuen Paper-/Minecraft-Basis und Folias Regions-Code behoben.
- Alle 34 zunächst nicht angewendeten Upstream-Feature-Patches portiert und aktiviert. Dazu gehören Moonrise, Starlight, Entity Activation Range sowie Netzwerk-, Tracker-, Pathfinder- und I/O-Optimierungen.
- Die bisherigen Folia-/Tessera-Minecraft-, Server- und API-Patchserien auf die neue Basis angepasst. Die früheren Portierungskonflikte sind aufgelöst; der vollständige Server lässt sich wieder bauen.
- Fehlerhafte Übernahme einer Paper-internen Git-Update-Referenz korrigiert, die die Einrichtung der Minecraft-Quellen im Tessera-Repository blockierte.
- Datenmigration für 26.3 ergänzt: Der bestehende DataConverter wird für neuere Datenversionen mit den originalen Vanilla-Datafixern verbunden. Neue Blockstate-Schlüssel und zusammengelegte Terrain-Chunk-Status werden berücksichtigt.
- End-Gateway-Erzeugung und die Generierung benötigter Zielinseln an die neue Feature-Registry angepasst.

### Sinopia als integrierte Basis

- Sinopia als eigene, Paper-abgeleitete Basis direkt in das Tessera-Repository aufgenommen. Kein Submodule und kein zweites zu pflegendes Projekt erforderlich.
- Die integrierte Basis auf Sinopia umbenannt und die zugehörigen Branding-Patches angepasst. Das fertige Serverprodukt bleibt Tessera.
- Technische Paper-/Bukkit-Paketnamen und interne Kompatibilitätspfade beibehalten.
- Der Build verwendet die lokalen Sinopia-Quellen statt eines automatischen Paper-GitHub-Checkouts. `paperRef` dokumentiert die Herkunft der Basis und lädt allein keine neue Version mehr herunter.
- Ursprüngliche Lizenz-, Herkunfts- und Autorenhinweise bei der Aufnahme von Sinopia erhalten. Änderungen an Minecraft bleiben als Patches gespeichert; generierter Minecraft-Code gehört nicht in die Versionsverwaltung.

### Build- und Patch-Workflow

- Zentralen Gradle-Task `buildTessera` eingeführt: Sinopia vorbereiten, alle Patchschichten anwenden, Tests ausführen und die startbare Tessera-JAR erstellen.
- CI auf den zentralen Build-Einstieg umgestellt und um Prüfungen der Build-Unterstützung ergänzt.
- Separaten generierten Sinopia-Arbeitsbereich zum Bearbeiten, Wiederaufbauen und Übernehmen eigener Minecraft-Patches ergänzt.
- Schutzprüfungen gegen das Zurücksetzen bearbeiteter Arbeitskopien und das Überschreiben zwischenzeitlich geänderter Basisdateien eingebaut.
- Den bisherigen normalen `build`-Task für bereits vorbereitete Quellen beibehalten. Nach Änderungen an Basis oder Patches übernimmt `buildTessera` die nötige Vorbereitung automatisch.

### Regionssicherheit und 26.3-Kompatibilität

- Befehlsrückmeldungen an den neuen Vanilla-Ergebnis-Tracker angepasst. Regionsübergreifende Befehle sammeln Ergebniswerte und Namen; Entity-Änderungen bleiben auf dem jeweils zuständigen Regions-Thread.
- Spieler-Rückmeldungen auf den aktuellen Spieler-Thread zurückgeführt und entfernte beziehungsweise nicht mehr verfügbare Ziele beim Abschluss ausstehender Befehle berücksichtigt.
- Den neuen `/compute`-Pfad vor fremden Regionszugriffen abgesichert. Nicht unterstützte Welt-, Entity- oder Blockkontexte werden mit einer verständlichen Fehlermeldung abgelehnt, ohne synchrones Chunk-Laden auf dem Global-Thread.
- Zeitsynchronisierung an die neuen Welt-Clock-Daten angepasst und individuelle Spielerzeiten berücksichtigt. Verzögerte Updates prüfen den aktuellen Weltkontext des Spielers erneut, damit sie nach einem Dimensionswechsel nicht auf einen veralteten Kontext angewendet werden.
- Verwaltung gemeinsam genutzter Terrain-Density-Puffer auf einmal pro globalem Welt-Tick umgestellt. Die neue Vanilla-Terrain-Pipeline bleibt erhalten.

### Bett- und Strohbett-Korrekturen

- Rekursive Aufrufkette beim Schlafen behoben, die bei normalen Betten und Strohbetten einen Stackoverflow verursachen konnte.
- Bukkit-Aufrufe über `sleep(Location, boolean)` reichen den `force`-Wert wieder korrekt weiter.
- Plugin-Abbrüche, die Ablehnung toter Spieler sowie Vanilla-Regeln für Schlafen, Spawnpunkte und Strohbetten beibehalten.
- Die Minecraft-Korrektur als eigenen Sinopia-Patch `0036` aufgenommen und mit zwölf Regressionstestfällen abgesichert.

### Schlafposition für andere Spieler

- Versetzte Darstellung schlafender Spieler bei Beobachtern korrigiert, insbesondere beim Straw Bed. Die Position wird bereits beim Hinlegen synchronisiert und nicht erst beim Aufstehen berichtigt.
- Schlaf-Metadaten und absolute Position in einem zusammengehörigen Paket-Bundle übertragen. Dadurch wird der Positionssprung zum Bett nicht doppelt auf die Client-Interpolation angewendet.
- Schlafbeginn, Wechsel der Schlafposition und Aufstehen berücksichtigt; die Positionsbasis für nachfolgende Bewegungen bleibt synchron.
- Wiederholte Zusatzpakete bei unverändertem Schlafzustand vermieden. Normale Betten und die separate Positionskorrektur des schlafenden Spielers bleiben erhalten.
- Die Korrektur als eigenen Tessera-Patch `0035` aufgenommen und mit 13 Regressionstestfällen abgesichert.

### Schildbearbeitung nach dem Platzieren

- Fehler behoben, durch den direkt nach dem Platzieren eingegebener Schildtext mit „just tried to change non-editable sign“ abgelehnt wurde.
- Doppeltes automatisches Öffnen des Schildeditors entfernt. Der Editor wird nach einer erfolgreichen Platzierung genau einmal geöffnet.
- Plugin-Abbrüche, Wachsschicht, Bearbeitungsfreigabe und Vanilla-Prüfungen beibehalten.
- Die Korrektur als eigenen Tessera-Patch `0036` aufgenommen und mit 16 Regressionstestfällen abgesichert.
- Zusätzlich das Zeichenlimit für Schildzeilen korrigiert: Positive Limits zählen Unicode-Codepoints, ohne Surrogatpaare zu zerschneiden. Ein Wert von `0` oder kleiner deaktiviert das Limit.

### Drachen-Respawn und Regionssicherheit

- Den ungeschützten Kristallzugriff beim Abschluss der Drachen-Wiederbeschwörung abgesichert, der zur Ausnahme „Cannot remove entity off-main“ und zum Serverstopp führen konnte.
- Welt, Regionszuständigkeit und Gültigkeit der Kristallreferenzen vor den Respawn-Phasen sowie erneut nach Spawn- und Explosions-Callbacks geprüft.
- Beschwörungen mit unzulässigen Kristallreferenzen vor der Phase kontrolliert abgebrochen. Fremde oder übertragene Kristalle werden nicht vom falschen Regions-Thread verändert oder entfernt.
- Diagnose auf eine Warnung pro Beschwörungsversuch begrenzt. Reguläre Vanilla-Phasen, Zeiten und Explosionen beibehalten.
- Die Korrektur als eigenen Tessera-Patch `0033` aufgenommen und mit 21 Regressionstestfällen abgesichert.

### Konsole

- Wiederholte Info-Ausgabe „Player … standing on air - force-sending blocks below“ auskommentiert und als Tessera-Patch `0034` gespeichert.
- Die zugrunde liegende erneute Übertragung der Blöcke unter dem Spieler bleibt unverändert aktiv.

### Gamerule-Rückmeldungen

- Falsche Meldung „Game rule … is already set to …“ nach einer tatsächlich erfolgreichen Gamerule-Änderung behoben.
- Erfolgreiche Änderungen melden jetzt den wirksamen neuen Wert. Tatsächlich unveränderte Werte behalten die entsprechende „already set“-Meldung.
- Plugin-Abbrüche und von Plugins angepasste Werte bei Rückmeldung und Befehlsstatus korrekt berücksichtigt.
- Das erfolgreiche Setzen einer booleschen Gamerule auf `false` bleibt erfolgreich, auch wenn der numerische Rückgabewert `0` ist.
- Die Korrektur als eigenen Sinopia-Patch `0037` aufgenommen und mit elf Regressionstestfällen abgesichert.

### Poplar-Wachstum und Server-Crash

- Fehlende Bukkit-Zuordnung für rote, orangefarbene und gelbe Poplar-Bäume ergänzt.
- Fehler `Unknown tree generator` beim Einsatz von Knochenmehl auf `poplar_sapling` behoben.
- Dieselbe Fehlerursache beim natürlichen Wachstum behoben, die einen Region-Tick abbrechen und den Server herunterfahren lassen konnte.
- `TreeType.RED_POPLAR`, `TreeType.ORANGE_POPLAR` und `TreeType.YELLOW_POPLAR` ergänzt. Die Reihenfolge der bisherigen API-Werte bleibt erhalten.
- Bukkit-Baumgenerierung verwendet für diese Typen die passenden Vanilla-Features und fällt nicht auf Eiche zurück.
- Die Baumtyp-Zuordnung bleibt in Tessera threadlokal. Vanilla-Farbgewichtung, Baumformen, Wachstumsvoraussetzungen und Plugin-Abbruchbehandlung bleiben unverändert.
- Die Minecraft-Korrektur als eigenen Sinopia-Patch `0038` samt API-/Folia-Anpassungen aufgenommen und mit 22 Regressionstestfällen abgesichert.

### Paper-Backports in Sinopia

- Ausgewählte Korrekturen aus Paper `dev/26.3` bis einschließlich Commit `e285a336a6bc7ccdec8803d528c67c80d431760a` in die lokale Sinopia-/Tessera-Basis übernommen.
- Die ursprüngliche Importreferenz `38b0bfeb67855206ede9cb1df4f3354c4611c4c2` beibehalten. Die Übernahme erfolgt selektiv und nicht als vollständiger Austausch der Basis.
- Allgemeine Minecraft-Korrekturen im eigenen Sinopia-Patch `0039` zusammengefasst. Regionsabhängige Ergänzungen separat in Tessera gespeichert.

### Partikel-API

- Öffentliche Partikel-API um getrennte Geschwindigkeitswerte für X, Y und Z erweitert.
- Randomisierungsarten `DEFAULT`, `ALTERNATIVE` und `ALTERNATIVE_WITH_SPEED` in die API aufgenommen.
- `ParticleBuilder`, Welt- und Spieler-Methoden sowie die CraftBukkit-Paketübertragung an die erweiterte 26.3-Partikelstruktur angepasst.
- Bisherige öffentliche Overloads erhalten. Ein einzelner Geschwindigkeitswert wird auf alle drei Achsen übertragen; die bisherige Standardrandomisierung bleibt erhalten.
- `ParticleBuilder.extra(double)` als kompatiblen, veralteten Einstieg beibehalten und auf die neue Geschwindigkeitsbehandlung weitergeleitet.

### Blockabbau über größere Entfernungen

- Abbruchpakete für begonnenen Blockabbau auch außerhalb der normalen Interaktionsreichweite innerhalb der vorgesehenen 32-Block-Grenze zugelassen.
- Paketannahme und Verarbeitung auf den zuständigen Spieler-/Regions-Thread, die aktuelle Welt und bereits geladene Chunks begrenzt.
- Gespeicherte frühere Abbaupositionen vor erneutem Zugriff auf Regionszuständigkeit geprüft.
- Synchrone Chunk-Ladevorgänge und Zugriffe auf fremde Regionen in diesem Pfad ausgeschlossen.
- Die regionssichere Ergänzung als eigenen Tessera-Patch `0037` aufgenommen.

### Zeitbefehl und Plugin-Ereignisse

- Rückmeldung des Zeitbefehls korrigiert: Der bisherige Wert wird vor der Änderung erfasst und mit dem tatsächlich freigegebenen neuen Wert verglichen.
- Verhindert, dass eine durch Plugins abgebrochene Zeitänderung die Weltuhr trotzdem verändert.
- Von Plugins angepasste Zeitwerte korrekt übernommen; tatsächlich unveränderte Werte behalten die entsprechende Rückmeldung.
- Bestehende globale Clock-Zuständigkeit und regionsbezogene Verteilung an Spieler erhalten.
- `EntityChangeBlockEvent` bei Blocktransformationen vor Blockänderung, Drops und Itemverbrauch ausgelöst. Bei Abbruch bleiben diese Änderungen aus; erforderliche Inventarsynchronisierung bleibt erhalten.

### Weitere Gameplay- und API-Korrekturen

- Bukkit-Zuordnung der Kolbenreaktionen an die aktuelle Vanilla-Reihenfolge angepasst.
- Neuberechnung des Advancement-Baums nach Änderungen korrigiert; leere Änderungen lösen keine unnötige Neuanordnung aus.
- Synchronisierung aller vier Braustand-Menüwerte wiederhergestellt.
- `BrewingStartEvent` verwendet die tatsächliche Rezeptbrauzeit statt eines fest vorgegebenen Werts.
- Doppeltes Weiterschalten der Note bei Notenblöcken korrigiert und die Einstellung für deaktivierte Noten-Updates berücksichtigt.
- Bei Bett-Explosionen den ursprünglichen Blockzustand vor dem Entfernen gesichert und als Schadensursache weitergegeben.
- Konfigurierten Seed für verlassene Lager bei der Strukturplatzierung berücksichtigt.
- Größenprüfung für NBT-Long-Arrays vor Speicherreservierung und Allokation ergänzt; negative und übergroße Längen werden abgewiesen.

### Bestehende Tessera-Funktionen beibehalten

- Vorhandene Tick-, Gamerule-, Enderperlen-/Stasis-, Locatorbar- und Golem-Patches mitportiert.
- Vorhandene Portal-, Respawn-, Runtime-World-, Snapshot- und Spieler-Restore-Erweiterungen in der Patchserie erhalten.
- Bestehende regionsichere Block-Nachbearbeitung für Laufzeit-Strukturen und die zugehörigen APIs beibehalten.
- Diese Funktionen sind keine neuen 26.3-Features; ihre bestehenden Implementierungen wurden auf die neue Basis übernommen. MCC- und MVE-Code wurden dafür nicht geändert.

### Automatisierte Prüfung

- Vollständigen Tessera-Build einschließlich Patch-Anwendung, Standardtests, Checkstyle, Prüfung verbotener API-Aufrufe und Paperclip-Erstellung erfolgreich ausgeführt.
- Zusätzliche Regressionstests für Build-/Git-Integration, Datenmigration, Storage, Regionszugriffe, Betten, Gamerules und Poplar ergänzt.
- 53 zusätzliche Regressionstestfälle für die Paper-Backports ergänzt. Zusammen mit 41 bestehenden Schild-/Bett-Testfällen liefen alle 94 gezielt geprüften Fälle erfolgreich durch.
- Der vollständige Build vom 14.09.2026 enthält 9.892 gemeldete Server-Testfälle und 529 API-Testfälle, ohne Fehler oder Fehlschläge; insgesamt 89 Fälle wurden übersprungen.
- Alle gespeicherten Patchschichten erneut erfolgreich angewendet. Die erzeugten Minecraft- und Server-Quellbäume stimmen vollständig mit dem zuvor getesteten Stand überein.
- Den Launcher im Rahmen der Portierung mit Java 25.0.3 und `--version` erfolgreich geprüft, ohne eine Spielwelt zu starten.
- Ausführbare Server-JAR `tessera-server-26.3-rc-2.build.001-alpha.jar` erfolgreich erstellt.
