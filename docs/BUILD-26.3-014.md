# Build 014 – Endportal-Flug und gezielte Enderaugen-Regel

Stand: 29.09.2026. Minecraft/API 26.3, Java 25, Tessera `014-beta`.

## Ursache und Korrektur

Der native Vergleichspfad `EndPortalBlock#getPortalDestination` setzt Nichtspieler
bei Eintritt ins End an `(100.5, 50, 0.5)` ein. Die Plattform wird mit Ursprung
Y=49 erzeugt; ihre Obsidianblöcke liegen bei Y=48 und ihre Oberfläche bei Y=49.
Nur Spieler werden in diesem Vanilla-Pfad um einen Block tiefer eingesetzt.
Tesseras bisheriger asynchroner Pfad benutzte dagegen auch für fallende Blöcke
Y=49. Dadurch landeten sie unmittelbar auf der Plattform, bevor ihre vorhandene
horizontale Bewegung sie zur Sammelanlage tragen konnte.

Die Korrektur trennt für **fallende Blöcke** die Standard-Ankunftshöhe von der
Plattformerzeugung: Ankunft wieder Y=50, Plattform unverändert. Andere Entity-
Typen und Spieler behalten ihre bisherige Behandlung. Die abweichenden
Zielkoordinaten eines Portal-Listeners werden nicht zusätzlich verschoben und
behalten die bisherige Plattformgeometrie. Die Höhenkorrektur greift auch ohne
Duplizierungsoption; die zusätzliche Quellkopie bleibt weiterhin an diese Option
gebunden. Rückweg aus dem End und Netherportal-Logik werden nicht geändert.

Es wird **keine** neue Geschwindigkeit gesetzt und kein künstlicher Item-Drop
erzeugt. Die native Portaltransformation bleibt bestehen: `Relative.DELTA`
enthält auch `ROTATE_DELTA`, sodass die vorhandene Geschwindigkeit passend zur
Ausgangsausrichtung gedreht wird. Bei Quell-Yaw 0 und Ziel-Yaw 90 wird zum
Beispiel Ostbewegung zu Südbewegung. Schwerkraft, Kollision, Buttons, Platzierung,
Drops und Hopper verarbeiten anschließend den normalen FallingBlock.

Die unveränderliche Plattformposition wird auf dem Ursprungsbesitzer berechnet.
Die eigentliche Erzeugung bleibt im bestehenden asynchronen Zielregionspfad.
Keine zusätzliche fremde Entity-Abfrage im Zielcallback, keine abgeschaltete
Ownership-Prüfung und kein blockierendes Warten auf Region-/Global-Threads.

## Ergänzung: Enderaugen trotz gesperrter Endportale

Die Ergänzung bleibt ausdrücklich bei Build `014-beta`. Die bestehende Regel
`allow_eyes_of_ender_use` wird nicht umbenannt; Standardwert `true` und bereits
gespeicherte Werte bleiben erhalten. Bei `false` gilt jetzt:

- Enderaugen dürfen wie im bestehenden Vanilla-Pfad geworfen werden und
  Strongholds finden. Keine Änderung an Suchradius, Projektilflug, Verbrauch,
  Drop-/Bruchchance oder Verhalten ohne gefundenes Ziel.
- Das Einsetzen in einen **leeren Endportalrahmen** ist weiterhin gesperrt;
  dabei wird kein Auge verbraucht. Bereits gefüllte Rahmen werden nicht verändert.
- Andere angeklickte Blöcke liefern wieder das normale `PASS`, damit der
  bestehende Item-Use-Fallback möglich bleibt. Es gibt keinen pauschalen Abbruch
  vor der Prüfung, ob überhaupt ein leerer Portalrahmen angeklickt wurde.
- Der bestehende Endportal-Reisepfad einschließlich seiner Besitzerprüfungen
  und bisherigen Spielerbehandlung bleibt gesperrt. API-Teleports werden
  dadurch nicht allgemein verboten. Die Regel gilt weiterhin pro Welt.

Mit `true` bleiben Rahmen-Einsetzen und Portalreisen wie bisher erlaubt.
Plugin-Vetos im Rahmen-/Entity-Spawn-Pfad werden nicht umgangen. **Enderperlen**
sind nicht Enderaugen und werden durch diese Änderung nicht verändert.

Die Implementierung entfernt ausschließlich die zusätzliche Wurf-Sperre und
verschiebt die Rahmen-Sperre hinter die Rahmenprüfung. Kein neuer Thread,
Scheduler, Suchalgorithmus oder weltübergreifender Direktzugriff wird eingeführt.
API-Javadoc beider Regel-Aliase und die [Gamerule-Referenz](api/gamerules.md)
dokumentieren den neuen Umfang.

## Quellstand und dauerhafte Patches

- Branch: `ver/26.3.x`.
- Root-Basiscommit: `b8c29f2e89e806ed8f6eb7fa42285d857395ad86`, zusätzlich die
  lokalen Änderungen aus Build 013 und diesem Build. Der Basiscommit allein
  enthält die neuen Patches noch nicht.
- Minecraft-Patch:
  `folia-server/minecraft-patches/features/0052-Restore-Vanilla-falling-block-End-arrival-clearance.patch`.
- Server-Testpatch:
  `folia-server/paper-patches/features/0041-Test-Vanilla-falling-block-End-arrival-geometry.patch`.
- Zusätzlicher Gamerule-Patch:
  `folia-server/minecraft-patches/features/0053-Keep-Ender-eye-locating-independent-of-portal-gameru.patch`.
- Gamerule-Testpatch:
  `folia-server/paper-patches/features/0042-Test-Ender-eye-use-and-portal-only-gamerule-guards.patch`.
- API-Dokumentationspatch:
  `folia-api/paper-patches/features/0014-Document-portal-only-Ender-eye-gamerule.patch`.
- Beim API-Patch-Export wurden in den vorhandenen Patches `0004`, `0006` und
  `0012` ausschließlich Blob-IDs und Zeilenpositionen aufgefrischt, keine
  zusätzlichen API-Inhalte oder Verhaltensänderungen ergänzt.
- Feature-Commits wurden nur in den generierten Arbeitsrepositories für den
  Patch-Export erstellt. Kein Commit oder Push im Tessera-Hauptrepository.
- Kein Sinopia-Upgrade, keine Änderung an MCC/MVE, öffentlichen API-Signaturen,
  Java-/Minecraft-Version oder Buildwerkzeugen.

| Lokale Quelldatei | SHA-256 |
| --- | --- |
| Minecraft-Patch `0052` | `41f80c92ae721df9d8aed4935285b04b8e7d348e598884af32d1de97207be57c` |
| Server-Testpatch `0041` | `7733f22f8f312aafee7bad38c64b36ab0ed531a2a6add7a346f97f1012d46b64` |
| Minecraft-Patch `0053` | `ef7c739538bba0be4342865957962ebdb0f1c4e1db1c5e6677d7d010182ebd7e` |
| Server-Testpatch `0042` | `dfd5d2476b2ef1bf934974281b5ec7a5ab74e2f2046620ed9348457d8fc56e68` |
| API-Dokumentationspatch `0014` | `ea4acc996816582c887d4883bcc89ea49467d5c0a506ca51f0ddbb67ebbfd43c` |
| `gradle.properties` | `d3ded656c943ae66b16eb3d8287f4b2e445875328e0c852a3cfe8edb494c8904` |

Die vorausgesetzten 013-Patches `0051`/`0040` und deren Prüfsummen stehen im
[vorherigen Buildbericht](BUILD-26.3-013.md#ausführbares-artefakt).

## Fehlernachweis auf Build 013

Der neue native Standardziel-Test wurde zuerst auf der unveränderten 013-JAR
mit SHA-256 `8a549633f6089143422f4ce4a6def7fda0145187e77b5c1a5b3b6843a06a3a1a`
ausgeführt. Er scheiterte an der erwarteten Assertion: Ankunft Y=49 statt Y=50.
Rohdaten: `build/end-portal-flight-true-1790713138083/result.json` und
`build/end-flight-013-reproduction.log`.

Die 68 bisherigen Portaltests benutzten absichtlich durch Listener gesetzte
Zielkoordinaten. Sie prüften Quellfortsetzung, Events, Platzierung und Drops,
aber nicht die Höhe und Flugbahn am unveränderten End-Standardziel. Der neue
20-Fälle-Test schließt genau diese Lücke, ohne die bisherigen Assertions zu
entfernen. Beim Entwickeln wurde außerdem eine falsche Testannahme korrigiert:
Vanilla behält den Bewegungsvektor nicht ungedreht bei, sondern wendet die oben
beschriebene Portalrotation an. Der Produktionscode dieser Rotation blieb
unverändert.

## Ausführbares Artefakt und abgeschlossene Prüfungen

- Datei: `build/libs/tessera-server-26.3.build.014-beta.jar`.
- Größe: **55.383.619 Bytes**.
- SHA-256: `7ba5721f611bf045214b0ac54e2852725569b691b50b4419b3bfaf11ff1a3e05`.
- Vollständiger `buildTessera`-Durchlauf: **BUILD SUCCESSFUL**, 10 Minuten
  8 Sekunden; Log `build/build-014-beta-ender-eye-final.log`.
- Diese ergänzte JAR ersetzt die vorherige 014-JAR mit SHA-256
  `4ed4d1bdba224ac39ec5619ed956e134d444105e670aef4f9f89f924851ae609`
  unter demselben Dateinamen und derselben Buildnummer.
- Die neuen Patches wurden dabei erneut angewendet; der Build enthält Tests,
  Checkstyle und API-/Server-`scanJarForBadCalls`. Unveränderte API-Aufgaben
  wurden teilweise als `UP-TO-DATE` wiederverwendet.
- Die generierten Minecraft-/Server-Arbeitsrepositories sind anschließend ohne
  ungesicherte zusätzliche Änderungen. Die JAR-Prüfsumme wurde nach den nativen
  Serverläufen erneut überprüft. Maschinenlesbare Ergebnisse:
  [native-end-flight.json](test-evidence/26.3-014/native-end-flight.json).
- Server-Testberichte: **10.205 Fälle**, 87 übersprungen, 0 Fehler/Fehlschläge.
  API-Testberichte: 529 Fälle, 2 übersprungen, 0 Fehler/Fehlschläge. Keine
  Änderung an bestehenden Skip-Regeln oder Assertions.
- Alle **sechs neuen Tests** wurden ausgeführt: Vanilla-Abstand zur Plattform,
  unveränderte Spieler-/sonstige Entity-Höhe, abweichendes Pluginziel, End-Ursprung,
  sonstige Entity-Plattform und Vanilla-Rotation der vier Bewegungsrichtungen.

### Enderaugen-Regeltests

**16 zusätzliche Regressionen bestanden** (`EnderEyePortalRuleTest`):

- Stronghold-Suche ohne Ergebnis bei beiden Regelwerten: Vanilla-Rückgabewert,
  keine Entity-Erzeugung und kein Item-Verbrauch.
- Gefundenes Ziel bei beiden Regelwerten, jeweils mit erlaubter/abgelehnter
  Entity-Registrierung: richtiges Zielsignal und Verbrauch nur bei Erfolg.
- Normale Blöcke und bereits gefüllte Rahmen bei beiden Regelwerten: `PASS`,
  kein Verbrauch und keine Rahmenmutation.
- Leerer Rahmen bei ausgeschalteter Regel: `FAIL`, keine Platzierung, kein
  Verbrauch. Mit eingeschalteter Regel normales Einsetzen bzw. wirksames
  `EntityChangeBlockEvent`-Veto.
- Portalreise bei beiden Regelwerten und Abweisung eines fremden Besitzers
  noch vor dem Lesen der Gamerule.

Diese Tests führen die echten `EnderEyeItem#use`, `useOn` und
`EndPortalBlock#portalAsync` aus; Welt, Struktursuche, Entity-Registrierung und
Pluginentscheidungen sind kontrollierte Testdoubles. Sie sind kein zusätzlicher
End-to-End-Clienttest der Stronghold-Suche. Die Projektillaufzeit selbst wird
nicht verändert. Bereits bestehende native Portal-/FallingBlock-Tests bleiben
separate Nachweise und werden nicht als Enderaugen-Clientabnahme ausgegeben.
Zusätzlich wurde die vom abschließenden Testserver aus der finalen JAR entpackte
Klasse mit `javap` geprüft: Der Wurfpfad enthält Struktursuche, Zielsignal und
Verbrauch ohne Gamerule-Abbruch; im Rahmenpfad ist die Gamerule-Sperre enthalten.

### Native Standardziel-Flüge auf der finalen JAR

**20 Fälle bestanden:** Sand, roter Sand, Kies, weißes Betonpulver und Amboss,
jeweils mit vier kardinalen Quellgeschwindigkeiten. Echte Portalberührung und
Landung in zwei entfernten Ursprungsregionen, keine Umleitung des Portalziels,
keine manuell gesetzte `onGround`-Markierung und keine nachträgliche
Geschwindigkeitsänderung. Für jeden Fall wurde überprüft:

- Ankunft `(100.5, 50, 0.5)`, Obsidianblock Y=48, freie Luft Y=49.
- Originaler Bukkit-Wrapper, zuständiger Entity-Besitzer und exakt die native
  Rotation der bereits vorhandenen Geschwindigkeit.
- Tatsächlicher Flug über die Plattform hinaus: etwa 8,31 Blöcke bis zur letzten
  Scheduler-Messung; der anschließende native Drop liegt etwa 9,15 Blöcke vom
  Startpunkt entfernt. Diese Entfernungen gelten für die Testgeschwindigkeit,
  nicht pauschal für jede Farm.
- Drop an einem unterstützten Wandbutton, genau ein Item des richtigen Materials
  und tatsächlich genau ein Item im Hopper. Keine Zielblock-Platzierung, kein
  synthetisches Pickup-Event und keine übrig gebliebene markierte Entity.
- Genau ein Portaltransfer, eine Ankunft, ein Quellblock, ein Drop und ein Pickup.

Rohdaten: `build/end-portal-flight-true-1790716043103/result.json`,
`end-portal-flight-checks.txt` und `runner.log` in diesem Fixture-Verzeichnis.

### Weitere Regressionen und Dokumentation

- Die vollständigen **68 bisherigen Portal-Fälle** auf derselben finalen JAR
  erneut bestanden: jeweils 34 mit deaktivierter/aktivierter Option, einschließlich
  beider Transferrichtungen, Veto, Umleitung, Listener-Entfernung, Platzierung und
  Item-Drop. Die durch Plugins gesetzten abweichenden Zielkoordinaten funktionieren
  weiterhin. Keine Cross-Region-/Thread-Ausnahme oder zurückbleibende markierte
  FallingBlock-Entity. Rohdaten:
  `build/end-portal-dupe-false-1790716039179/result.json` und
  `build/end-portal-dupe-true-1790716127179/result.json`.
- Runtime-Welt-Lifecycle auf derselben finalen JAR: **PASS**, 298 Prüfschritte
  mit zwei Regions-Threads. Ohne verbundenen Spieler; der optionale interaktive
  Spielerpfad wurde ausdrücklich übersprungen. Rohdaten:
  `build/paper-beta-smoke-full-1790716041085/result.json`.
- Buildwerkzeug-Selbsttests: **22 bestanden**.
- Dokumentations-Validator-Selbsttests: **57 bestanden**; API-Referenz mit
  19 Artikeln und 49 Links gültig; **9 vollständige Java-Beispiele kompiliert**.
- Neues Release-Frontmatter und lokale Dokumentationslinks geprüft. Der globale
  historische Changelog-Scan scheitert weiterhin an unquotierten Strings in
  `docs/builds/26.2/0.0.1.md`; dieser vorbestehende historische Eintrag wurde
  nicht im Gameplay-Auftrag geändert. Der neue 014-Eintrag besteht die Prüfung.

## Wiederholbare native Prüfungen

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel --no-configuration-cache
.\gradlew.bat :test-plugin:jar
$env:TESSERA_SMOKE_MODE = 'end-portal-flight'
node smoke-tests/end-portal-duplication/run.mjs "$env:JAVA_HOME/bin/java.exe" build/libs/tessera-server-26.3.build.014-beta.jar
$env:TESSERA_SMOKE_MODE = 'end-portal-duplication'
node smoke-tests/end-portal-duplication/run.mjs "$env:JAVA_HOME/bin/java.exe" build/libs/tessera-server-26.3.build.014-beta.jar
Remove-Item Env:TESSERA_SMOKE_MODE
```

`JAVA_HOME` muss auf JDK 25 zeigen. Linux/macOS verwenden `./gradlew` und die
entsprechende Shell-Syntax. Alle Serverfixtures liegen in eigens markierten
Verzeichnissen unter `build/`, nutzen freie Loopback-Ports und keine produktiven
Welten. Aufbau und Assertions: [Fixture-Dokumentation](../smoke-tests/end-portal-duplication/README.md).

## Betrieb und Grenzen

Für die zusätzliche Quellkopie bleibt in `config/paper-global.yml` dieselbe
vorhandene Einstellung erforderlich:

```yaml
unsupported-settings:
  allow-unsafe-end-portal-teleportation: true
```

Server zum JAR-Wechsel vollständig stoppen und neu starten. Keine neue
Flugrichtungs- oder Drop-Option erforderlich. Ein Block ohne ausreichende
horizontale Anfangsbewegung darf auch nach dieser Korrektur normal auf der
Plattform landen; Tessera erfindet keinen Schub in eine Himmelsrichtung.

Die Tests verwenden echte native Portal-Kollisionen, Buttons und Hopper,
geben aber die Anfangsgeschwindigkeit durch das Testplugin vor. Sie sind kein
Nachweis identischer Piston-/Slime-/Honey-Taktung einer konkreten Spielerfarm,
kein visueller Clienttest und kein Durchsatzbenchmark. Externe Gameplay-Plugins
und MCC/MVE werden in diesem Auftrag nicht gemeinsam getestet. Die allgemeinen
[Grenzen der optionalen Duplizierung](BUILD-26.3-013.md#plugin-verhalten-und-grenzen)
bleiben bestehen.
