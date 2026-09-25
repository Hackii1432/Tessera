---
version: 0.0.7
title: "26.3 – Regionale TPS-Übersicht und Spielerabfragen"
description: "Tessera Build 007 – englische, klickbare TPS-Anzeige mit eigener Region, Spielervergleichen und ausführlichen Regionsdaten"
date: 2026-09-22
minecraftVersion: "26.3"
status: alpha
breaking: false
tags:
  - Tessera
  - TPS
  - Region-Threading
  - Commands
releaseUrl: "https://github.com/Hackii1432/Tessera/"
downloadUrl: "https://home.mosaikdev.com"
---

### TPS-Übersicht und Navigation

- `/tps` zeigt zuerst die eigene Region und danach die übrigen geladenen Regionen mit TPS, durchschnittlicher Tickzeit und sichtbaren Spielern.
- Eigene Region deutlich mit `YOUR REGION` gekennzeichnet und farbige Zustände `OK`, `BUSY`, `LAG`, `PAUSED`, `SPRINT` und `NO DATA` ergänzt.
- Regionsnamen und Spielernamen klickbar gemacht. Navigation, Aktualisierung und Spielersuche verwenden den bestehenden Namensraum `/paper:tps`; Klicks verändern keine Serverdaten und teleportieren niemanden.
- Sämtliche neuen Befehlsausgaben, Hilfetexte, Hover-Texte und Diagnosen auf Englisch ausgegeben.
- Lange Ausgaben in begrenzte Chatzeilen aufgeteilt und dabei Farben sowie Klickaktionen erhalten, damit nicht sämtliche Regionsdaten in einem einzelnen großen Chatpaket versendet werden.

### Spielerabfragen und Details

- `/tps player <name>` und die Kurzform `/tps <name>` für direkte Spielerabfragen ergänzt, einschließlich Vergleich mit der eigenen Region und Kennzeichnung gleicher beziehungsweise unterschiedlicher Regionen.
- `/tps region [ID]` für die eigene oder eine ausgewählte Region sowie `/tps list` für die Gesamtübersicht ergänzt.
- `/tps all` zeigt ausführliche Messwerte aller Regionen sowie gesondert gekennzeichnete globale Tickdaten und serverweite Chunk-Lade- und Generierungsraten.
- Bestehende Servermessungen für 5 Sekunden, 15 Sekunden, 1 Minute, 5 Minuten und 15 Minuten verwendet; keine zusätzlichen Messaufgaben und keine Plugin-Berechnungen eingeführt.
- Fehlende Messwerte ausdrücklich dargestellt. Statusbewertung an die konfigurierte Tickrate sowie `/tick freeze` und Tick-Sprint angepasst.

### Regionssicherheit und Sichtbarkeit

- Spielerzuordnungen aus der bereits veröffentlichten Regionszugehörigkeit gelesen, ohne Positionen fremder Spieler oder deren Entities auszulesen.
- Fremdthread-Aufrufe für Spieler über deren Entity-Scheduler weitergeleitet. Berechtigungs- und Sichtbarkeitsprüfung auf dem zuständigen Thread ausgeführt.
- Regionszuordnungen bei erneuten Abfragen neu aufgelöst und verschwundene Regionen, entladene Welten sowie vorübergehend fehlende Spielerzuordnungen mit klaren Hinweisen behandelt.
- Spielernamen, Vorschläge und angezeigte Spielerzahlen nach Bukkit-Sichtbarkeit gefiltert. Versteckte und nicht verbundene Spieler erhalten dieselbe Abfragerückmeldung.
- Keine Koordinaten oder Teleportationsaktionen ausgegeben und wiederholte Messwertabfragen auf eine akzeptierte Anfrage pro Spieler je 500 ms begrenzt.
- Berechtigung `bukkit.command.tps` beibehalten und geprüft. Die Anzeige misst Regionen, nicht die individuelle Last einzelner Spieler.

### Patchpflege und Buildstand

- TPS-Implementierung als eigenen Minecraft-Patch `0043` sowie ergänzenden Server-/Testpatch `0032` abgelegt.
- Buildnummer auf `007-alpha` angehoben und README, Build-Workflow sowie die TPS-Dokumentation aktualisiert.
- Minecraft `26.3`, Mache `26.3+build.1`, Java 25, Gradle `9.4.1` und Plugin-API-Version `26.3` beibehalten. Keine MVE-Änderungen vorgenommen.

