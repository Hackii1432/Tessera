# Tessera-Changelogs pflegen

Dieser Ordner ist die gemeinsame Release-Historie von **Tessera einschließlich
Sinopia**. Die Website liest die Release-Dateien serverseitig aus GitHub;
`README.md`-Dateien werden ignoriert. Die Root-README und die separat gestaltete
Website-Landingpage werden dadurch nicht automatisch eingebunden.

## Ablage und Zuordnung

- Neue Releases ausschließlich als `<minecraftVersion>/<version>.md` anlegen:
  genau eine Minecraft-Unterordnerebene und eine Datei pro Versionspaar.
- Dateiname und Ordner müssen exakt den **Strings** `version` und
  `minecraftVersion` entsprechen. Die Website sortiert nach Datum und Version.
- Bestehende Versionen und Veröffentlichungsdaten nicht nachträglich neu vergeben.
  Nicht jeder JAR-Build hat einen eigenen veröffentlichten Changelog-Eintrag.
- In der übernommenen Historie gehören RC2 und RC3 zur bereits verwendeten
  Minecraft-Gruppe `26.3`. Der gemeinsame Eintrag [0.0.1](26.3/0.0.1.md)
  kennzeichnet die unterschiedlichen Serverstände und deren Prüfungen ausdrücklich.
  Minecraft-Gruppe und tatsächliche RC-Protokollversion nicht verwechseln.
- Änderungen an Sinopia im zugehörigen Tessera-Release unter „Änderungen am
  Unterbau“ dokumentieren; keine eigene Sinopia-Versionsreihe erzeugen.
- Architektur, Arbeitsnotizen und ausführliche Prüfberichte bleiben unter `docs/`.
  Aus einer Release-Datei erreicht ein Link wie `../../BUILD-26.3-010.md` diese
  Berichte; Links zur Repository-Wurzel beginnen mit `../../../`.
- Fehlende Release-Daten zuerst in Dokumentation/Git prüfen und gegebenenfalls
  beim Verantwortlichen klären. Keine Daten oder Versionsnummern erfinden.

## Vorlage

Die folgenden Werte sind **nur eine Vorlage**. Vor dem Speichern alle Werte mit
belegten Angaben des tatsächlich veröffentlichten Releases ersetzen; nicht als
neue Release-Datei unverändert kopieren. Strings doppelt quotieren, Tags ebenfalls.
Die Prüfroutine akzeptiert bewusst diesen kleinen, YAML-1.2-kompatiblen Stil
(JSON-Escapes in Strings), nicht beliebige YAML-Anker oder mehrzeilige Skalare.

```markdown
---
version: "TESSERA-VERSION"
minecraftVersion: "MINECRAFT-VERSION"
title: "Kurzer Titel des tatsächlichen Releases"
description: "Wichtigste tatsächlich enthaltene Änderungen."
date: YYYY-MM-DD
status: alpha
breaking: false
tags:
  - "Tessera"
  - "Sinopia"
---

## Verbesserungen

- Tatsächlich enthaltene Verbesserungen und deren Prüfstand.

## Fehlerbehebungen

- Behobene Fehler, keine geplanten Maßnahmen.

## Änderungen am Unterbau

- Zugehörige Sinopia-/Paper-Änderungen; andernfalls ausdrücklich keine dokumentiert.

## Hinweise zum Update

Nur bei Bedarf: notwendige Schritte, Einschränkungen und noch offene Integrationsprüfungen.
```

`status` ist `stable`, `beta` oder `alpha`, niemals `planned`. `breaking` ist ein
Boolean, keine Zeichenkette. Optional sind `releaseUrl` und `downloadUrl` als
doppelt quotierte echte HTTP(S)-Adressen zulässig. Nur belegte Release-/Downloadziele
verwenden, keine Projekt-Homepage als scheinbarer JAR-Download und keine Tokens.
`changes: added/updated/fixed` wird nicht importiert: Änderungen gehören in Markdown.

## Prüfung vor Veröffentlichung

Im Repository-Hauptverzeichnis mit Node.js 22 oder neuer:

```text
node --test scripts/validate-changelogs.test.mjs
node scripts/validate-changelogs.mjs
```

Die bestehende Build-CI führt beide Befehle ebenfalls aus. Die Prüfung benötigt
keine npm-Installation. Sie kontrolliert Frontmatter, Datentypen, Kalenderdatum,
Versionspfade, Überschriften, lokale Links/Anker und die Importgrenzen: **200 Releases,
512 KiB pro Release, 8 MiB insgesamt und 30 Minecraft-Unterordner**. Bei Überschreitung
abbrechen und die Website-Verantwortlichen informieren; keine Historie auslassen.
Inhaltliche Behauptungen und tatsächliche externe Downloadziele zusätzlich prüfen.

Bestandsaufnahme, Zusammenführungen und offene historische Metadaten:
[Migrationsnachweis](../changelog-migration.md). Die Dokumentationsmigration
ändert keine Serverversion und veröffentlicht selbst kein Release.
