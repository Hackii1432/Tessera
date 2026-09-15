# Sinopia – Tesseras integrierte Paper-Basis

Dieses Verzeichnis ist versionierter Quellcode im Tessera-Repository, kein
Submodule und kein separat zu pflegendes Projekt. Der zentrale Einstieg ist
der Gradle-Wrapper im Tessera-Hauptverzeichnis.

Importiert aus PaperMC/Paper, Commit
`38b0bfeb67855206ede9cb1df4f3354c4611c4c2` (Minecraft 26.3-rc-2).
Ursprüngliche Lizenzen und Autorenhinweise bleiben erhalten. Dekompilierter
Minecraft-Code ist nicht enthalten; Änderungen daran bleiben Patches.

Lokale Integrationsänderungen:

- Name der Basis: Sinopia. Tessera bleibt das finale Serverprodukt.
- Papers historische `oldPaperCommit`-Updatehilfe ist deaktiviert: deren
  Commit existiert nicht im generierten lokalen Git-Snapshot.
- Die 34 ursprünglich zurückgestellten Minecraft-Feature-Patches sind auf
  26.3-rc-2 portiert und unter `paper-server/patches/features/` aktiviert.
  Patch 0035 ergänzt die 26.3-Datenmigration und Quellkompatibilität.
  Patch 0036 korrigiert die rekursive Schlaf-Aufrufkette bei normalen Betten und
  Strohbetten. `CraftHumanEntity#sleep` reicht `force` wieder weiter; zwölf
  Regressionstestfälle sichern diese Korrekturen ab (13. September 2026).
  Patch 0037 korrigiert Gamerule-Befehlsrückmeldungen nach erfolgreicher Änderung
  und berücksichtigt Plugin-Abbrüche sowie angepasste Werte. Elf Regressionstests
  prüfen Befehlsstatus, Rückgabewert und Rückmeldung (13. September 2026).
  Patch 0038 ergänzt die Bukkit-Zuordnung der drei Vanilla-Poplar-Farben für
  natürliches Wachstum und Knochenmehl. API und CraftRegionAccessor unterstützen
  dieselben Typen; Tesseras Folia-Basispatch hält die Zuordnung threadlokal.
  22 Regressionstestfälle prüfen Zuordnung, Platzierungspfad, Farbauswahl und
  Thread-Isolation (13. September 2026).
  Dies ist keine Produktionsfreigabe; Prüfstand: `../docs/PORTIERUNG-26.3-rc-2.md`.
- Technische Paper-/Bukkit-Paketnamen und interne Modulpfade bleiben erhalten.

Normale API-/Serverdateien können direkt hier bearbeitet werden. Für Minecraft
den generierten Sinopia-Arbeitsbereich verwenden, siehe
`../docs/SINOPIA-WORKFLOW.md`.

Künftige Upstream-Änderungen mit den lokalen Änderungen zusammenführen und
den Herkunftscommit hier sowie im root-`paperRef` dokumentieren. `paperRef`
ist kein automatischer Download-/Updatebefehl mehr.

## Selektive Paper-Backports vom 14. September 2026

Der Importstand bleibt `38b0bfeb67855206ede9cb1df4f3354c4611c4c2`.
Aus `dev/26.3` wurden gezielt Korrekturen bis
`e285a336a6bc7ccdec8803d528c67c80d431760a` übernommen, kein vollständiger Rebase.
API-/Adapteränderungen liegen direkt in Sinopia; Minecraft-Patch 0039 enthält
die Laufzeitkorrekturen für Braustand, Zeitbefehl, Notenblock, Bett-Explosion,
Struktur-Seed, NBT, Blocktransformation und Schild-Zeichenlimit.
Die Folia-spezifische Abbruch-Zuständigkeit liegt in Tesseras Minecraft-Patch
0037. Der vorhandene Schildplatzierungs-Patch 0036 bleibt erhalten.

Original-Commits, bewusst ausgelassene Änderungen, Tests und Build-Anleitung:
[Paper-Backports](../docs/PAPER-BACKPORTS-2026-09-14.md).

## Minecraft 26.3-rc-3

Mit Build 001 wurde die lokale Minecraft-Basis auf `26.3-rc-3` umgestellt, mit
`io.papermc:mache:26.3-rc-3+build.1` aus dem offiziellen Mache-Release.
Mache-Herkunft: `88f922b9326db08bb96c6ca7aef80a92a9cc83ef`.
Die Paper-Importreferenz und die oben beschriebenen selektiven Backports bleiben
erhalten; es wurde kein neuer Paper-Gesamtstand importiert.

Alle vorhandenen Minecraft-Patches ließen sich unverändert auf RC3 anwenden.
Der Vergleich der vollständigen erzeugten Tessera-Minecraft-Quellen mit RC2
zeigt ausschließlich die erwarteten Änderungen in `DetectedVersion` und
`SharedConstants`: Datenversion `5022`, Snapshot-Protokoll `338` beziehungsweise
`1073742162` auf der Verbindung. Die Minecraft-Versionsressource wurde ebenfalls
aus der offiziellen RC3-Basis übernommen. Die Plugin-API-Version bleibt `26.3`.

`MinecraftRc3VersionTest` prüft die tatsächlichen Laufzeit-Versionsdaten und
Minecraft-Konstanten, nicht nur den Namen des Build-Artefakts.

## Minecraft 26.3 Release – Build 005

Die aktuelle lokale Minecraft-Basis ist das offizielle Release `26.3` mit
`io.papermc:mache:26.3+build.1`. Mache-Herkunft ist Commit
`56bbc351c83dd071f69a1a0f79c3ccfad8ca618e` im Branch `release/26.3`.
Die Datenversion ist `5023`, das Release-Netzwerkprotokoll `777`.
Ressourcenpakete verwenden Format `97.1`, Datenpakete Format `121.0`.

Root- und Sinopia-Version sind gemeinsam umgestellt; der zugehörige
Tessera-Gradle-Patch verweist auf denselben Mache-Build. `paperRef` bleibt die
ursprüngliche Importreferenz. Es wurde kein vollständiger Paper-Rebase vorgenommen.
Die bisherigen selektiven Backports und Folia-/Tessera-Patches bleiben erhalten.

Der bisherige RC3-Test wurde durch `MinecraftReleaseVersionTest` ersetzt.
Er prüft Versionskennung, Datenversion, Versionsserie, Release-Protokoll,
Paketformate und den Stable-/Snapshot-Status der tatsächlichen Laufzeitdaten.
Java 25, Plugin-API-Version `26.3` und Gradle `9.4.1` bleiben unverändert.
Tessera bleibt trotz der veröffentlichten Minecraft-Basis im Alpha-Kanal.

Änderungen und abschließender Prüfstand:
[Build-005-Changelog](../docs/CHANGELOG-26.3-005-RELEASE.md).
