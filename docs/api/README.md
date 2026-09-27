# Pflege der API-Referenz

Die Website importiert die Artikel direkt aus `docs/api` auf `ver/26.3.x`.
Diese `README.md` ist nur für Repository-Beitragende bestimmt und wird nicht
als Artikel importiert. Die Referenz beginnt bei [index.md](index.md).

## Öffentliche API und Quellabgleich

Bei jeder öffentlichen API-Änderung müssen die betroffenen Artikel **zusammen
mit dem Code und seinen dauerhaften Patches** aktualisiert werden. Dazu zählen
Signaturen, Ergebnisstatus, Fehler, Thread-/Ownership-Verträge, Lifecycle,
Abhängigkeiten, Deprecations und Verhalten bei Plugin-Disable. Das gilt auch für
integrierte Sinopia-/Paper-Änderungen, die dokumentierte Verträge beeinflussen.

Prüfgrundlage sind die deklarierte API, die tatsächlich gepatchte Implementierung
und Tests, nicht nur frühere Beschreibungen. Tessera-Erweiterungen, geerbte
Paper-/Folia-APIs und Implementierungsdetails ausdrücklich unterscheiden.
Interne Queues, CraftBukkit/NMS und geplante Funktionen sind keine öffentlichen
Plugin-Verträge. Vorhandene Javadoc ergänzen, nicht die gesamte Paper-API kopieren.
Nachweise und interne Architekturberichte gehören außerhalb des Importordners.

## Dateien und Metadaten

- Nur eigenständige Markdown-Artikel direkt in diesem Ordner; keine Unterordner.
- Dateinamen: `[a-z0-9-]{1,80}.md`. Einzige Ausnahme: diese ignorierte `README.md`.
- Dateinamen sind dauerhafte Website-Links. Nicht beiläufig umbenennen.
- Höchstens 100 Artikel, 512 KiB pro Datei, insgesamt 8 MiB. Der lokale Validator
  zählt die README für Dateigröße/Gesamtgröße konservativ mit.
- Alle Artikel starten mit YAML-Frontmatter. Unser überprüfbarer YAML-Teilumfang
  verwendet für Textfelder doppelt zitierte Strings (JSON-kompatibles Escaping),
  eine numerische `order` und ein unzitiertes ISO-Datum. Keine Aliase, Tags oder
  implizit typisierten Textwerte. Das ist reguläres YAML, keine neue Website-API.

```yaml
---
title: "Scheduler und Threadkontexte"
description: "Aufgaben auf dem passenden Scheduler ausführen."
navTitle: "Scheduler"
order: 30
updated: 2026-09-28
minecraftVersion: "26.3"
badge: "Referenz"
---
```

`title` (maximal 200 Zeichen) und `description` (600) sind Pflicht. Optional:
`navTitle` (100), `order` (endliche Zahl, Standard 100), `updated` (gültiges Datum
`YYYY-MM-DD`), `minecraftVersion` (String, 40), `badge` (80). Leere Textwerte nicht
verwenden. Tatsächliches Bearbeitungsdatum und anhand des Codes belegte Zielversion
setzen; das Muster nicht blind kopieren. `index.md` hat ausdrücklich `order: 0`.

## Navigation, Inhalt und Links

`order` sortiert aufsteigend; die bestehenden Abstände lassen Raum für neue Themen.
Neue Themen auch im Einstieg verlinken. Jeder Artikel beginnt mit einer kurzen
Einleitung, ohne wiederholten H1-Seitentitel. Abschnitte verwenden `##`, `###`
und `####`, mit Leerzeilen vor ihrem Inhalt. Überschriften müssen eindeutige,
stabile Anker erzeugen; Änderungen können bestehende Deep-Links brechen.

Andere Artikel relativ verlinken, etwa
`scheduler.md#entityscheduler`; eigene Abschnitte mit `#anker`. Alle Linkziele und
Anker prüfen. Websiteinterne Routen nicht erraten. Für Quellcode oder datierte
Repository-Berichte öffentliche GitHub-Links verwenden, nicht lokale Rechnerpfade
oder relative Ausflüge außerhalb von `docs/api`. Keine private Bilddatei wird
mitgeliefert; nur tatsächlich öffentliche HTTP(S)-Bildquellen verwenden.

Jeden Codeblock mit Sprachkennung versehen, z. B. `java`, `kotlin`, `yaml`,
`powershell`, `bash` oder `text`. Signaturübersichten und unvollständige Ausschnitte
als solche erklären; vorausgesetzte Variablen und den Aufrufkontext nennen.
Vollständige Java-Beispielklassen erhalten direkt vor dem Block die Markierung
`<!-- compile: Klassenname -->`. Der Name muss der öffentlichen Klasse entsprechen.
Der Prüfhelfer extrahiert den Block unverändert: keine zweite Beispielkopie pflegen.

Für jede öffentliche Erweiterung Zweck, Signatur, Parameter, Rückgabe/Ergebnisse,
Fehler, Lifecycle und erlaubten Thread beschreiben. Wo relevant: Disable,
Retirement, Regions-/Dimensionswechsel, Cancellation, asynchrone Folgearbeit und
Teilfehler zeigen. Keine Tickthreads blockieren und keinen Future-Abschluss als
impliziten Ownership-Nachweis behandeln. Kompilierung belegt Signaturen, keine
Thread-Sicherheit oder erfolgreiche Server-/Pluginintegration.

## Bestehende Links erhalten

Bei Aufteilung alter Referenzen die alten Dateien als kurze Hinweisseiten mit
Links zu den neuen Artikeln erhalten. Bestehende Abschnittsanker möglichst
beibehalten. Keine vollständigen widersprüchlichen Kopien parallel warten.
README und aktive Entwicklerhinweise auf die kanonischen Artikel umstellen;
historische Testberichte als datierte Nachweise kennzeichnen, nicht umschreiben.
Release-Dateien unter `docs/builds` und Changelogs haben ihren eigenen Workflow.

## Prüfungen

Vom Repository-Root, mit Node.js 22 oder neuer:

```bash
node --test scripts/validate-api-docs.test.mjs
node scripts/validate-api-docs.mjs
```

Metadaten, Format, Namen, Grenzen, relative Artikel-/Ankerlinks und einfache
Credential-Muster werden geprüft. Der Testlauf prüft auch negative Beispiele.
Ein Secret-Muster-Check ersetzt keine Inhaltsprüfung: keine Tokens, Zugangsdaten,
privaten URLs, Nutzer-IP-Adressen oder ausschließlich intern bestimmten Inhalte
in diesen öffentlichen Ordner aufnehmen. HTTP(S)-Ziele inhaltlich prüfen;
der lokale Validator ruft keine externen Webseiten ab.

Auf einem bereits gepatchten Checkout mit JDK 25:

```powershell
.\gradlew.bat -I scripts/check-api-examples.init.gradle.kts :folia-api:checkApiDocumentationExamples --no-configuration-cache --console=plain
```

Linux/macOS verwenden `./gradlew`. Das reine Dokumentations-Init-Script ergänzt
temporär einen Prüftask, baut die aktuelle API-JAR und kompiliert alle markierten
Klassen mit deren echter Compile-Classpath. Es verändert keine Server-Patches.
Ausgaben liegen unter `build/docs-api-examples/`. In einem frischen Checkout zuerst
den normalen Patch-/Buildworkflow vorbereiten. CI führt die Dokumentationsprüfungen
und nach `buildTessera` die Beispielkompilierung aus.

Zusätzlich Signaturen, Statuslisten und Threadannahmen manuell mit API,
Implementierung und Sinopia-Basis abgleichen. Der initiale Umfang und seine
Prüfgrenzen stehen im [Migrationsnachweis](../api-reference-validation.md).
