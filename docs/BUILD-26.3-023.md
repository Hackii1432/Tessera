# Build 023 – regionsichere Allianzen, Mob-Effekte und KI-Ziele

Stand: 26.10.2026. Tessera `26.3-023-beta`, Minecraft/API `26.3`, Java 25.

## Quellbasis und Umfang

Ausgangscommit auf `ver/26.3.x`:
`5731a919dbc85aa6a39f3f11b7cf1d089ef81f64`.
Der Build benötigt die zusätzlichen Arbeitsbaumänderungen und Patchdateien
dieses Auftrags. Der Ausgangscommit allein enthält die Korrektur nicht.
Kein automatischer Commit oder Push im Tessera-Hauptrepository.

Die Änderung schließt die bei Build 022 gefundene Anpassungslücke zwischen
Papers Allianz-API und Folias Regionszuständigkeit. Sie ist keine neue
Upstream-Migration. MCC-/MVE-Produktcode und Serverkonfiguration bleiben
unverändert; Minecraft/API 26.3, Java 25 und Kanal beta bleiben erhalten.

## Dauerhafte Patchsicherung

- [Minecraft 0071](../folia-server/minecraft-patches/features/0071-Guard-native-alliances-area-effects-and-cached-AI-ta.patch)
  sichert 19 native Quelldateien: Allianz- und Besitzerauflösung, Flächeneffekte,
  gespeicherte Ziele, Gruppenalarm und Fortsetzung nach Callbacks.
- [Server 0064](../folia-server/paper-patches/features/0064-Revalidate-alliance-callbacks-and-test-region-safe-n.patch)
  sichert die revalidierte Bukkit-Regelauswertung und die Java-Regressionstests.
- Die zusätzliche Änderung in Server-Patch 0001 betrifft ausschließlich die
  neu berechneten Git-Index-Hashes des CraftEventFactory-Diffs, keine neue Logik.

Der Rebuild erzeugt insgesamt 71 Minecraft- und 64 Server-Feature-Patches.
Der vollständige Build wendet die Patchschichten erneut an. Es genügt nicht,
nur die generierten Quellen zu behalten; die hier verlinkten Patches und die
versionierte Basis sind die dauerhafte Grundlage. Sinopia selbst wurde in
diesem Auftrag nicht um einen weiteren Upstream-Stand erweitert.

## Geschlossene Zugriffs- und Callback-Lücken

Warden-Dunkelheit und die Mining-Fatigue-Aura Ältester Wächter verwenden eine
lokale Spielerliste. Ownership wird vor Spielmodus-/Effektzuständen geprüft,
die Reichweite vor der Allianzprüfung. Die Vanilla-Werte bleiben unverändert:
Warden-Radius 20, Darkness 260 Ticks; Guardian-Radius 50, Fatigue 6000 Ticks mit
Amplifier 2. Die ursprüngliche Auswahlphase mit Appearance-Events liegt
weiterhin vor der anschließenden Effektanwendung.

Die neue interne Besitzerauflösung prüft einen gecachten Handle zuerst auf
Zuständigkeit, bevor sie dessen Lebenszykluszustand liest. Ein lokaler Cache
funktioniert auch vor Eintragung in den Entity-Index; die Alternative ist eine
UUID-Abfrage im vorhandenen Index derselben Welt, kein Chunk-Load und keine
synchrone Übergabe an eine andere Region. Die allgemeine EntityReference-
Auflösung für andere Systeme wie Enderperlen/Restore bleibt unverändert.

Gezähmte Tiere und Vex-Besitzerketten reichen deshalb keine lebenden
Allianz-/Teamprüfungen an entfernte Besitzer weiter. Die UUID-Bindung wird
nicht gelöscht. Fremde Plugin-Prädikate lassen sich nicht synchron sicher
auswerten; diese stehen erst wieder bei passender lokaler Zuständigkeit zur
Verfügung. Fangs verwenden die normale Folia-Verfügbarkeit eines lokalen
Projektilbesitzers und behalten die gespeicherte UUID, Besitzer-Schutz und
ihren Magieschaden. Ein vorübergehendes `getOwner() == null` löscht keine Bindung.

TargetingConditions, Vergeltungsziele, Creaking-Erinnerungen und Warden-Wut
prüfen gespeicherte Referenzen vor Zustandszugriffen erneut. Vorübergehend
entfernte Warden-Suspects werden als UUID mit weiter abklingender Wut gehalten,
statt als fremder Live-Handle weiter ausgewertet zu werden. Normal getötete
lokale Suspects werden weiterhin verworfen.

Zusätzliche Guards liegen nach Allianz-/Potion-/Appearance-/Push-/Knockback-
Callbacks, im Schwert-Sweep und Keulenrückstoß sowie im Gruppenalarm. Auch die
Aufrufer stoppen: Creaking schreibt nach Verlust seines Besitzers keine
Bewegungsdaten/Game-Events weiter, und ein Gruppenalarm setzt auf dem alten
Besitzer keinen Goal-Zustand zurück. Die öffentlichen API-Ownership-Prüfungen
bleiben aktiv. Regeln werden pro Auswertung kopiert, damit eine reentrante
Änderung der Regel-Map keine ConcurrentModificationException erzeugt.

Die [API-Regeln](api/paper-api-022.md#entity-allianzen-und-schaden),
[Fangs-Verfügbarkeit](api/entity-and-tree-compatibility.md#evoker-fangs-und-verfügbare-besitzer)
und [Event-Grenzen](api/events.md#mob-effekte-und-rückstoß) beschreiben die
öffentlichen Auswirkungen; die internen NMS-Helfer sind keine neue Plugin-API.

## Java-Tests und vollständiger Build

Der [gezielte Lauf](test-evidence/26.3-023/targeted-tests.log) besteht mit
**82 Fällen**, ohne Fehler oder Skips:

- 19 neue Allianz-/Ownership-/Callback-/Spawnwarnungsprüfungen.
- 3 bestehende Checks der öffentlichen Entity-API-Guards.
- Alle 60 vorhandenen Wolf-Besitzer-Kampfprüfungen aus der bisherigen Korrektur.

Die [Ergebnisse](test-evidence/26.3-023/targeted/) behalten ihre Assertions.
Ein Vorlauf scheiterte in zwei neuen Spawnwarnungs-Testfixtures daran, dass
ServerPlayer einen konkreten CraftPlayer statt eines generischen CraftEntity
zurückgibt. Der Mock-Wrappertyp wurde korrigiert, nicht die Erwartung an die
Regionssicherheit. Frühere Adapter-/Testplugin-Kompilierfehler wurden vor dem
erfolgreichen Abschluss ebenfalls behoben.

Der [vollständige `buildTessera`](test-evidence/26.3-023/buildTessera.log)
besteht nach erneuter Patch-Anwendung in **9 min 46 s**, einschließlich Javadoc,
Checkstyle und Bad-Call-Prüfungen.

| Modul | Fälle | Bestanden | Übersprungen | Failures / Errors |
| --- | ---: | ---: | ---: | ---: |
| Server | 10.453 | 10.366 | 87 | 0 / 0 |
| API | 529 | 527 | 2 | 0 / 0 |
| Checkstyle-Modul | 3 | 3, unveränderter Gradle-Cache | 0 | 0 / 0 |
| Gesamt | 10.985 | 10.896 | 89 | 0 / 0 |

Die 19 neuen Fälle bestehen auch im vollständigen, ungefilterten Build.
Ausgewählte [vollständige XML-Ergebnisse](test-evidence/26.3-023/full-build/)
liegen getrennt vom gezielten Lauf. Die API-Referenz umfasst 23 Artikel und
75 lokale Links; 57 Validator-Tests bestehen. Alle 15 vollständigen Java-
Beispiele [kompilieren gegen die 023-API](test-evidence/26.3-023/api-examples.log).

## Native Abnahme auf der ausführbaren JAR

Alle folgenden Nachweise beziehen sich auf dieselbe finale JAR mit dem unten
genannten SHA-256, eigene Loopback-Ports und Java 25. Es wurden ausschließlich
frische, markierte Testwelten verwendet, keine produktiven Welten/Saves.

Der [erweiterte Mob-/Besitzerlauf](test-evidence/26.3-023/native-alliance.json)
besteht mit **171 Einzelprüfungen**, zwei Logins, null Kicks/Disconnects bis PASS
und regulärem Stop mit Exit 0. Wiederholte Callback-Checks sind nicht ebenso
viele unterschiedliche Spielszenarien.

- Zwei tatsächliche, getrennte Regionsbesitzer mit verbundenen Protokollclients.
- Echte Warden-/ElderGuardian-AI-Ticks in beiden Regionen: Darkness und Fatigue
  werden angewendet, Amplifier bleibt 2 und alle vier Aura-Mobs bleiben gültig.
  Für die Aura-Phase wurde tickCount unmittelbar vor das nächste Vanilla-
  Intervall gesetzt; danach läuft der normale Scheduler, kein Ersatz-Logger.
- Wölfe, Katzen, Papageien und Nautilusse behalten Besitzer-UUIDs über echte
  Regions-/Dimensionswechsel. Nach der Rückkehr funktioniert die Besitzerallianz.
- Ein tatsächlicher Beschwörer wechselt Region und Dimension und kehrt zurück;
  Vex-Rootowner und Evoker-Allianz bleiben sicher und die Bindung wird wieder
  verfügbar. Auch die gespeicherte Warden-Wut löst den zurückgekehrten Spieler
  wieder auf.
- Kontrollierte native Prüfungen veralteter Warden-/Creaking-/Vergeltungsziele,
  Fangs-Schaden von genau 6 bei entferntem Besitzer und erhaltene Fangs-UUID.
- Reale native Sweep-/Keulen- und Gruppenalarm-Aufrufe erhalten lokale Wirkung;
  ein Piglin-Callback entfernt den Ausgangsmob ohne weitere Gruppenmutation.
- Tatsächliche Appearance-Vetos und Potion-/Allianz-/Push-Callbacks, die
  Beteiligte entfernen. Ein kontrollierter Creaking-aiStep schreibt nach seiner
  Entfernung keine CAN_MOVE-Daten weiter.

Die letzten kontrollierten Pfade rufen echte native Methoden teilweise direkt
oder reflektiv auf, statt einen menschlichen Mausklick zu simulieren. Auren und
ihre Mobs werden dagegen über normale AI-Ticks geprüft. Die Clients sprechen
das reale Protokoll, rendern aber keine Vanilla-Welt.

Die bestehenden [Navigationsprüfungen](test-evidence/26.3-023/native-navigation.json)
bestehen auf derselben finalen JAR mit **107 Einzelprüfungen**, zwei Logins und
null Kick/Disconnect bis PASS, Exit 0. Gerittene Pferde, Ausrüstung, Besitzer,
UUIDs und Regions-/Dimensionswechsel bleiben erhalten.

Der [native Online-Restore-Rückwärtstest](test-evidence/26.3-023/native-restore.json)
besteht ebenfalls auf derselben finalen JAR: öffentlicher Vertrag 1, echte
angenommene Transaktion, wiederholte Zustandswiederherstellung und Rollback.
Beide Clients behalten jeweils einen Login und null Kick/Disconnect; beobachtet
werden 37 bzw. 35 Positions-/Teleportpakete, je acht Null-Stat-Resets und zwölf
Advancement-Resets. Regulärer Stop, Exit 0. Dies ist weiterhin keine Prüfung
des vollständigen MCC-/MVE-Pluginstacks.

## Ausführbare Datei und Wiederholung

Datei: `build/libs/tessera-server-26.3.build.023-beta.jar`.
Größe: **56.728.942 Bytes**.

```text
SHA-256 26702a8332a2ac6681abce5ce8cab86a6d08006397105e6ee90d7d9db0a43b83
```

Saubere generierte Quellrepositories nach erneuter Patch-Anwendung:

- Minecraft: `61e686884e7ef35878e1b84f4b37139576d59037`.
- Server: `2a7125ec81d5631b10b3a855a1769692c0782f87`.
- API: `9251e8570e84797c3901838723267f0f5aef5a4d`.

Dies sind generierte Quellköpfe, keine neuen Root-Commits. Die
[maschinenlesbare Zusammenfassung](test-evidence/26.3-023/build-summary.json)
verknüpft Quellbasis, Patch-Hashes, Tests und JAR-Hash.

```powershell
.\gradlew.bat buildTessera --console=plain --max-workers=2 --no-parallel
.\gradlew.bat :test-plugin:jar --console=plain --max-workers=2 --no-parallel
node smoke-tests/alliance-ownership/run.mjs "C:\Program Files\Java\jdk-25.0.3\bin\java.exe" build/libs/tessera-server-26.3.build.023-beta.jar
```

Das Testplugin wird separat gebaut und nur in die isolierten Fixtures kopiert.
Es ist kein Bestandteil der produktiven Server-JAR. Zusätzliche Produktiv-
Plugins oder JVM-Pflichtoptionen werden nicht eingeführt.

## Grenzen und Update

Die vollständige MCC-/MVE-/TAB-/LuckPerms-Pluginintegration und lange
Produktionslast wurden in diesem Auftrag nicht erneut getestet. Native
Prüfungen sind kein Freibrief für beliebige fremde Plugin-Hauptthreadannahmen.
Ein gerenderter Vanilla-/Konsolenclient und eine Behebung sporadischer
Internetlatenz werden nicht behauptet. Auch der globale historische Changelog-
Validator bleibt von der bekannten unveränderten 26.2-YAML-Datei getrennt;
die neue Release-Datei und ihre Links werden gesondert geprüft.

Vor dem Update Welten und Plugin-Daten sichern, regulär stoppen, JAR ersetzen
und neu starten. Bereits durch den alten Fehler verworfene Mobs werden nicht
nachträglich wiederhergestellt.

[Changelog 0.0.23](builds/26.3/0.0.23.md)
