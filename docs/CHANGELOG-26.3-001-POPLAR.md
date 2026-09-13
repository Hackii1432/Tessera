# Tessera 26.3-rc-2 – Poplar-Wachstum und Region-Tick-Crash

Stand: 13. September 2026. Versionswerte unverändert: `001-alpha`.

## Behobener Fehler

Die Bukkit-Zuordnung in `TreeGrower#setTreeType` kannte die drei neuen
Vanilla-Features `red_poplar`, `orange_poplar` und `yellow_poplar` noch nicht.
Sie warf deshalb `IllegalArgumentException: Unknown tree generator`, bevor
der ausgewählte Baumgenerator aufgerufen wurde.

Bei Knochenmehl erschien der Fehler während der Paketverarbeitung. Beim
natürlichen Wachstum trat dieselbe Ausnahme im Region-Tick auf und führte
zum Herunterfahren des Servers. Der zusätzliche Crashlog für Region 58
bestätigt diesen zweiten Aufrufpfad über `SaplingBlock.randomTick`.

## Änderungen

- Eigener Sinopia-Minecraft-Patch:
  `sinopia/paper-server/patches/features/0038-Support-vanilla-poplar-tree-types.patch`.
- Die versionierte Sinopia-API ergänzt `TreeType.RED_POPLAR`,
  `TreeType.ORANGE_POPLAR` und `TreeType.YELLOW_POPLAR` am Ende der Enumeration.
  Die Reihenfolge der bisherigen Werte bleibt erhalten.
- `CraftRegionAccessor` ordnet diese API-Typen den gleichnamigen originalen
  Vanilla-Features zu. `generateTree` fällt für sie nicht auf Eiche zurück.
- Der bestehende Folia-Patch `0001-Region-Threading-Base.patch` berücksichtigt
  die drei neuen Zuordnungen und schreibt sie wie alle bisherigen Baumtypen
  in `SaplingBlock.treeTypeRT`. Kein neuer globaler Zustand oder Threadwechsel.

Die Änderung gilt für Knochenmehl und natürliches Setzlingswachstum.
Vanillas Farbgewichtung von jeweils 1, Wachstumsvoraussetzungen, Baumformen,
Dekorationen und Wiederherstellung bei fehlgeschlagener Platzierung bleiben
unverändert. Unbekannte Features werden nicht pauschal durch Eichen ersetzt
und Ausnahmen nicht einfach verschluckt.

Die bestehenden `StructureGrowEvent`-/`BlockFertilizeEvent`-Aufrufe, deren
Abbruchbehandlung und das Aufräumen der aufgenommenen Blockzustände bleiben
unverändert. Plugins erhalten beim Wachstum nun den passenden Poplar-Typ.
Plugins mit einer eigenen abschließenden Liste erlaubter Baumtypen müssen
die neuen API-Werte gegebenenfalls berücksichtigen.

## Prüfung

`PoplarTreeRegressionTest` enthält 22 Testfälle: echte Registry-Zuordnungen
für alle drei Farben und bestehende Baumtypen, erfolgreiche/fehlgeschlagene
Platzierung über den echten `TreeGrower`, Bukkit-Featureauswahl, unveränderte
Vanilla-Gewichte und parallele threadlokale Baumtyp-Zuordnung.

Gegen den vorherigen generierten Servercode bestätigen die drei Tests mit
echten Poplar-Registry-Einträgen die gemeldete Ausnahme; auch die Prüfung der
neuen Enum-Werte und der parallelen Zuordnung schlägt fehl. Drei bestehende
Baumtypen sowie die Ablehnung tatsächlich unbekannter Features bestehen.
Protokoll: `build/poplar-regression-before.log`.

Der erste Testlauf meldete insgesamt 18 Fehler, davon gingen 13 auf einen
inzwischen korrigierten Aufbau der Mockito-Test-Doubles zurück. Diese 13
Ergebnisse sind kein Nachweis eines Serverfehlers.

Die Tests verwenden echte Registry-Inhalte und die echten Zuordnungs- und
Wachstumsmethoden. Laufende Welten und die eigentliche Feature-Platzierung
werden durch Test-Doubles ersetzt. Der Paralleltest prüft Metadaten-Isolation,
nicht die Platzierung vollständiger Bäume in zwei laufenden Regionen.

Alle 22 neuen Fälle bestehen nach der Korrektur; keiner wurde übersprungen.
Die XML-Testberichte des geprüften Gesamtstands enthalten:

| Testberichte | Erfasst | Fehler | Übersprungen |
| --- | ---: | ---: | ---: |
| API | 529 | 0 | 2 |
| Server einschließlich Poplar-Tests | 9.789 | 0 | 87 |
| Checkstyle-Modul | 3 | 0 | 0 |
| Gesamt | 10.321 | 0 | 89 |

Die Server-Tests wurden neu ausgeführt. Unveränderte Teilaufgaben durften
Gradles gültige Up-to-date-/Cache-Ergebnisse verwenden.

`buildTessera` war nach 9 Minuten 33 Sekunden erfolgreich. Alle Patchschichten
wurden erfolgreich vorbereitet. Die generierten API-, Server- und
Minecraft-Arbeitsquellen enthalten keine ungesicherten Handänderungen.
Protokoll: `build/poplar-tessera-build-2.log`.
Der vorherige vollständige Build in `build/poplar-tessera-build.log`
scheiterte ausschließlich an den 13 inzwischen korrigierten Testaufbau-Fehlern.

Der Launcher-Test mit JDK 25.0.3 und `--version` endete mit Exit 0 und
`26.3-rc-2-001-2f44fa6`. Dabei wurde keine Spielwelt gestartet.
Protokoll: `build/port-26.3-launcher-check/launcher-poplar-java25-version.log`.
Ein Ingame-Test auf einer laufenden Welt wurde hier nicht durchgeführt.

## Anwenden und bauen

Mit JDK 25 im Tessera-Hauptverzeichnis:

```powershell
.\gradlew.bat buildTessera
```

Der Task übernimmt die lokale Sinopia-Basis, wendet alle Patchschichten an,
testet und baut den Server. Kein zusätzliches manuelles `git apply` nötig.
Eigene Änderungen in generierten Minecraft-Quellen vorher in Patches sichern.

API-/CraftBukkit-Änderungen unter `sinopia/`, Patch 0038 und die Anpassung des
Folia-Basispatches gehören zusammen. Nur Patch 0038 allein genügt nicht.
Sinopia ist bereits Teil desselben Tessera-Repositories.

Ausgabe: `build/libs/tessera-server-26.3-rc-2.build.001-alpha.jar`.
Neue JAR: 66.415.088 Bytes. SHA-256:
`2de72191c2324a72f9af85a45856a4bd6429870b2b3431b0cef6d147389ccdd8`.

Nach erfolgreichem Build die Server-JAR ersetzen und vollständig neu starten.
Die vorherigen Bett- und Gamerule-Korrekturen sind weiterhin enthalten.

Vor dem Einsatz eine Welt-/Server-Sicherung anlegen und auf einer separaten
Testwelt Knochenmehl, natürliches Wachstum und Plugin-Abbruch prüfen.
Bei genug Wiederholungen müssen alle drei Farben wachsen; ein Setzling
muss nicht bei jeder Knochenmehl-Anwendung erfolgreich wachsen.
Zusätzlich in zwei getrennten Regionen testen und das Serverlog auf
`Unknown tree generator` kontrollieren.

Es wurde kein Commit im Tessera-Hauptrepository erstellt und nichts gepusht.
