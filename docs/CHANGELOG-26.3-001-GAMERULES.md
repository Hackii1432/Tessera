# Tessera 26.3-rc-2 – korrekte Gamerule-Rückmeldungen

> Neuerer Folgefix bei gleicher Buildnummer: [Poplar-Wachstum und Region-Tick-Crash](CHANGELOG-26.3-001-POPLAR.md).
> Die unten stehende Prüfsumme dokumentiert ausschließlich den damaligen Gamerule-Fix-Build.

Stand: 13. September 2026. Versionswerte unverändert: `001-alpha`.

## Behobener Fehler

Beim Ändern einer Gamerule wurde der neue Wert bereits übernommen. Anschließend
verglich der Befehl diesen gespeicherten Wert erneut mit dem Zielwert und meldete
fälschlich „Game rule … is already set to …“. Der Befehl galt deshalb trotz
erfolgreicher Änderung als fehlgeschlagen.

Der eigene Sinopia-Patch
`sinopia/paper-server/patches/features/0037-Fix-gamerule-command-feedback.patch`
vergleicht den wirksamen Wert jetzt mit dem Wert **vor** dem Änderungsversuch.

- Erfolgreiche Änderungen erhalten die normale Vanilla-Erfolgsmeldung.
- Echte Nicht-Änderungen behalten die Meldung „already set“.
- Plugin-Abbrüche erhalten eine eigene Fehlermeldung mit verständlichem
  englischem Fallback, ohne eine erfolgreiche Änderung vorzutäuschen.
- Von Plugins angepasste Werte werden für Speicherung, Rückmeldung und
  numerischen Befehlsrückgabewert verwendet.
- Das erfolgreiche Setzen auf `false` bleibt erfolgreich, auch wenn der
  numerische Rückgabewert wie in Vanilla `0` beträgt.
- Der gemeinsame Bukkit-Eventpfad und die weltbezogene Speicherung bleiben
  unverändert. Plugins dürfen weiterhin auch einen zunächst unveränderten
  Änderungswunsch anpassen. Es wird keine zweite Mutation hinzugefügt.
- Abfragen, Argumentprüfung und bestehende Regions-/Thread-Zuordnung werden
  nicht geändert.

## Prüfung

Elf neue Regressionstestfälle verwenden den tatsächlich registrierten Befehl,
den realen Event-Hilfspfad und echte Gamerule-Speicherung. Plugin-Entscheidungen,
Command-Sender und laufende Server-/Weltobjekte werden durch Test-Doubles ersetzt.

Der vorherige Stand scheiterte erwartungsgemäß in 7 von 11 Fällen. Nach der
Korrektur bestehen alle 11 Fälle; keiner davon wurde übersprungen.

| Testberichte | Erfasst | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| API | 529 | 0 | 2 |
| Server, einschließlich der neuen Gamerule-Tests | 9.767 | 0 | 87 |
| Checkstyle-Modul | 3 | 0 | 0 |
| Gesamt | 10.299 | 0 | 89 |

Die Server-Tests wurden neu ausgeführt. Unveränderte Teilaufgaben durften
Gradles gültige Up-to-date-Ergebnisse verwenden.

Alle Patchschichten einschließlich Sinopia-Patch 0037 wurden erfolgreich neu
angewendet. `buildTessera` war nach 10 Minuten 58 Sekunden erfolgreich.
Die generierten Minecraft- und Server-Arbeitsquellen enthalten keine
ungesicherten Handänderungen.

Der Launcher-Test mit JDK 25.0.3 und `--version` endete mit Exit 0 und
`26.3-rc-2-001-2f44fa6`. Dabei wurde keine Spielwelt gestartet.

Lokale Protokolle: `build/gamerule-regression-before.log` und
`build/gamerule-tessera-build-2.log`. Der erste Build-Versuch in
`build/gamerule-tessera-build.log` brach wegen einer beim Patch-Export fehlenden
Kontextzeile ab; dieser Formatfehler ist korrigiert.
Launcher-Protokoll:
`build/port-26.3-launcher-check/launcher-gamerule-java25-version.log`.

Ein zusätzlicher Ingame-Test auf einer separaten Testwelt bleibt sinnvoll:
`random_tick_speed` ändern, abfragen und denselben Wert noch einmal setzen;
anschließend den ursprünglichen Wert wiederherstellen. Bei eingesetzten
Gamerule-Plugins auch Abbruch und Wertanpassung prüfen.

## Anwenden und bauen

Mit JDK 25 im Tessera-Hauptverzeichnis:

```powershell
.\gradlew.bat buildTessera
```

Der Task übernimmt Sinopia, wendet alle Patchschichten an, testet und baut den
Server. Patch 0037 muss nicht zusätzlich mit `git apply` angewendet werden.
Eigene Änderungen in generierten Minecraft-Quellen vorher in Patches sichern.

Ausgabe bei den unveränderten Versionswerten:
`build/libs/tessera-server-26.3-rc-2.build.001-alpha.jar`.

Neue JAR: 66.414.339 Bytes. SHA-256:
`e7b2974a2d233e1f2a66e4feb04ff494306fe501a0f0f92a3a3926fca5a55158`.
Zum Einsatz die bisherige Server-JAR durch diesen Build ersetzen und den Server
neu starten. Der vorherige Bett-Fix 0036 ist weiterhin enthalten.

Es wurde kein Commit im Tessera-Hauptrepository erstellt und nichts gepusht.
