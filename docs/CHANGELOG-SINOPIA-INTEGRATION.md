# Tessera – Integration der Sinopia-Basis

## Neu

- Sinopia ist Tesseras eigene, Paper-abgeleitete Basis im selben Repository.
- Ein zentraler Gradle-Einstieg bereitet die Basis vor, wendet die vorhandenen
  Patchschichten an und startet Tests sowie den Tessera-Build.
- Eigene Sinopia-Minecraft-Änderungen können über einen getrennten generierten
  Arbeitsbereich bearbeitet, zu Patches zurückgeschrieben und in die
  versionierte Basis übernommen werden.
- Schutzprüfungen verhindern das stille Zurücksetzen bearbeiteter
  Arbeitskopien und das Überschreiben zwischenzeitlich geänderter Basisdateien.

## Geändert

- Der Build verwendet lokale Sinopia-Quellen statt eines automatischen
  Paper-GitHub-Checkouts. `paperRef` dokumentiert künftig nur die Herkunft.
- Das Branding der integrierten Basis lautet Sinopia; das fertige Produkt
  bleibt Tessera. Bestehende Tessera-Branding-Patches wurden angepasst.
- Die CI verwendet den zentralen Build-Einstieg und prüft zusätzlich die neue
  Build-Unterstützung.

## Unverändert

- Bestehende Folia-/Tessera-Patchschichten und Gameplay-Funktionen.
- Paper-/Bukkit-Paketnamen, interne Kompatibilitätspfade und gespeicherte Daten.
- Herkunfts-, Autoren- und Lizenzhinweise.

## Versionshinweis

Dies ist ein Infrastruktur-Update, keine Freigabe für Minecraft 26.3.
Der damalige Import enthielt noch unportierte Upstream-Features. Die inzwischen
erfolgte Portierung und deren Prüfgrenzen sind separat dokumentiert:
[Portierung 26.3-rc-2](PORTIERUNG-26.3-rc-2.md).

Anleitung: [Sinopia-Workflow](SINOPIA-WORKFLOW.md).
Prüfergebnisse: [Integrationsprüfung](SINOPIA-INTEGRATION-VALIDATION.md).
