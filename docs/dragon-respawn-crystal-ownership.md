# Drachen-Respawn: regionsichere Beschwörungskristalle

## Fehler und Ursache

Beim Abschluss einer Wiederbeschwörung konnte `DragonRespawnStage` einen
Endkristall entfernen, für den der aktuelle Regions-Thread nicht zuständig war.
Der Thread-Check meldete `Cannot remove entity off-main`; der Scheduler leitete
daraufhin einen Serverstopp ein.

Der Stacktrace allein bestätigt nicht, wodurch der Kristall seine Zuständigkeit
verlor. Der Codepfad erlaubte jedoch dimensionsübergreifend aufgelöste
`EntityReference`-Referenzen ohne anschließende Welt-/Eigentümerprüfung.
Zusätzlich können Spawn- und Explosions-Callbacks eine zuvor geprüfte
Entity-Referenz während derselben Abschlussphase ungültig machen.

## Änderung

- Jede Respawn-Phase prüft zuerst sämtliche aufgelösten Kristalle: gleiche
  `ServerLevel`-Instanz, zuständiger Regions-Thread und kein alter Handle aus
  einem Dimensionswechsel oder Chunk-Unload.
- Bei einem unzulässigen Kristall vor der Phase wird die laufende Beschwörung
  über den vorhandenen Abbruchpfad beendet. Dieser stellt das lokale Portal
  wieder her und setzt die Säulenkristalle zurück. Der fremde Kristall wird
  weder verändert noch entfernt. Das ist die bewusste Fehlerfallbehandlung;
  es wird nicht mit einer stillschweigend verkleinerten Kristallliste fortgefahren.
- Nach dem Drachen-Spawn und vor jedem abschließenden Kristalleffekt wird erneut
  geprüft. Nach der Explosion wird die Zuständigkeit vor `discard()` nochmals
  geprüft. Bereits übertragene oder entladene Handles bleiben unangetastet.
- Ist der Drache bereits gespawnt, wird dieser Abschluss nicht rückgängig
  gemacht. Nicht mehr zugängliche Kristalle werden nicht auf fremden Threads
  nachbearbeitet.
- Eine Warnung je Beschwörungsversuch nennt die Kampf-Dimension, Kristall-UUID
  und Entity-Dimension. Keine wiederholte Tick-Warnungsflut.
- Der reguläre lokale Ablauf behält Phasen, Zeiten, Strahlen, Explosionsradius,
  Explosionsmodus und Bukkit-Entfernungsursache bei. Durch die erste Explosion
  bereits zerstörte lokale Kristalle behalten ihre weiteren Vanilla-
  Abschluss-Explosionen; zerstört und transferiert werden getrennt behandelt.

Es gibt keine synchrone Weiterleitung auf den Global-Thread, keine wartenden
Cross-Region-Aufträge und keine Abschwächung der allgemeinen Thread-Checks.
Sinopia, Plugins und die öffentliche Bukkit/Paper-API werden nicht verändert.

## Patch und Build

Die Korrektur liegt in
`folia-server/minecraft-patches/features/0033-Guard-dragon-respawn-crystal-ownership.patch`;
die Regressionstests liegen in
`folia-server/paper-patches/features/0024-Test-dragon-respawn-crystal-ownership.patch`.
Beide gehören zusammen mit dieser
Dokumentation in den Tessera-Commit. Generierte Java-Arbeitsquellen werden nicht
als vollständige Minecraft-Quellen eingecheckt.

Mit JDK 25 im Repository-Hauptverzeichnis:

```powershell
.\gradlew.bat buildTessera
```

Dieser Task wendet die Patchschichten an, führt Tests aus und baut die ausführbare
Tessera-JAR unter `build/libs/`. Die neuen Patches müssen nicht einzeln mit
`git apply` angewendet werden. Vorhandene eigene Änderungen an generierten
Arbeitsquellen müssen wie bisher zuerst in Patches gesichert werden.

## Prüfung

Die automatisierten Regressionstests führen die echten Respawn-Phasen mit
kontrollierten Welt-, Kristall- und Eigentümer-Testdoubles aus. Sie prüfen den
Normalfall, fremde Dimensionen/Regionen, alte Transfer-/Unload-Handles,
Eigentumswechsel in Spawn-/Explosions-Callbacks, Kettenexplosionen, einmalige
Abbruchbehandlung und unabhängige Ausführung auf zwei Test-Threads. Die echte
`EntityReference`-Auflösung wird sowohl für gecachte Handles als auch für die
dimensionsübergreifende UUID-Suche geprüft.

Dies ersetzt keinen Laufzeittest mit echten Minecraft-Regionen. Auf einer
separaten Testwelt sind noch sinnvoll:

1. Normalen Drachen-Respawn mit vier Kristallen vollständig durchlaufen lassen.
2. Während der Beschwörung einen beteiligten Kristall per regionsicherem
   Test-Plugin in eine andere Dimension bzw. entfernte Region teleportieren.
   Erwartung: kontrollierter Abbruch, kein Serverstopp, kein fremder Zugriff.
3. Einen Transfer aus einem Spawn-/Explosions-Callback gezielt auslösen.
   Erwartung: kein `discard()` auf dem alten bzw. fremden Handle.
4. Nach einem Abbruch erneut regulär beschwören; parallel eine andere Region
   betreiben und auf Cross-Region-Ausnahmen prüfen.

### Lokale Prüfergebnisse

- JDK: Oracle JDK 25.0.3.
- Alle 21 neuen Regressionstestfälle erfolgreich, ohne Fehler oder übersprungene
  Fälle, einschließlich der echten `EntityReference`-Auflösung.
- Patch-Roundtrip über `buildTessera`: beide neuen Feature-Patches erfolgreich
  angewendet. Die erzeugten Java-Dateien stimmen mit den exportierten
  Arbeitsständen überein; beide generierten Git-Arbeitskopien sind sauber.
- Vollständiger Aufruf am 13.09.2026:
  `.\gradlew.bat buildTessera --offline --no-daemon --console=plain`:
  **BUILD SUCCESSFUL** in 9 min 7 s, einschließlich Patch-Anwendung, Tests,
  API-Build, Server-Build und `scanJarForBadCalls`.
- Server-Testberichte: 9.810 erfasste Testfälle, davon 87 übersprungen,
  keine Fehler. API-Testberichte: 529 erfasste Testfälle, davon 2 übersprungen,
  keine Fehler. Die 21 neuen Regressionstestfälle sind in der Server-Zahl enthalten.
- Neu gebautes Artefakt:
  `build/libs/tessera-server-26.3-rc-2.build.001-alpha.jar`.
- Der ursprüngliche sporadische Auslöser wurde nicht auf einem laufenden Server
  reproduziert; die oben beschriebenen Gameplay-Tests bleiben empfohlen.
