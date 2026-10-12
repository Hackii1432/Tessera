# Native Nachweise für Tessera 26.3-019-beta

Eigene isolierte Tests vom 11.10.2026, keine produktiven Welten und keine
MCC-/MVE-Produktänderungen. Alle JSON-Dateien sind unveränderte Kopien der
tatsächlichen Runner-Ergebnisse. Der vollständige
[Buildbericht](../../BUILD-26.3-019.md) nennt Quell-/Patchstände, Grenzen und Befehle.

Finale JAR: `tessera-server-26.3.build.019-beta.jar`, 56.595.256 Bytes.
SHA-256: `38a469ba075dd4fc9d36293b71aaa5d5ae077d27062d33da24994ad505d77cea`.
Alle sieben Runner, insgesamt neun Serverstarts, bestehen mit regulärem Stop,
Exitcode 0 und ohne Timeout. Absichtlich injizierte Fehler beziehungsweise
beabsichtigte Disconnects werden nicht als unerwartete Erfolgsfälle gezählt.

- [Paper-/Moonrise-Update](paper-october-update.json), [55 Assertions](paper-october-update-checks.txt): zwei reale Regionen, kontrollierte native Noise-/Biome-/Palettenvergleiche, PDC/Waffen/Physik, Cube-Callback-Ownership, globaler Rezeptkatalog und Client-Resets bei Freeze/Dimensionswechsel. Zwei Logins, keine Kicks/Disconnects vor PASS; je fünf echte Rezeptpakete/RecipeBook-Resets.
- [October-Regressionslauf](paper-october.json): 12 Assertions; Shelf-/Bundle-Disk- und Netzwerkdaten, Unload/Reload, Snapshots, Todesattribution und Passagiere. Zwei verbundene Protokollclients.
- [Öffentlicher nativer Spieler-Restore](native-player-restore.json), [Prüffälle](native-player-restore-checks.txt): Vertrag 1, wiederholtes Save/Load, Rollback, echte Writer-/Publikationsfehler und erhaltene Generatorbindungen. Pro Client ein Login, keine Kicks/Disconnects, acht Stats- und zwölf Advancement-Resets.
- [Disconnect-Lifecycle](disconnect-lifecycle.json): abgebrochener/angenommener Kick, verzögerter echter Netty-Write, Play-/Configuration-Keepalive und normaler Quit. Exakt beabsichtigte Ereignisse, kein wiederholter Timeout-Kick, abgeschlossenes Cleanup bei Freeze.
- [Pregen samt echtem Neustart](native-pregeneration.json), [Erstlauf](native-pregeneration-checks.txt), [Recovery](native-pregeneration-recovery-checks.txt): Dimensions-/Regionsjobs, Ticket-Retirement bei Freeze, Status/400%-TPS-Kapazität, Snapshot/Unload und Wiederaufnahme nach Save-/Checkpoint-Fehlern. Zwei Logins und kein Kick/Quit vor PASS pro Phase.
- Endportal-Dupe [aus](end-portal-dupe-false.json) und [an](end-portal-dupe-true.json): je 46 Fälle einschließlich Drachenei-Ausschluss, Veto/Umleitung, Entfernung und Rückreise. [Flug/Buttons/Hopper](end-portal-flight.json): 20 Fälle. Insgesamt 112 bestanden.

Die vier `TEST-*.xml`-Dateien enthalten die 22 neuen Java-Portierungstests im
unveränderten JUnit-Format. Die Gesamt-Suiten umfassen 10.901 Fälle mit 89
regulären Skips, also 10.812 ausgeführte bestandene Fälle und keine Failures/
Errors. Zusätzlich 57 Validator-Tests und 14 separat kompilierte API-Beispiele.

Diese Protokollclients rendern keine Vanilla-Oberfläche. Kontrollierte
Terrainvergleiche sind keine pauschale vollständige Vanilla-Worldgen-Parität
und kein Durchsatz-Benchmark. Der gemeinsame produktive MCC-/MVE-/SMP-
Pluginstack ist nicht hiermit abgenommen. In JSON genannte absolute Pfade sind
die ursprünglichen lokalen Fixture-Pfade; nach Archivierung dürfen ihre
Wegwerfwelten entfernt werden. Assertions und Runner-Ergebnisse bleiben erhalten.
