# Schlafposition bei Beobachtern (26.3-rc-2)

## Anlass und Korrektur

Beim Schlafen im Straw Bed wurde der Spieler für andere Spieler versetzt
angezeigt; beim Aufstehen korrigierte sich die Darstellung. Der eigene Client
erhält bereits eine separate Spieler-Positionskorrektur.

Die 26.3-Client-Interpolation berücksichtigt Positionssprünge außerhalb ihrer
eigenen Bewegung. Die Schlaf-Metadaten lösen genau einen solchen Sprung aus.
Die bisherige Reihenfolge (Bewegung, anschließend Schlaf-Metadaten) kann dadurch
den Sprung zum Bett nochmals auf das Interpolationsziel aufschlagen.

Tessera behandelt Änderungen der Schlafposition eines getrackten Spielers jetzt
als zusammengehörigen Synchronisationsübergang:

- Beobachter erhalten ein Paket-Bundle: zuerst die aktuellen Entity-Metadaten,
  danach eine absolute Position mit dem aktuellen Endpunkt, ohne alte
  serverseitige Bewegungsschritte. Zwischen beiden Paketen läuft kein Client-Tick.
- Das betrifft Schlafbeginn, Wechsel der Schlafposition und Aufstehen.
- Die Positionsbasis des Trackers wird regulär aktualisiert. Nachfolgende
  relative Bewegungen verwenden weiterhin dieselbe Basis wie der Client.
- Der schlafende Spieler bekommt nur die Metadaten über diesen Zusatzpfad;
  seine vorhandene Spieler-Teleportbehandlung bleibt unverändert.
- Ein unveränderter Schlafzustand erzeugt keine wiederholten Zusatz-Bundles.
- Auch bereits verbrauchte Dirty-Metadaten werden beim Übergang durch einen
  vollständigen Metadatenstand abgesichert.

Der Zusatzpfad liest keine Bettblöcke oder fremden Chunks. Er läuft im bestehenden
Entity-Tracking auf dem zuständigen Regions-Thread, ohne neue Tasks, Wartezeiten
oder globale Spielersuche. Regeln für Schlafen, Spawnpunkte, Bettzerstörung und
Bukkit-Ereignisse werden nicht geändert. Normale Betten bleiben unterstützt.

## Patch und Build

In den Tessera-Commit gehören:

- `folia-server/minecraft-patches/features/0035-Synchronize-observer-sleep-transitions.patch`
- `folia-server/paper-patches/features/0025-Test-observer-sleep-synchronization.patch`
- Diese Dokumentation.

Sinopia und Plugins werden nicht verändert. Die generierten Java-Arbeitsquellen
selbst gehören nicht als vollständiger Minecraft-Code in den Tessera-Commit.

Im Repository-Hauptverzeichnis mit JDK 25:

```powershell
.\gradlew.bat buildTessera
```

Der Task wendet alle Patchschichten an, führt Tests aus und erstellt die
ausführbare JAR unter `build/libs/`. Die Patches müssen nicht einzeln per
`git apply` angewendet werden. Eigene Änderungen in generierten Arbeitsquellen
müssen vor dem erneuten Anwenden wie bisher in Patches gesichert sein.

## Prüfung und Grenzen

Die Regressionstests verwenden den echten `ServerEntity`-Tracker, echte
`SynchedEntityData`, die Vanilla-Schlafpositionsberechnung und die gemeinsame
26.3-Client-Interpolationsimplementierung. Welt und Netzwerkverbindungen sind
Testdoubles. Der Empfang der Bewegungs- und Metadatenpakete wird anhand des
lokal geprüften `ClientPacketListener` aus 26.3-rc-2 nachgebildet.

Ein erfolgreicher automatisierter Test ersetzt keinen visuellen Mehrspieler-Test.
Insbesondere andere Client-Versionen, Client-Mods oder Plugins, die Pakete
verändern, sind dadurch nicht mitgeprüft.

Auf einer separaten Testwelt bitte mit zwei unveränderten 26.3-rc-2-Clients prüfen:

1. Einen Spieler aus dem Stand und aus der Bewegung ein Straw Bed benutzen
   lassen; der zweite beobachtet die gesamte Schlafdauer und das Aufstehen.
2. Mit einem normalen Bett wiederholen, jeweils in allen vier Ausrichtungen.
3. Während des Schlafens den Beobachter neu beitreten bzw. den Tracking-Bereich
   verlassen und erneut betreten lassen; anschließend aufstehen und laufen.
4. Zwei getrennte Regionen gleichzeitig testen; außerdem in der anderen
   Dimension erneut ein Bett benutzen. Keine Cross-Region-Ausnahme und kein
   Übertragen alter Schlafdaten auf andere Spieler oder Welten.

Die normale kurze Bewegungsinterpolation des Clients wird nicht grundsätzlich
abgeschaltet. Ziel ist die Beseitigung des falschen, bleibenden Positionsversatzes.

### Automatisierte Prüfergebnisse

- JDK 25.0.3; alle 13 neuen Testfälle erfolgreich, keine übersprungenen Fälle.
- Vier Kontrollfälle spielen die ursprüngliche Reihenfolge Bewegung → Metadaten
  nach und weisen mit der echten Interpolation den doppelten Positionssprung
  nach (beide Bettarten, mit und ohne bereits laufende Interpolation).
- Die korrigierte Übertragung hält in denselben vier Situationen die jeweilige
  Vanilla-Schlafposition. Die übrigen Fälle prüfen Paket-Bündelung und Empfänger,
  fehlende Dirty-Markierungen, neue Beobachter, ausbleibende Dauer-Wiederholung,
  Aufstehen und nachfolgende relative Bewegung.
- Die gesamte `AllFeaturesTestSuite` läuft mit der Änderung erfolgreich durch.
- Beide Feature-Patches wurden über `buildTessera` erneut konfliktfrei angewendet;
  die Git-Inhaltshashes der erzeugten Java-Dateien stimmen exakt mit den
  exportierten Patches überein. Beide generierten Arbeitskopien sind sauber.
- Vollständiger Build am 14.09.2026:
  `.\gradlew.bat buildTessera --offline --no-daemon --console=plain`:
  **BUILD SUCCESSFUL** in 9 min 18 s, einschließlich `scanJarForBadCalls`.
- Server-Berichte: 9.823 erfasste Testfälle, 87 übersprungen, keine Fehler.
  API-Berichte: 529 erfasste Testfälle, 2 übersprungen, keine Fehler.
  Die 13 neuen Testfälle sind in der Server-Zahl enthalten.
- Startbares Artefakt: `build/libs/tessera-server-26.3-rc-2.build.001-alpha.jar`.
  Der neu erzeugte Server-Code wurde zusätzlich auf den enthaltenen
  Synchronisierungs-Pfad geprüft.
- Ein visueller Test auf einem laufenden Server mit zwei echten Clients wurde
  hier nicht durchgeführt und bleibt die abschließende Gameplay-Prüfung.
