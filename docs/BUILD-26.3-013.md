# Build 013 – regionsgebundene Endportal-Nachbearbeitung

Stand: 29.09.2026. Minecraft/API 26.3, Java 25, Tessera `013-beta`.

## Quellstand und Umfang

- Branch: `ver/26.3.x`.
- Basiscommit: `b8c29f2e89e806ed8f6eb7fa42285d857395ad86`.
- Minecraft-Patch: `folia-server/minecraft-patches/features/0051-Restore-opt-in-End-portal-duplication-with-owned-tic.patch`.
- Server-Testpatch: `folia-server/paper-patches/features/0040-Test-owned-End-portal-falling-block-continuation-gua.patch`.
- Keine Commits/Pushes im Tessera-Hauptrepository. Die Feature-Commits in den
  generierten Arbeitsrepositories dienen ausschließlich dem Patch-Export.
- Sinopia, MCC/MVE-Produktcode, Minecraft/API und Gradle-Werkzeuge unverändert.
  Der Basiscommit allein enthält die neuen lokalen Patches noch nicht.

## Ausführbares Artefakt

- Datei: `build/libs/tessera-server-26.3.build.013-beta.jar`.
- Größe: **55.382.588 Bytes**.
- SHA-256: `8a549633f6089143422f4ce4a6def7fda0145187e77b5c1a5b3b6843a06a3a1a`.
- Vollständiger `buildTessera`-Durchlauf: **BUILD SUCCESSFUL**, 11 Minuten
  15 Sekunden; Log `build/build-013-beta-final.log`.
- Beide neuen Feature-Patches wurden exportiert und in diesem Durchlauf erneut
  angewendet. Die danach generierten Minecraft-/Server-Arbeitsrepositories sind
  ohne zusätzliche ungesicherte Änderungen. Es wurden keine Schutzprüfungen
  abgeschaltet und keine bestehenden Testassertionen entfernt.

Die Quellbasis besteht aus dem genannten Basiscommit plus diesen lokalen Dateien:

| Datei | SHA-256 |
| --- | --- |
| Minecraft-Patch `0051` | `c90d6e49a40c42c60bcffecaef9a2045506dbf0595a3a1cffbccb2a3d0333cee` |
| Server-Testpatch `0040` | `2e8c5659a34586d17e326fa71b9d5d7b91f6dee2eaaf8e0b7211217588282651` |
| `gradle.properties` | `3173d50c2907a98c6e57f19dac1b9e551697b6fc9ec45fb5e85b2bec333cbb62` |

Die kurze Revision `b8c29f2` im Artefakt bezeichnet die Basis, nicht einen bereits
veröffentlichten Root-Commit mit den neuen Patches. Das separate Testplugin wurde
danach für zusätzliche Drop-Gegenprüfungen neu gebaut; die Server-JAR wurde
dabei nicht verändert.

## Aktivierung und Verhalten

In `config/paper-global.yml` die vorhandene Einstellung verwenden und den Server
neu starten. Ein vorhandenes `unsupported-settings`-Mapping erweitern, nicht ein
zweites gleichnamiges Mapping anlegen:

```yaml
unsupported-settings:
  allow-unsafe-end-portal-teleportation: true
```

Der Standard bleibt `false`: weiterhin Folias bisheriger FallingBlock-Ablauf.
Mit `true` wird für passende Endportal-Kollisionen der Portalpfad vor dem
Landungs-/Platzierungsteil des FallingBlock-Ticks ausgeführt. Während Tessera
das Ziel asynchron vorbereitet, bleibt der erfasste FallingBlock im Ursprung
angehalten. Es wird kein Regions- oder Global-Thread auf einen Future blockiert.

Nach erfolgreicher Vorbereitung und erneuter Zulässigkeitsprüfung wird das
Original regulär zum Ziel eingereiht. Danach wird der unveränderte native
Landungs-/Drop-Teil einmalig auf einer separat registrierten Entity im Ursprung
ausgeführt. Diese hat eine neue UUID und einen eigenen Bukkit-Wrapper. Der
Wrapper und der Scheduler des Originals folgen weiterhin seinem Zieltransfer.
Der entfernte Original-Handle wird nicht getickt; keine Threadprüfung wird
deaktiviert. Die zusätzliche Quell-Entity wird im selben Aufruf wieder entfernt.

Die Nachbearbeitung wirkt nur, wenn die normale Landungs-, Platzierungs- oder
Drop-Logik in diesem Tick tatsächlich etwas erzeugen würde. Ein frei schwebender
Portaltransfer erzeugt durch die Option allein keine zweite Kopie. Wiederholte
Ausführung der Quell-Nachbearbeitung und weitere Teleports dieser kurzlebigen
Entity sind gesperrt. Netherportale und gewöhnliche API-Teleports erhalten keine
solche Nachbearbeitung. Passenger-/Vehicle-Konstellationen sind ausgeschlossen.

Portal-Vetos und fehlgeschlagene Vorbereitung erzeugen keine Zusatzkopie. Vor
der Quell-Nachbearbeitung werden Weltidentität, aktive Welt, unveränderte Position
und Ownership des betroffenen Bereichs geprüft. Bei ungültigem Quellzustand wird
auf die zusätzliche Arbeit verzichtet. Ein bereits zugelassener Zieltransfer
wird dadurch nicht zurückgenommen.

Das Konzept der eigenständigen Quell-Fortsetzung ist durch
[Canvas PR 307](https://github.com/CraftCanvasMC/Canvas/pull/307) angeregt.
Tesseras Integration berücksichtigt zusätzlich seine bereits vorhandene
asynchrone Portal-Vorbereitung, die das Original bis zum Besitzer-Callback noch
nicht entfernt. Die Canvas-Transferreihenfolge wurde deshalb nicht unverändert
übernommen.

## Plugin-Verhalten und Grenzen

- Ein `EntityChangeBlockEvent` der Quell-Entity lässt sich abbrechen. Das ist kein
  Rollback des bereits eingereihten Zieltransfers. Quell- und Ziel-Events können
  auf unterschiedlichen Regions-Threads laufen.
- Eine durch einen Listener entfernte Quell-Entity darf danach keinen Block
  mehr platzieren. Original und Quell-Entity haben bewusst verschiedene UUIDs.
- Die Option bleibt eine bewusste Exploit-Freigabe, keine Vanilla-Sicherheitsoption.
  NBT-Kopien und temporäre Entity-Registrierung verursachen zusätzliche Arbeit;
  ein Durchsatz-/Lastbenchmark großer Farmen ist nicht Teil dieser Abnahme.
- Asynchrone Zielvorbereitung verändert die zeitliche Abfolge gegenüber Vanilla.
  Eine konkrete Spieleranlage mit Piston-/Slime-/Honey-Taktung und externen
  Plugins ist damit nicht pauschal zertifiziert.
- Keine dauerhafte Zweiphasentransaktion über beide Welten: Ein harter Prozess-
  oder Maschinenabbruch garantiert nicht die atomare Speicherung beider Seiten.
  Unregistrierte Fortsetzungszustände werden nicht als spätere Replay-Jobs
  persistiert. Die bestehenden Portal-/Welt-Speicherregeln bleiben maßgeblich.

## Tatsächlich ausgeführte Prüfungen

Die folgenden Serverläufe verwendeten die oben angegebene **finale JAR** mit
SHA-256 `8a549633f6089143422f4ce4a6def7fda0145187e77b5c1a5b3b6843a06a3a1a`.
Die Prüfsumme wurde nach den Läufen erneut kontrolliert. Die maschinenlesbaren
Ergebnisse stehen in [native-end-portal.json](test-evidence/26.3-013/native-end-portal.json).

### Native Portalserie

**68 Fälle bestanden**, jeweils 34 mit `false` und mit `true`. Die YAML-Option
wurde im laufenden nativen Konfigurationsobjekt geprüft. Echte FallingBlock-
Bewegung und Portal-Kollision, kein manuell erzwungenes `onGround`, kein
gemockter Transfer. Mehrere gleichzeitig aktive, entfernte Ursprungsregionen:

| Fälle pro Einstellung | Prüfung |
| --- | --- |
| 20 | Sand, roter Sand, Kies, weißes Betonpulver und Amboss; zwei entfernte Ursprungsbereiche in zwei Runden, zunächst ungeladene End-Ziele |
| 5 | Rückweg aus dem End in die Oberwelt für dieselben Materialien |
| 1 | Freier Portaltransfer ohne Landung: keine zusätzliche Quellkopie |
| 2 | Veto beim Portaleintritt und beim Portaltransfer |
| 1 | Abgebrochene Quell-Platzierung; Zieltransfer wird nicht zurückgenommen |
| 1 | Umleitung in die Ursprungswelt: keine zusätzliche Quellkopie |
| 2 | Entfernung des Originals im Portal-Listener bzw. der Fortsetzungs-Entity im Platzierungs-Listener |
| 2 | Tatsächlicher Item-Drop mit Material/Menge und separat abgebrochenes `EntityDropItemEvent` |

Bei geeigneter Landung ergibt `false` einen Ursprungsblock und keinen Zielblock;
`true` ergibt genau einen Ursprungs- und einen Zielblock. Beide Transferrichtungen
wurden so geprüft. Event-Zähler, echte Blockzustände, Quell-/Ziel-Ownership,
unabhängige Quell-UUID, beibehaltener Ziel-Wrapper und fehlende zurückbleibende
FallingBlock-Entities wurden überprüft. Der Drop-Fall prüft genau ein echtes
Item, erhöht nur dessen Portal-Cooldown gegen einen anschließenden, hier nicht
untersuchten Item-Portaltransfer und entfernt es nach der Inhaltsprüfung.
Keine zusätzlichen Items und keine Cross-Region-Ausnahmen im Abschlusslauf.

Lokale Rohdaten:

- `build/end-portal-dupe-false-1790711117752/result.json`
- `build/end-portal-dupe-true-1790711203384/result.json`
- Jeweils zugehörige `runner.log` und `end-portal-duplication-checks.txt`.

### Weitere Regressionen und Qualitätsprüfungen

- Vollständiger Gradle-Build inklusive Patch-Reapply, Tests, Checkstyle und
  API-/Server-`scanJarForBadCalls`: bestanden. Unveränderte API-Aufgaben wurden
  teilweise durch Gradles `UP-TO-DATE`-Prüfung wiederverwendet.
- Server-Testberichte: 10.183 Fälle, 87 übersprungen, **0 Fehler/Fehlschläge**.
  API-Testberichte: 529 Fälle, 2 übersprungen, **0 Fehler/Fehlschläge**. Bestehende
  Skip-Regeln wurden nicht geändert.
- Die sechs neuen Guard-Tests wurden ausgeführt, keiner übersprungen: wartender
  Zustand, Abbruch, deaktivierte Option, veraltete Position, keine weiteren
  asynchronen Teleports der Quell-Entity und keine zweite Tick-Ausführung.
- Bestehender Runtime-Welt-Lifecycle-Lauf auf derselben JAR: **PASS**, 298
  aufgezeichnete Prüfschritte auf zwei Regions-Threads. Kein Spieler verbunden:
  Der optionale interaktive Spieler-Lifecycle wurde ausdrücklich übersprungen.
  Ergebnis: `build/paper-beta-smoke-full-1790711120070/result.json`.
- Buildwerkzeug-Selbsttests: **22 bestanden**.
- Dokumentations-Validator-Selbsttests: **57 bestanden**; API-Referenz mit
  19 Artikeln/49 Links gültig; **9 vollständige Java-Beispiele kompiliert**.
- Neue Release-Metadaten sowie lokale Links in Changelog, README, Workflow und
  diesem Buildbericht geprüft. Der globale historische Changelog-Scan meldet
  weiterhin unquotiertes Frontmatter in `docs/builds/26.2/0.0.1.md`. Dieser
  bereits vorhandene Eintrag wurde nicht im Gameplay-Auftrag umgeschrieben;
  der neue 013-Eintrag besteht seine gesonderte Prüfung.

Nicht Bestandteil dieser Abnahme: komplette Spieler-Farm, visuelle Client-
Darstellung, externe Plugin-Integration, Farm-Durchsatz und Crash-Atomarität.
MCC/MVE wurden in diesem Auftrag weder verändert noch erneut gemeinsam getestet.

## Wiederholbare Prüfungen

Nach dem regulären Patchworkflow und mit JDK 25:

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel --no-configuration-cache
node smoke-tests/end-portal-duplication/run.mjs "$env:JAVA_HOME/bin/java.exe" build/libs/tessera-server-26.3.build.013-beta.jar
```

Auf Linux/macOS `./gradlew` verwenden und den Java-Pfad anpassen. Die genaue
Testanordnung und die Unterscheidung zwischen nativen Portal-Kollisionen und
einer kompletten Spieler-Farm stehen in der
[Fixture-Dokumentation](../smoke-tests/end-portal-duplication/README.md).
