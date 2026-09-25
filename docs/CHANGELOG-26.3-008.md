---
version: 0.0.8
title: "26.3 – Paper-main-Integration und regionsichere Moonrise-Optimierungen"
description: "Tessera Build 008 & 007 – Paper-Korrekturen, PlayerPostEffects-API, Moonrise-Optimierungen und aktualisierte Build-Werkzeuge mit eigenen Folia-/Tessera-Anpassungen"
date: 2026-09-25
minecraftVersion: "26.3"
status: alpha
breaking: false
tags:
  - Tessera
  - Sinopia
  - Paper-Backports
  - Moonrise
  - Region-Threading
  - Bugfixes
releaseUrl: "https://github.com/Hackii1432/Tessera/"
downloadUrl: "https://home.mosaikdev.com"
---

### Paper-main-Integration und Gameplay-Korrekturen

- Änderungen aus Papers inzwischen zusammengeführtem `main`-Branch bis zum geprüften Stand `a15fed9` in die lokale Sinopia-Basis integriert. Bereits gleichwertig vorhandene Korrekturen beibehalten, ohne sie doppelt anzuwenden. Die ursprüngliche Paper-Importreferenz und bestehende Tessera-Anpassungen bleiben erhalten.
- Reparatur durch Mending für Gegenstände mit benutzerdefinierter maximaler Haltbarkeit korrigiert. Maßgeblich ist die Haltbarkeit des konkreten ItemStacks.
- Wettersynchronisierung nach dem Respawn korrigiert, einschließlich des Zurücksetzens auf klares Wetter sowie der Regen- und Gewitterstärke.
- Positionsauswertung von Kissen in Flüssigkeiten korrigiert, damit Flüssigkeitseffekte an der richtigen Blockposition verarbeitet werden.
- Drehwinkelvergleiche bei Spielerbewegungen normalisiert, damit ein Übergang über die Winkelgrenze keine falsche große Drehung ergibt.
- Beim Itemtransport entfernte oder ersetzte Zielcontainer erkannt und zusammengesetzte Container einschließlich beider Kistenhälften validiert.
- Migration veralteter `is_tempted`-Brain-Memory-Daten in den vorhandenen Vanilla-Datenmigrationspfad für 26.3 aufgenommen. DataConverter beibehalten.
- Eintragszahl des Gamerule-Pakets und Größe der Locale-Caches begrenzt.

### Plugin-API und Befehlsvervollständigung

- Papers `PlayerPostEffects`-API zum Abfragen, Setzen, Hinzufügen, Entfernen und Zurücksetzen visueller Nachbearbeitungseffekte für einzelne Spieler ergänzt.
- Post-Effects-Zugriffe an den aktuellen Spieler und dessen zuständigen Thread gebunden. Aufbewahrte API-Objekte folgen dem aktuellen Spieler-Handle nach einem Respawn; zurückgegebene Listen sind unveränderbare Momentaufnahmen.
- Das Suggestions-Event auch in den serverseitigen Pfad der Befehlsvervollständigung eingebunden und die Behandlung von Befehlsnamensräumen korrigiert.
- Leere Vorschlagslisten nicht mehr vor dem Plugin-Event verworfen, damit Plugins eigene Ergebnisse ergänzen können.

### Moonrise-Optimierungen

- Einen threadlokalen Cache für das Packen von Blockpaletten übernommen. Wiederverwendete Hilfsdaten bleiben auf den jeweiligen Thread begrenzt; gespeicherte Referenzen werden auch bei Fehlern freigegeben.
- Überflüssige NBT-Kopien bei exklusiver Datenübergabe reduziert. Mehrere verändernde Leser erhalten weiterhin voneinander unabhängige Kopien; ausstehende Schreibdaten bleiben vor Änderungen durch Leser geschützt.
- Abbruch des zuerst registrierten NBT-Leseauftrags korrigiert.
- Zusätzliche Kopien bereits abgetrennter Chunk-Sections beim Entladen über einen eigenen Serializer-Einstieg reduziert. Autosave, Shutdown-Save und Runtime-Snapshots behalten den kopierenden Pfad; Lichtdaten werden weiterhin kopiert.
- Entladene Chunks aus der Liste ausstehender Chunk-Broadcasts entfernt und diese Bereinigung an Folias regionslokale Datenhaltung angepasst.
- Chunk-Status-/Ticketlevel-Zuordnung aktualisiert und Leafpile auf `1.2.2` angehoben.

### Folia-/Tessera-Regionssicherheit

- Serverseitige Suggestions nach Abschluss fremdthreadiger Plugin-Futures über den Entity-Scheduler zum zuständigen Spieler-Thread weitergeleitet. Verbindung, Spieleridentität und Regionszuständigkeit vor und nach dem Event erneut geprüft.
- Den ausdrücklich asynchronen, vom Plugin behandelten Tab-Complete-Pfad sowie Tesseras vorhandene Syntax-, Tiefen- und Anfragebegrenzungen beibehalten.
- Bei Containerzugriffen Ladezustand und Regionsbesitz vor dem Zugriff auf BlockEntities geprüft. Fremde oder ungeladene Regionen werden dabei nicht synchron geladen.
- Mending nach Plugin-Events erneut auf Spieleridentität und Regionszuständigkeit geprüft, bevor das Inventar verändert wird.
- Chunk-Unload und Broadcast-Bereinigung auf dem zuständigen Regions-Thread abgesichert. Bestehende Folia-Threadchecks sowie die Listenübertragung bei Regions-Merge und -Split erhalten.
- Die Post-Effects-API bleibt synchron: Plugins verwenden dafür den Entity-Scheduler des Spielers. Keine automatische Verlagerung synchroner API-Aufrufe auf einen fremden Thread eingeführt.

### Patchpflege und Build-Werkzeuge

- Allgemeine Minecraft-Korrekturen als Sinopia-Patch `0045` und Moonrise-Optimierungen als Sinopia-Patch `0046` abgelegt.
- Eigene Tessera-Regionsanpassungen im Minecraft-Patch `0044`, ergänzende Implementierungs- und Teständerungen im Server-Patch `0033` sowie API-Dokumentation im API-Patch `0013` aufgenommen.
- Folias Basispatch an die aktualisierten Unload-Hooks angepasst und bestehende Patchdateien auf das Ausgabeformat der neuen Paperweight-Version gebracht.
- Gradle in beiden Build-Schichten gemeinsam auf `9.8.0` und Paperweight auf `2.0.0-beta.24` aktualisiert.
- Fehlende Task-Abhängigkeiten zum Sinopia-Checkout ausdrücklich ergänzt, damit kombinierte Patch- und Rebuild-Aufrufe Gradles Abhängigkeitsprüfung erfüllen.
- Einen versehentlich im Sinopia-Quellverzeichnis erzeugten Gradle-Cache ausgelagert und den dadurch blockierten `buildTessera`-Aufruf wiederhergestellt. Die Schutzprüfung gegen generierte Dateien in der versionierten Basis bleibt aktiv; die Ursache und der korrekte Root-Workflow wurden dokumentiert.
- Buildstand `008-alpha` übernommen. Minecraft `26.3`, Mache `26.3+build.1`, Java 25 und Plugin-API-Version `26.3` beibehalten. Keine MVE-Änderungen vorgenommen.

