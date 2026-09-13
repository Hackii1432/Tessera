# Tessera 26.3-rc-2 – Sinopia-Aufnahme und Bett-Korrektur

> Neuerer Folgefix bei gleicher Buildnummer: [Gamerule-Rückmeldungen](CHANGELOG-26.3-001-GAMERULES.md).
> Die unten stehende Prüfsumme dokumentiert ausschließlich den damaligen Bett-Fix-Build.

Stand: 13. September 2026. Die lokalen Versionswerte bleiben `001` / `alpha`.

## Änderungen

- Sinopia wird zusammen mit der benötigten Build-Unterstützung in Tesseras
  Versionsverwaltung aufgenommen. Kein Submodule und kein zweites Projekt:
  `sinopia/` enthält die Paper-abgeleitete Basis, `buildSrc/` deren lokale
  Snapshot-/Patch-Integration. Originale Lizenz- und Autorenhinweise bleiben erhalten.
- Eine rekursive Aufrufkette beim Schlafen wird korrigiert. `ServerPlayer`
  rief die vierparametrige Basismethode auf, die wieder zur überschriebenen
  fünfparametrigen Methode zurücksprang. Das konnte beim Benutzen von Strohbetten
  ebenso wie bei normalen Betten zum Stackoverflow führen.
- Die Minecraft-Korrektur liegt als eigener Sinopia-Feature-Patch 0036 vor.
  Der direkte Basisklassen-Aufruf übergibt jetzt auch `force` und erreicht die
  eigentliche Schlaf-Implementierung ohne erneutes Auslösen des Bett-Ereignisses.
- `CraftHumanEntity#sleep(Location, boolean)` reicht den übergebenen `force`-Wert
  wieder weiter. Diese normale Serverquelldatei wird direkt in Sinopia versioniert.
- Vanilla-Bettregeln, Strohbett-Statistik, Spawnpunktbedingungen und das
  Strohbett-Verhalten beim Aufstehen werden nicht geändert. Plugin-Abbrüche sowie
  die nicht übersteuerbare Ablehnung für tote Spieler bleiben erhalten.
- Zwölf Regressionstestfälle prüfen die Aufrufkette mit beiden Bettarten,
  `force`, Plugin-Abbrüche, tote Spieler, abgelehntes Schlafen und Bukkit-Aufrufe.

## Prüfung

Der ursprüngliche Stand scheiterte erwartungsgemäß in 9 der 12 neuen Testfälle.
Nach der Korrektur bestehen alle 12 Fälle, ohne übersprungene Bett-Tests.

| Prüfung | Ergebnis |
| --- | --- |
| Sinopia-Build-/Git-Integration | 22 Prüfungen bestanden |
| Erneute Anwendung aller Patchschichten | Erfolgreich; 36 Sinopia-Minecraft-, 32 Tessera-Minecraft-, 23 Implementierungs- und 12 API-Feature-Patches |
| API-Testberichte | 529 erfasst, 0 Fehler, 2 übersprungen |
| Server-Testberichte, einschließlich der 12 Bett-Tests | 9.756 erfasst, 0 Fehler, 87 übersprungen |
| Checkstyle-Modultests | 3 erfasst, 0 Fehler |
| `buildTessera` | Erfolgreich, 11 Minuten 39 Sekunden |
| Neue JAR mit JDK 25.0.3 und `--version` | Exit 0; `26.3-rc-2-001-0b66675` |

Insgesamt 10.288 erfasste Tests ohne Fehler, davon 89 übersprungen. Unveränderte
Teilaufgaben durften Gradles gültige Up-to-date-Ergebnisse verwenden; die
Server-Tests einschließlich der neuen Bett-Regressionen wurden neu ausgeführt.
Der Launcher-Test startet ausdrücklich keine Spielwelt.

Der eigene Minecraft-Patch wurde aus einem technischen Feature-Commit im
generierten Sinopia-Arbeitsbereich exportiert. Fünf nebenbei vom Rebuild
umgeschriebene ältere Patches wurden nicht übernommen; der versionierte Export
enthält ausschließlich den neuen Patch 0036. Der temporäre Arbeitsbereich
bleibt unter `build/sinopia-workspace-bed-fix-2026-09-13/` erhalten.

Lokale Nachweise: `build/bed-fix-regression-before.log`,
`build/bed-fix-build-support-tests.log`, `build/bed-fix-tessera-build.log` und
`build/port-26.3-launcher-check/launcher-bed-fix-java25-version.log`.

Die Tests isolieren Weltzugriff und Plugin-Dispatcher mit Test-Doubles. Sie
ersetzen keinen Ingame-Test mit echten Spielern und den eingesetzten Plugins.
Vor einem Produktiveinsatz auf einer separaten Testwelt prüfen:

1. Stroh- und normales Bett nachts benutzen und wieder verlassen.
2. Strohbett-Statistik und Vanilla-Verhalten beim Verlassen überprüfen.
3. Normales Bett einschließlich Spawnpunkt und Schlafen bei Tageslicht prüfen.
4. Falls ein Plugin Schlafereignisse verarbeitet: Abbruch sowie `sleep(..., true)`
   testen; die Konsole darf keine rekursive Bett-Fehlermeldung mehr enthalten.

## Anwenden und bauen

Im Tessera-Hauptverzeichnis mit JDK 25:

```powershell
.\gradlew.bat buildTessera
```

Dieser Task übernimmt die lokale Sinopia-Basis, wendet sämtliche Patchschichten
an und führt Tests und Build aus. Es ist kein manuelles `git apply` für Patch
0036 nötig. Änderungen in generierten Minecraft-Quellen vorher als Patches sichern.

Bei bereits angewendeten Patches funktioniert auch der normale `build`-Task.
Nach einem Wechsel der Basis oder Änderungen an Patchdateien zuerst
`applyAllPatches` ausführen; `buildTessera` erledigt beides zusammen.

Erfolgreich gebautes finales Artefakt bei den aktuellen Versionswerten:
`build/libs/tessera-server-26.3-rc-2.build.001-alpha.jar`.

Größe: 66.414.962 Bytes. SHA-256:
`9586a686e2336c5d7740dd0914ab27076892f65fc8230e78651cc4d6663b2d94`.

Sinopia, Build-Unterstützung und die passenden Tessera-Patches gehören in
denselben Commit. Generierte Quellen, `build/` und Caches gehören nicht hinein.
Die zugehörigen Dateien sind für den nächsten Tessera-Commit vorgemerkt.
Es wurde kein Commit im Hauptrepository erstellt und nichts gepusht.
