# Paper-Backports für Tessera / Sinopia – RC3 Build 003

## Herkunft und Umfang

Auf dem bestehenden lokalen RC3-Stand werden ausgewählte Laufzeitkorrekturen
aus diesen Paper-Commits übernommen:

- [09d1b0ab3488038ad3cbbc9447be1fb9a486ccd0](https://github.com/PaperMC/Paper/commit/09d1b0ab3488038ad3cbbc9447be1fb9a486ccd0)
- [2a842951007886e64775c096d30167dfaffea05c](https://github.com/PaperMC/Paper/commit/2a842951007886e64775c096d30167dfaffea05c)

Die Originaländerungen stammen von Lulu13022002. Der Minecraft-Patch bewahrt
die Autorenangabe und dokumentiert die selektive Anpassung durch Tessera.
Die ursprünglichen Paper-/Sinopia-Lizenzen bleiben unverändert.

Dies ist kein vollständiger Rebase auf einen neuen Paper-Stand. `paperRef`
bleibt die Importreferenz `38b0bfeb67855206ede9cb1df4f3354c4611c4c2`;
die zuvor dokumentierten Backports bis `e285a33` bleiben erhalten.
Minecraft bleibt `26.3-rc-3`, Mache `26.3-rc-3+build.1` und die API `26.3`.
Die Tessera-Buildnummer steigt von `002` auf `003`.

## Versionierte Dateien

| Datei | Änderung |
| --- | --- |
| `sinopia/paper-server/patches/features/0040-Backport-Paper-RC3-entity-and-player-corrections.patch` | Cushion-Typprüfung, Break-Event-Ursachen, Karten-Drops und Kick-Grund. |
| `sinopia/paper-server/src/main/java/org/bukkit/craftbukkit/CraftRegionAccessor.java` | Entfernt die pauschale Peaceful-Sperre nach der weiterhin geltenden Feature-Prüfung. |
| `sinopia/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEntityTypes.java` | Verwendet für explizite Plugin-Erzeugung `EntitySpawnRequest(COMMAND, true)`. |
| `sinopia/paper-server/src/main/java/org/bukkit/craftbukkit/event/CraftEventFactory.java` | Entfernt das zweite Spawn-Event für Erfahrungsorbs. |
| `sinopia/paper-server/src/test/java/io/papermc/paper/porting/PaperRc3EntityBackportTest.java` | Regressionstests gegen die tatsächlichen Laufzeitmethoden. |

Wie bei den bisherigen Backports werden Minecraft-Quellen als Feature-Patch
und CraftBukkit-Implementierungsquellen direkt in der versionierten Sinopia-Basis
gepflegt. Erzeugte Quellen unter `paper-server/` und `folia-server/src/minecraft/`
sind keine zusätzlichen Wartungsstellen und gehören nicht in den Root-Commit.

## Verhalten und Regionssicherheit

- Cushions lösen bei Bewegung kein für sie ungeeignetes Hanging-Event aus.
  Der bestehende Kill-/Drop-Pfad bleibt erhalten; für tatsächliche Hanging-Entities
  bleibt das Event inklusive Abbruch- und Entfernt-Prüfung bestehen.
- Die Break-Ursache folgt Papers Blockprüfung. Die Änderung führt keine neue
  vollständige Kollisions- oder Suffokationsberechnung ein.
- Erfahrungsorbs durchlaufen den bereits vorhandenen allgemeinen Spawn-Event-Pfad
  genau einmal. Dessen Abbruch-/Entfernt-Prüfung findet weiterhin vor dem Merging statt.
- Die beiden Änderungen am Plugin-Spawning gehören zusammen. Die äußere
  Feature-Prüfung bleibt erhalten. Der interne Request ändert weder das natürliche
  Spawning noch die spätere vanillaabhängige Despawn-Logik auf „Friedlich“.
- Beim Karten-Drop wird ausschließlich der vorhandene Map-Daten-Pfad für weitere
  Kartentypen zugelassen. Folias bestehende Synchronisierung bleibt erhalten.
- Die Bewegungspaket-Änderung ergänzt nur die Disconnect-Ursache. Die bestehende
  Thread-Prüfung und die Entscheidung über den Kick bleiben bestehen.
- Es werden keine zusätzlichen Tasks, Thread-Wechsel, synchronen Chunk-Ladevorgänge
  oder Zugriffe auf fremde Regionen eingeführt. Die bestehenden Besitzerprüfungen
  der nachgelagerten Folia-/Tessera-Schichten bleiben erhalten.

Nicht übernommen sind die bereits lokal behobene Strohbett-Rekursion,
die Schild-Kontextbereinigung, die Entfernung der End-Terrain-Option,
Access-Transformer-Bereinigungen und nicht erforderliche Typ-/Formatierungsänderungen.

## Anwenden und bauen

Im Repository mit einem funktionsfähigen Java-/Gradle-Setup ausführen:

```powershell
.\gradlew.bat buildTessera
```

Der Task übernimmt die versionierte Sinopia-Basis, wendet alle Patchschichten an,
führt Tests und Build-Prüfungen aus und erstellt:

```text
build/libs/tessera-server-26.3-rc-3.build.003-alpha.jar
```

Ein manuelles Einfügen in erzeugte Java-Dateien ist nicht notwendig.
Vor einem erneuten Patch-Durchlauf eigene ungesicherte Änderungen an solchen
erzeugten Dateien zuerst in die zuständige versionierte Schicht übernehmen.

Die neuen Tests isolieren Welt-, Netzwerk- und Plugin-Grenzen mit Mocks.
Sie ersetzen keinen Live-Test mit Clients, Plugins und mehreren Regions-Threads.

## Verifizierter Prüfstand

- Der vollständige Build wurde nach dem PC-Absturz mit
  `.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel`
  erfolgreich abgeschlossen. Diese Optionen begrenzen nur den Prüflauf;
  die allgemeinen Gradle-Parallelitätseinstellungen wurden nicht verändert.
- Die erhaltenen, zum unveränderten Quellstand passenden Testergebnisse wurden
  ausgewertet: 9.923 Server-Testfälle, davon 87 übersprungen, sowie 529 API-Testfälle,
  davon 2 übersprungen; jeweils 0 Fehler und 0 Fehlschläge. Der Wiederanlauf
  bestätigte die Testtasks als aktuell.
- Alle 29 Fälle in `PaperRc3EntityBackportTest` sind erfolgreich und nicht übersprungen.
- Die Sinopia- und Tessera-Patchschichten wurden erneut angewendet. Die neuen
  Laufzeitkorrekturen sind im final erzeugten Tessera-Code vorhanden.
- Die fertige JAR wurde mit Java `25.0.3`, `-Xmx1G` und `--version` im isolierten
  Verzeichnis `build/rc3-build003-launcher-check-20260914-205354` geprüft.
  Ergebnis: `26.3-rc-3-003-f91ff9c`, Exit-Code `0`; kein Weltstart.

Artefaktgröße: `67.650.327` Bytes.

SHA-256 der geprüften JAR:

```text
108ffe662dfdd33c574290a31dc1665d27bba83075b0ae23c3bf6a17496ad3ac
```
