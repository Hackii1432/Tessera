# Selektive Paper-Backports vom 21. September 2026

Tessera für Minecraft **26.3**, Build **006-alpha**, Java 25 und Gradle 9.4.1.
Geprüfter Paper-Endstand: `c13e3c9f0a79d0a117af4273d872c767f05b0751` aus `dev/26.3`.
Dies ist kein vollständiger Paper-Rebase; `paperRef` bleibt die Importreferenz.

## Übernommene Korrekturen

| Bereich | Änderung | Paper-Commit |
| --- | --- | --- |
| Moonrise | Richtige Unload-Z-Koordinate, euklidische Chunk-Suchreihenfolge und IntegerProperty-Grenzen bei Minimum größer null. | [cc95f00](https://github.com/PaperMC/Paper/commit/cc95f009b7e4b5687feb0a068d9fc44b326dc8c3) |
| Blockabbau | Verzögerter Abbau verwendet vergangene Ticks; konsistente Initialisierung der monotonen Tick-Uhr. | [e6e9780](https://github.com/PaperMC/Paper/commit/e6e9780e32ac0663e324344a54d19d10fb6a85c9), [c13e3c9](https://github.com/PaperMC/Paper/commit/c13e3c9f0a79d0a117af4273d872c767f05b0751) |
| API-Adapter | Biomprovider ohne geteilten veränderlichen Sampler-Cache, ItemType-Metadaten ohne Zugriff auf eine beliebige Welt, aktualisierte Material-Tags. | [0fd1cf7](https://github.com/PaperMC/Paper/commit/0fd1cf72ffb8eefe8bd4899620695a77659db42b) |
| Schildtext | Veränderbare Builder-Listen, vier gültige Zeilen, unabhängige Kopien bestehender und erzeugter Komponenten. | [0f0cb00](https://github.com/PaperMC/Paper/commit/0f0cb00e6d76337b3a095fb9dabde8263f97282d) |
| Kissen | Abbrechbares `EntityPlaceEvent` vor Spawn und Verbrauch. | [baa0d82](https://github.com/PaperMC/Paper/commit/baa0d82f7cb3001443d24c2847a81effd10800b4) |
| Spectator-Portale | Portalinteraktionen werden nicht allein wegen eines fehlenden Inventarmenüs gesperrt. | [4dee7c4](https://github.com/PaperMC/Paper/commit/4dee7c425bdafaca594b7711e78493443e5e0901) |
| Schwefelwürfel | Spieler-Schubpfad respektiert `EntityCollideWithEntityEvent`, einschließlich Abbruch vor Bewegung und Kontaktschaden. | [a255185](https://github.com/PaperMC/Paper/commit/a2551859805030dd1986d412cdc4f587fdddbc0b) |
| Bienen/Pandas | Alarmierte Artgenossen verwenden `TARGET_ATTACKED_NEARBY_ENTITY`. | [27553f5](https://github.com/PaperMC/Paper/commit/27553f52f7776f59fea6499756fba66422c35232) |
| Region-Dateicache | Eine fehlende Datei verdrängt keine andere offene Datei aus einem vollen Cache. | Teil aus [c5c8f6c](https://github.com/PaperMC/Paper/commit/c5c8f6cbbf70ae37448995092660c3046a1192b1) |

## Tessera-/Folia-Anpassungen

- Beim Login wird der Spieler vor seiner Region-Zuweisung erstellt. Sein
  Konstruktor liest deshalb nur die weltunabhängige Uhr. Während des Tickens
  bleibt der Zugriff auf den regionalen Zeitstand erhalten.
- Vorhandene Besitz- und Ladeprüfungen beim Blockabbau bleiben erhalten.
- Spectator-Portale verwenden `Portal#portalAsync` mit Tesseras bestehenden
  Portal-Events und Zielregions-Verarbeitung, nicht den synchronen Vanilla-Pfad.
  Nach Kamera-/Plugin-Events wird die Spielerzuständigkeit erneut geprüft.
- Schwefelwürfel-Kollisionen benötigen einen gemeinsamen Besitzer-Thread;
  nach dem Event werden Besitzer, Entfernungsstatus und Welt erneut geprüft.
- Nach dem Kissen-Event werden Entfernungsstatus, Welt und Chunk-Zuständigkeit
  geprüft, bevor die neue Entity hinzugefügt wird.
- Es werden keine wartenden Futures oder synchronen Cross-Region-Aufrufe eingeführt.

## Erhaltene Kompatibilität und Grenzen

`SignText.signText(List)` liefert weiterhin `Builder`; `addLine`, `filteredLines`
und vorhandene Bridge-Signaturen bleiben erhalten. Ersetzte Zeilen aktualisieren
auch ihre gefilterte Darstellung; unveränderte gefilterte Zeilen bleiben erhalten.

Konstante Datenpaket-Werte für Brenndauer und Kompostierung bleiben unterstützt.
Gewichtete Kompostierverteilungen liefern Wahrscheinlichkeiten zwischen 0 und 1.
Die alte ItemType-API hat keinen Welt-/Blockkontext: Bei Kompostier-Dispatchern
wird wie im Paper-Backport der Default-Fall ausgewertet. Beliebige kontextabhängige
Datenpaket-Formeln werden damit nicht allgemein auswertbar. Normale Betonblöcke
bleiben neben den neuen Varianten in `MaterialTags.COLORABLE` enthalten.

Kein DataConverter-Wechsel, keine optionale Poplar-/SulfurCube-API, kein kompletter
Moonrise-Dateicache-Umbau und keine Entfernung bestehender Tessera-Korrekturen.
Die Korrekturen sind Build **006-alpha** zugeordnet; die Buildnummer ist in
`gradle.properties` bereits auf `006` gesetzt.

## Patch-Ablage und Build

- Sinopia-Minecraft: `sinopia/paper-server/patches/features/0044-*`.
- API, Adapter und allgemeine Tests: versionierte Quellen unter `sinopia/`.
- Tessera-Minecraft: `folia-server/minecraft-patches/features/0040-*` bis `0042-*`.
- Folia-Basispatch `0001-*`: Kontext an die neue Sinopia-Basis angepasst.
- Tessera-Tests: `folia-server/paper-patches/features/0031-*`.

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel
```

Dieser Befehl wendet alle Patches an und führt Tests und Build aus. Mit der
aktuellen Buildnummer erzeugt er `build/libs/tessera-server-26.3.build.006-alpha.jar`. Generierte Java-Dateien
müssen nicht separat übernommen werden.

## Verifikation

Der folgende Prüf- und Buildlauf enthielt bereits sämtliche hier beschriebenen
Codeänderungen, erfolgte aber vor der anschließenden Anhebung der Buildnummer
noch unter `005-alpha`. Für die Dokumentationsanpassung auf `006-alpha` wurde
kein erneuter Build ausgeführt.

- Sämtliche Sinopia-, Folia- und Tessera-Patches wurden gemeinsam erfolgreich angewendet.
- `:folia-server:test` erfolgreich: 10.012 erfasste Testfälle, keine Fehler,
  87 übersprungen. Darin enthalten sind 47 neue Regressionstestfälle.
- API-Tests erfolgreich: 529 erfasste Testfälle, keine Fehler, 2 übersprungen.
- Vollständiges `buildTessera --console=plain --max-workers=2 --no-parallel`
  erfolgreich: erneute Patch-Anwendung, Tests, Checkstyle, Bad-Call-Prüfungen
  und ausführbare Paperclip-JAR. Abschluss am 21. September 2026.
- Die neuen Tests prüfen tatsächliche Methoden für Metadaten, Builder, Events,
  Blockabbau, Portal-Routing und Besitzverlust sowie Suchreihenfolge und Dateicache.

Die automatisierten Regionsprüfungen verwenden kontrollierte Mocks. Sie ersetzen
keinen Live-Test mit Plugins, mehreren Spielern, getrennten Regionen und
Dimensionswechseln auf einer separaten Testwelt.

Veröffentlichungsnotizen: [Changelog für Build 006](CHANGELOG-26.3-006.md).
