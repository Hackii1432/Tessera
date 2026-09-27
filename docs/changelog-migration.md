# Changelog-Migration für den Website-Import

Stand: 27.09.2026. Diese Änderung betrifft Dokumentation und deren Prüfung,
keine neue Tessera-Version. Server-Build `010-alpha` und Minecraft/API `26.3`
bleiben unverändert. Keine Website-Dateien, Tokens, Commits oder Pushes gehören
zu dieser Migration.

## Umfang und Herkunft

Ausgangsstand: `713f60c` auf `ver/26.3.x`, Repository
`https://github.com/Hackii1432/Tessera`. Die vollständige Ausgangsrevision,
SHA-256 der Quelldateien, ursprüngliches Frontmatter und Zielzuordnungen stehen
in [changelog-migration.json](changelog-migration.json).
Die SHA-256-Werte beziehen sich auf die Quelldateien im ursprünglichen lokalen
Checkout einschließlich ihrer damaligen Zeilenenden.

Erfasst wurden **19 bestehende Changelog-Dateien**. Davon wurden **18 Quellen
in 13 Release-Dateien** mit vollständigen Metadaten zusammengeführt. Ein weiterer
historischer Nachtrag bleibt wegen ungeklärter Pflichtangaben erhalten, siehe unten.
Die Suche umfasste versionierte Markdown-Dateien, Release-/Changelog-Namen,
Frontmatter, Git-Historie sowie Workflow-/Build-Anweisungen. Technische Berichte
wurden nicht zu neuen Releases erklärt.

| Historischer Eintrag | Neue Ablage |
| --- | --- |
| 26.2: Vanilla-Entity-Selektoren, 0.1.3, 26.08.2026 | [26.2/0.1.3](builds/26.2/0.1.3.md) |
| 26.2: Entities, Redstone und Game Events, 0.1.4, 26.08.2026 | [26.2/0.1.4](builds/26.2/0.1.4.md) |
| 26.2: Vanilla-Blockbefehle, 0.1.5, 28.08.2026 | [26.2/0.1.5](builds/26.2/0.1.5.md) |
| 26.2: Runtime-Strukturen, 0.1.6, 03.09.2026 | [26.2/0.1.6](builds/26.2/0.1.6.md) |
| 26.3: Build 001 RC2/RC3 und zugehörige Zwischenstände | [26.3/0.0.1](builds/26.3/0.0.1.md) |
| 26.3: Build 003, 14.09.2026 | [26.3/0.0.3](builds/26.3/0.0.3.md) |
| 26.3: Build 004, 15.09.2026 | [26.3/0.0.4](builds/26.3/0.0.4.md) |
| 26.3: Build 005, 15.09.2026 | [26.3/0.0.5](builds/26.3/0.0.5.md) |
| 26.3: Build 006, 21.09.2026 | [26.3/0.0.6](builds/26.3/0.0.6.md) |
| 26.3: Build 007, 22.09.2026 | [26.3/0.0.7](builds/26.3/0.0.7.md) |
| 26.3: Build 008, 25.09.2026 | [26.3/0.0.8](builds/26.3/0.0.8.md) |
| 26.3: Build 009, 26.09.2026 | [26.3/0.0.9](builds/26.3/0.0.9.md) |
| 26.3: Build 010, 27.09.2026 | [26.3/0.0.10](builds/26.3/0.0.10.md) |

## Zusammenführungen und Erhalt historischer Aussagen

- RC2 und RC3 verwendeten bereits dasselbe Versionspaar `26.3` / `0.0.1`
  und dasselbe Veröffentlichungsdatum `2026-09-14`. Ein gemeinsamer Eintrag
  vermeidet eine doppelte Release-Identität. Titel und Beschreibung benennen
  jetzt beide Phasen; konkrete MC-/Protokollversionen und JAR-Namen bleiben erhalten.
- Bett-, Gamerule- und Poplar-Fixes wurden ausdrücklich unter unveränderter
  Buildnummer `001-alpha` dokumentiert. Ihre Zusatzdetails, Tests, Grenzen und
  unterschiedlichen damaligen JAR-Prüfsummen bleiben als datierte Unterabschnitte
  im Eintrag 0.0.1 erhalten. Sie sind keine Prüfsummen der späteren RC3-JAR.
- Die Sinopia-Infrastrukturnotiz ist durch Commit `2f44fa6` und den ursprünglichen
  Build-001-Changelog derselben Auslieferung zugeordnet. Ihr historischer Hinweis
  auf eine damals noch nicht abgeschlossene Portierung bleibt ausdrücklich
  historisch gekennzeichnet. Es gibt keine separate Sinopia-Release-Historie.
- Die Beschreibung von 0.0.8 nannte „Build 008 & 007“, ihr Versionsfeld und Inhalt
  dokumentieren aber Build 008; Build 007 besitzt bereits den eigenen TPS-Eintrag
  vom 22.09.2026. Die Beschreibung wurde auf Build 008 präzisiert, nicht künstlich
  in weitere Releases aufgeteilt. Der Originalwert steht im JSON-Nachweis.
- Versionen, Minecraft-Gruppen, Veröffentlichungsdaten, Status, Tags und
  Breaking-Angaben bestehender Frontmatter bleiben erhalten. Textabschnitte
  wurden unter die drei Importkategorien und erforderliche Updatehinweise geordnet.
- Die alten optionalen URLs zeigten auf die Repository-Startseite und
  `home.mosaikdev.com`, nicht auf belegte versionsspezifische Release-/JAR-Ziele.
  Diese Felder wurden weggelassen; ihre ursprünglichen Werte bleiben im
  JSON-Nachweis. Es wurden keine Download- oder GitHub-Release-Links erfunden.
- Historische lokale Buildpfade bleiben als damalige Nachweise im Text stehen.
  Sie sind keine Zusage, dass bereinigte Build-Artefakte noch verfügbar sind.
- Die 18 migrierten Quelldateien wurden erst nach erfolgreicher Prüfung von
  **590 nichtleeren Inhaltszeilen** und den ursprünglichen Versions-, Datums-,
  Status- und Breaking-Werten entfernt. Der Vergleich berücksichtigt geänderte
  Überschriften und korrigierte Links; Aussagen und Prüfergebnisse bleiben erhalten.
  Die alten Dateien bleiben über die aufgezeichnete Git-Revision wiederherstellbar.

## Offene historische Pflichtangaben

[Level-Root-Nachtrag zu 26.2-017](CHANGELOG-26.2-017-LEVEL-ROOT.md) enthält kein
Frontmatter. Verlässlich belegt sind Minecraft `26.2`, Build `017`, Kanal `stable`
in Commit `1a592ab` sowie Tests vom **10.09.2026**. Die Changelog-Datei wurde erst
mit `ffd422d` am **13.09.2026** in Git aufgenommen. Weder das Testdatum noch das
Commitdatum beweist das Veröffentlichungsdatum dieses Nachtrags.

Die Website-Versionsnummer `0.1.7` ist nach den Vorgängern plausibel, aber im
vorliegenden Nachtrag und der geprüften Historie nicht ausdrücklich belegt.
**Release-Version und Veröffentlichungsdatum müssen bestätigt werden.** Bis dahin
bleibt das Original unverändert außerhalb des Importordners erhalten. Es wird
nicht stillschweigend weggelassen, gelöscht oder mit erfundenen Werten importiert.
Danach in den bestätigten Versionspfad übernehmen, Links korrigieren und erneut prüfen.

Der ausführliche Bericht [Tessera-Update 26.2-017](TESSERA-UPDATE-26.2-017.md),
die Online-/Level-Root-Prüfberichte, `BUILD-26.3-010.md`, Backport-Analysen,
Sinopia-Validierung, API- und Architekturtexte bleiben technische Dokumentation.
Für bloß erwähnte Builds wie 002 oder 018 wird kein Release erfunden.

## Prüfung und laufende Pflege

```text
node --test scripts/validate-changelogs.test.mjs
node scripts/validate-changelogs.mjs
```

Die 22 Validator-Tests prüfen gültige Dokumente und gezielt ungültige Datentypen,
fehlende/duplizierte Felder, Datum, URLs, Pfade, Verschachtelung, Importlimits sowie
lokale Links und Überschriftenanker. Der eigentliche Bestandslauf prüft alle
Release-Dateien. Die aktualisierten Verweise in README, Portierungsbericht,
Backport-Bericht, Sinopia-Workflow und Baseline werden zusätzlich kontrolliert.
Jede Überschreitung der 200-Dateien-/512-KiB-/8-MiB-/30-Ordner-Grenzen ist ein Fehler,
nicht Anlass zum Entfernen von Historie. Externe HTTP-Ziele werden nicht live geprüft.

Ergebnis des finalen lokalen Prüflaufs:

| Prüfung | Ergebnis |
| --- | --- |
| Validator-Testfälle | 22 bestanden, 0 Fehler, 0 übersprungen |
| Release-Dateien / Minecraft-Ordner | 13 / 2; Grenzen 200 / 30 eingehalten |
| Gesamte Importquelle einschließlich Pflege-README | 83.319 Bytes; Grenze 8 MiB eingehalten |
| Größte Release-Datei (0.0.1) | 34.616 Bytes; Grenze 512 KiB eingehalten |
| Lokale Links/Anker in Releases und betroffenen Dokumenten | 79 geprüft, keine defekten Ziele |
| Migrierte Quellen | 18; 590 Inhaltszeilen ohne Textverlust übertragen |
| Versions-/Datumswerte und Pfadzuordnung | Erfolgreich geprüft |
| `git diff --check` | Erfolgreich |

Die Bytezahlen beziehen sich auf die UTF-8-Dateien mit LF-Zeilenenden dieses
Prüflaufs. Git kann auf Windows CRLF auschecken; der Validator zählt die dann
tatsächlich vorliegenden Bytes. Es wurde kein historisches Release wegen der
Importgrenzen weggelassen.

README und Projektbeschreibung erklären nun Sinopia, Regionsmodell, Installation,
Plugin-Anforderungen, Migration und tatsächliche Grenzen von Build 010. Der
veraltete Vertrag-0-Hinweis im aktuellen Sinopia-Workflow wurde anhand des nativen
Serverpatches und der finalen Build-Nachweise auf Vertrag 1 mit offener gemeinsamer
MCC-Integration korrigiert. Historische Prüfberichte wurden nicht umgeschrieben.

Es gab keinen vorhandenen Changelog-Generator. Pflegeanleitung und Formatvorlage
liegen in [docs/builds/README.md](builds/README.md); der bestehende Build-Workflow
führt den Validator samt Tests aus. Für diese Dokumentationsänderung werden
keine Server-/Clienttests oder neue JAR-Prüfungen behauptet.

## GitHub-Quelle für die Website

```yaml
githubChangelogUrl: "https://github.com/Hackii1432/Tessera/tree/ver/26.3.x/docs/builds"
```

Der Ordner wird auf GitHub erst nach einem ausdrücklich gewünschten Commit/Push
dieser lokalen Änderungen verfügbar. Der Eintrag im Website-Header von
`src/data/tessera.md` wird nicht in diesem Repository vorgenommen.
