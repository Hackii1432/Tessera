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
