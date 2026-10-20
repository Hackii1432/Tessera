# Nachweise zu Build 021

Stand: 20.10.2026. Isolierte Windows-Tests mit Java 25, eigenen Wegwerfwelten
und zwei lokalen Protokollclients. Produktive Saves und fremde Pluginprojekte
wurden nicht verändert.

## Negativkontrolle

- [Runner-Ergebnis auf Build 020](navigation-baseline-020.json)
- [Einzelprüfungen](navigation-baseline-020-checks.txt)
- [Server- und Runner-Log](navigation-baseline-020.log)

`passed: true` bedeutet in dieser ausdrücklich als `baseline: true`
gekennzeichneten Kontrolle, dass der alte Fehler wie erwartet reproduziert
wurde. Es bedeutet **nicht**, dass Build 020 den Fehler bereits behoben hätte.
Ein gerittenes Pferd mit Sattel und Rüstung wird durch den echten
Entity-Fehlerschutz nach dem fremden synchronen Chunk-Zugriff entfernt.
Der Test installiert zuerst einen gültigen Pfad und ersetzt anschließend einen
Knoten durch die im Fehlerbericht enthaltenen entfernten Koordinaten.

## Java-Regressionstests

[Gezieltes JUnit-Ergebnis](TEST-NavigationRegionSafety-targeted.xml):
18 Fälle, keine Failures, Errors oder übersprungenen Tests. Die Testquellen
sind im [eigenen Server-Patch](../../../folia-server/paper-patches/features/0061-Test-navigation-ownership-and-no-load-guarantees.patch)
gesichert.

## Finale JAR

- [Runner-Ergebnis](navigation-final-021.json)
- [107 Einzelprüfungen](navigation-final-021-checks.txt)
- [Server- und Runner-Log](navigation-final-021.log)
- [Wiederholung mit frischen Testwelten](navigation-repeat-021.json)
- [Einzelprüfungen der Wiederholung](navigation-repeat-021-checks.txt)
- [Log der Wiederholung](navigation-repeat-021.log)
- [Vollständiger Build](buildTessera.log)
- [Build-, Quell- und Artefaktzusammenfassung](build-summary.json)
- [18 neue Fälle im vollständigen JUnit-Lauf](TEST-NavigationRegionSafety-full-build.xml)

Die finale JAR hat den SHA-256
`7ab2677fd3179a13edf57a21001526de6cc1241ce30e414c2d64ad219b1f1f91`.
Pferde in zwei getrennten Regionen bleiben nach veralteten geladenen und
ungeladenen Pfaden, gerittenem Regionswechsel und separatem Dimensionswechsel
erhalten. Beide Läufe bestehen mit je zwei Logins, keinen Kicks oder
Disconnects vor PASS und normalem Shutdown mit Exitcode 0. Die wiederholten
Identitäts- und Ausrüstungsprüfungen zählen zu den jeweils 107 Einzelprüfungen,
nicht als jeweils eigenständiges Szenario.

Ein [vorheriger Start ohne lokale Netzwerkfreigabe](navigation-sandbox-blocked.json)
ist nicht bestanden. Das [Log](navigation-sandbox-blocked.log) zeigt
`Unable to establish loopback connection` / `Permission denied: connect`
bereits im Java-HTTP-Selector; kein Client oder Navigationstest wurde
erreicht. Die bestandenen Läufe verwenden dieselbe unveränderte finale JAR.

## Aufräumen der Testumgebungen

Die fünf ausschließlich für diesen Auftrag erzeugten, markierten
Wegwerf-Testserver wurden nach der Sicherung und dem Hashvergleich ihrer
Nachweise entfernt. Das umfasst nur ihre Testwelten, Caches, Bibliotheks-
und JAR-Kopien; die finale JAR, produktive Daten und fremde Teststände
bleiben erhalten. Rund 1,14 GiB temporäre Dateien wurden dadurch entfernt.
Die Testwelten sind nicht als Backup erhalten, können aber über den Runner
neu erzeugt werden. `work` und `fixtureJar` in den JSON-Ergebnissen sind
historische Ausführungspfade, keine weiterhin vorhandenen Downloads.
Die kompakten Entwicklungsnachweise liegen zusätzlich unter
`build/navigation-021-development-evidence/`; die beiden finalen Läufe
und die Negativkontrolle sind hier dauerhaft dokumentiert.

## Aussagegrenzen

Die künstlich veralteten Pfade prüfen den echten Navigations-, Chunk- und
Entity-Fehlerpfad deterministisch. Die ursprüngliche Spielerbewegung, durch
die der gemeldete Pfad entstand, wurde nicht als aufgezeichnete Eingabesequenz
nachgespielt. Die erste native Regression verwendet die originale
`GroundPathNavigation`. Weitere negative Varianten verwenden eine Test-
Unterklasse, die einen verbotenen Boden-Read schon vor dessen Ausführung
mit einer Assertion beendet; gültige Reads werden an Vanilla delegiert.

Ein Test mit gerendertem Vanilla-Client, ein öffentlicher Mehrspieler-
Lastbenchmark und der konkrete produktive Plugin-Stack gehören nicht zu
dieser isolierten Abnahme. Beim Dimensionswechsel wird explizit abgesessen
und anschließend wieder aufgesessen; ein gerittener Cross-Dimension-
Portaltransfer wird damit nicht behauptet.

[Build und Wiederholung](../../BUILD-26.3-021.md)
