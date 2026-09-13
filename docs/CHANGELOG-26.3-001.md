---
version: 0.0.1
title: "26.3-rc-2 – First alpha build"
description: "Tessera Build 001 – 26.3-rc-2-Portierung, integrierte Sinopia-Basis und Fehlerkorrekturen"
date: 2026-09-13
minecraftVersion: "26.3"
status: alpha
breaking: false
tags:
  - Tessera
  - First alpha build
  - Sinopia
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

### Bestehende Tessera-Funktionen beibehalten

- Vorhandene Tick-, Gamerule-, Enderperlen-/Stasis-, Locatorbar- und Golem-Patches mitportiert.
- Vorhandene Portal-, Respawn-, Runtime-World-, Snapshot- und Spieler-Restore-Erweiterungen in der Patchserie erhalten.
- Bestehende regionsichere Block-Nachbearbeitung für Laufzeit-Strukturen und die zugehörigen APIs beibehalten.
- Diese Funktionen sind keine neuen 26.3-Features; ihre bestehenden Implementierungen wurden auf die neue Basis übernommen. MCC- und MVE-Code wurden dafür nicht geändert.

### Prüfstand und Hinweise

- Vollständigen Tessera-Build einschließlich Patch-Anwendung, Standardtests, Checkstyle, Prüfung verbotener API-Aufrufe und Paperclip-Erstellung erfolgreich ausgeführt.
- Zusätzliche Regressionstests für Build-/Git-Integration, Datenmigration, Storage, Regionszugriffe, Betten, Gamerules und Poplar ergänzt.
- Der zuletzt geprüfte Gesamtstand enthält 10.321 Einträge in den XML-Testberichten, ohne Fehler; 89 Einträge wurden übersprungen. Alle 45 Bett-, Gamerule- und Poplar-Regressionstestfälle bestanden ohne Überspringen.
- Die erzeugte Server-JAR mit Java 25.0.3 und `--version` erfolgreich geprüft. Dieser Launcher-Test startet keine Spielwelt.
- Der Build bleibt eine Alpha-Version auf Basis von Minecraft `26.3-rc-2`. Erfolgreiche Builds und automatisierte Tests ersetzen keine vollständige Ingame-Abnahme mit den eingesetzten Plugins.
- Vor einem Einsatz bestehende Welten sichern und auf einer separaten Testwelt insbesondere Weltmigration, Regions- und Dimensionswechsel sowie die korrigierten Gameplay-Funktionen prüfen.
