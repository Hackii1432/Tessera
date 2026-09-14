# Paper-Backports für Tessera / Sinopia 26.3-rc-2

## Herkunft und Umfang

Die importierte Paper-Basis bleibt
`38b0bfeb67855206ede9cb1df4f3354c4611c4c2`. Die folgenden Änderungen werden
selektiv aus `PaperMC/Paper`, Branch `dev/26.3`, bis einschließlich
`e285a336a6bc7ccdec8803d528c67c80d431760a` übernommen. `paperRef` wird deshalb
nicht auf einen vermeintlich vollständig importierten neuen Basisstand gesetzt.
Minecraft bleibt `26.3-rc-2`; Mache und Java-Toolchain werden nicht geändert.

| Paper-Commit | Übernahme |
| --- | --- |
| `812aeeb7270658a24cfa38e0d25a81a6947af5c0` | Partikel-API mit drei Geschwindigkeitsachsen und Randomisierung; alte öffentliche Overloads bleiben erhalten. |
| `890a11155b236035bcc0fabc82499f8517799566` | Piston-Reaktionszuordnung und Advancement-Baum-Neuberechnung. |
| `a8210fd786a1e329fe6bff82620c9afaa547fe16` | Entfernte Abbau-Abbrüche; in Tessera zusätzlich durch Besitzer-, Ladezustands- und Reichweitenprüfung abgesichert. |
| `4c90083394b217ada5ce9c2f41527d0f3a480f09` | Gezielte Laufzeitkorrekturen; keine pauschale Übernahme der 211 geänderten Dateien. |
| `74737daa9b99aa65b06b635b827cd9f024cafe7a` | Abbrechbares EntityChangeBlockEvent vor der Blocktransformation. |
| `e285a336a6bc7ccdec8803d528c67c80d431760a` | Schild-Zeichenlimit; die Editor-Platzierung bleibt im vorhandenen Tessera-Patch 0036. |

Vergleich und Original-Autoren:
https://github.com/PaperMC/Paper/compare/38b0bfeb67855206ede9cb1df4f3354c4611c4c2...e285a336a6bc7ccdec8803d528c67c80d431760a

Nicht übernommen werden die reinen TODO-Bereinigungen, der Merge-Commit,
Papers historische `oldPaperCommit`-Updatehilfe und die internen
ItemStack-Umbenennungen/-Methodenentfernungen. Die ursprünglichen Lizenzen
und Autorenhinweise der Sinopia-Basis bleiben erhalten.

## Verhalten und Regionsgrenzen

- Braustände synchronisieren ihre vier Menü-Datenwerte; BrewingStartEvent
  verwendet die tatsächliche Rezeptbrauzeit.
- Der Zeitbefehl vergleicht den vorherigen Zustand und schreibt erst nach
  erfolgreicher Plugin-Freigabe. Tesseras vorhandene globale Clock-Zuständigkeit
  und regionsbezogene Spieler-Paketverteilung bleiben erhalten.
- Notenblöcke schalten ihre Note einmal weiter und respektieren deaktivierte
  Noten-Updates.
- Bett-Explosionen erhalten den vor dem Entfernen gesicherten Blockzustand
  als Schadensursache; dies ersetzt nicht die vorhandenen Strohbetten-Fixes.
- `seed-abandonedcamp` wird bei der Strukturplatzierung berücksichtigt. Bereits
  erzeugte Chunks werden nicht nachträglich neu generiert.
- NBT-Long-Arrays erhalten die zusätzliche Größenprüfung vor der Allokation.
- Blocktransformationen lassen Plugins vor Blockänderung, Drops und
  Itemverbrauch abbrechen.
- Entfernte Abbau-Abbrüche dürfen keine fremden oder ungeladenen Chunks lesen,
  laden oder verändern. Es gibt dafür weder synchrones Nachladen noch eine
  Weiterleitung auf den Global-Thread. Auch eine gespeicherte alte Abbauposition
  wird vor dem Zugriff erneut auf Besitz geprüft.
- `Paper.maxSignLength <= 0` deaktiviert das Zeichenlimit. Positive Limits
  zählen Unicode-Codepoints und zerschneiden keine Surrogatpaare.

## Bauen und Prüfen

Alle Minecraft-Änderungen werden als Patches versioniert. Die normale
API-/Implementierung liegt wie bisher direkt unter `sinopia/`.

| Datei/Schicht | Inhalt |
| --- | --- |
| `sinopia/paper-server/patches/features/0039-Backport-Paper-26.3-runtime-corrections.patch` | Allgemeine Minecraft-Korrekturen aus Paper mit den beschriebenen Sinopia-Anpassungen. |
| `folia-server/minecraft-patches/features/0037-Keep-distant-block-break-aborts-region-owned.patch` | Regionssichere Zulassung und Verarbeitung der Abbau-Abbrüche. |
| `folia-server/paper-patches/features/0027-Test-Paper-backport-clock-and-block-abort-ownership.patch` | Tests für Clock-Weiterleitung, Plugin-Abbrüche und Regionsgrenzen. |
| `sinopia/paper-server/src/test/java/io/papermc/paper/porting/*Backport*Test.java` | API-, Paket-, Braustand-, Notenblock-, NBT-, Schildlimit-, Struktur- und Event-Regressionstests. |

```powershell
.\gradlew.bat buildTessera
```

Der Task wendet die versionierten Patchschichten neu an, führt Tests aus
und baut die ausführbare JAR unter `build/libs/tessera-server-*.jar`.
Generierte Minecraft-Quellen nicht zusätzlich in den Root-Commit aufnehmen.

### Prüfstand

Der gezielte Testlauf ist erfolgreich: 53 neue Regressionstestfälle und
41 bestehende Schild-/Bett-Testfälle, insgesamt 94 ohne Fehler. Zusätzliche
Gradle-Suite-Container sind dabei nicht als Testfälle gezählt.
Die Tests führen die betroffenen Produktionsmethoden aus; Live-Welt,
Plugin-Entscheidungen und Regionsbesitz werden gezielt durch Test-Doubles
abgebildet. Der Paralleltest verwendet zwei getrennte Test-Threads und Welten,
nicht zwei live laufende Folia-Regionen.

Der vollständige `buildTessera --offline --no-daemon --console=plain`-Lauf
ist am 14.09.2026 nach 8 Minuten und 6 Sekunden erfolgreich abgeschlossen:
erneute Anwendung aller Patchschichten, Kompilierung, gesamte Testsuite,
Checkstyle/Prüftasks und Erstellung der ausführbaren Server-JAR.
Die neu erzeugten Minecraft- und Server-Quellbäume stimmen vollständig mit
dem zuvor gezielt getesteten Stand überein; die Änderungen sind somit aus
den gespeicherten Quellen und Patches reproduzierbar.

| Testsuite | Gemeldete Testfälle | Davon übersprungen | Fehler / Fehlschläge |
| --- | ---: | ---: | ---: |
| Server | 9.892 | 87 | 0 |
| API | 529 | 2 | 0 |

Die neue JAR liegt unter
`build/libs/tessera-server-26.3-rc-2.build.001-alpha.jar`
(erstellt am 14.09.2026 um 15:53:37 Uhr, 66.419.571 Bytes).
SHA-256: `38ce0d5f2f631d0233aaec184c74c6ed683c07313df3af967ac9a648f716b040`.

Eine Live-Abnahme mit echten Clients und den eingesetzten Plugins bleibt
zusätzlich erforderlich, insbesondere an Regions-/Dimensionsgrenzen.

Empfohlene Live-Abnahme auf einer separaten Testwelt:

1. `/time set` bei geändertem und unverändertem Wert sowie mit Plugin-Abbruch
   prüfen; bei Abbruch darf sich die Uhr nicht ändern.
2. Braustand-Fortschritt/Brennstoff, einmalige Notenblock-Abstufung und
   Bett-Explosionsereignisse prüfen.
3. Blocktransformationen mit erlaubendem und abbrechendem Schutzplugin testen.
4. Blockabbau starten, Abstand vergrößern und abbrechen; zusätzlich getrennte
   Regionen und einen Dimensionswechsel prüfen.
5. Schilder platzieren und direkt bearbeiten sowie neue und alte Partikel-API
   mit echten Clients testen. Alte NMS-Getter wie `getX()` werden durch diese
   API-Aktualisierung nicht wieder eingeführt; Plugins mit direkten NMS-Zugriffen
   müssen weiterhin zum Server-Bundle passen.
