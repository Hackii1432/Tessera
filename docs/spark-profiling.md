# Native Spark-Profilierung

Ab Tessera `26.3-020-beta` ist der bereits gebündelte Spark-Kern `1.10.191`
über einen internen Folia-/Tessera-Adapter nutzbar. Ein zusätzliches Spark-Plugin
ist dafür nicht erforderlich. Java 25 und die üblichen Folia-kompatiblen Plugins
bleiben Voraussetzung.

## Aktivierung und Berechtigung

In `config/paper-global.yml` gelten die bestehenden Einstellungen:

```yaml
spark:
  enabled: true
  enable-immediately: false
```

Nach einem normalen Neustart ist `/spark` verfügbar. OPs dürfen die Befehle
standardmäßig verwenden; für andere Spieler können die Spark-Rechte, etwa
`spark` beziehungsweise einzelne Spark-Unterrechte, vergeben werden. Die
Integration umgeht die Berechtigungsprüfung des Spark-Kerns nicht.

Ein bereits installiertes Spark-Plugin wird standardmäßig bevorzugt; der native
Profiler startet dann nicht zusätzlich. Ein externes Plugin muss selbst Folia
unterstützen. Die geerbte JVM-Property `paper.preferSparkPlugin` wird wieder
ausgewertet. Nicht mit `-Dpaper.preferSparkPlugin=false` gleichzeitig ein zweites
Spark-Plugin erzwingen. `spark.enabled: false` deaktiviert nur den gebündelten
Profiler, nicht ein separat installiertes Plugin.

## Paket- und Verbindungsverzögerungen untersuchen

Für Netty-, Kompressions-, Verschlüsselungs- und Regionsarbeit alle Threads
getrennt aufzeichnen:

```text
/spark profiler start --thread * --not-combined
/spark profiler stop --save-to-file
```

Die erste Zeile startet die Aufnahme; die zweite beendet und speichert sie
lokal unter `plugins/spark/`. Während der Aufnahme das Problem möglichst
reproduzieren. `--thread *` ist wichtig: Ohne diesen Parameter erfasst der
Tessera-Standard die dynamischen `Folia Region Scheduler Thread #…`-Worker
und den Startthread `Server thread`, nicht automatisch die Netty-Threads.

Unter Windows oder bei fehlendem nativen Sampler kann der Java-Sampler
ausdrücklich gewählt werden:

```text
/spark profiler start --force-java-sampler --thread * --not-combined
```

Eine begrenzte Aufnahme mit automatischem Ende ist ebenfalls möglich:

```text
/spark profiler start --thread * --not-combined --timeout 120 --save-to-file
```

`/spark profiler stop` ohne `--save-to-file` kann das Profil an Sparks externen
Viewer-Dienst übertragen. Profil-Dateien können Threadnamen, Plugininformationen,
Serverkonfiguration und Angaben zum auslösenden Spieler enthalten. Vor einer
Weitergabe prüfen; ein öffentlicher Upload ist für die lokale Aufnahme nicht nötig.

## Bedeutung der Messwerte

Ein Worker ist **keine feste Region**. Er kann nacheinander unterschiedliche
Regionen und globale Aufgaben bearbeiten. Ein Stackprofil mit vier Workern
bedeutet daher nicht vier dauerhaft diesen Threads zugeordnete Regionen.

Spark erhält TPS für echte Zeitfenster von **5 s, 10 s, 1 min, 5 min und
15 min**. Die einzelne TPS-Zahl ist das Minimum der gemessenen aktiven Regionen,
begrenzt durch die Ziel-Tickrate. Sie ist weder ein Mittelwert noch eine
globale Minecraft-Tickrate. Eine neue Region ohne Messungen wird ignoriert;
ohne messbare Regionen wird kein künstlicher 20-TPS-Wert erzeugt.
In diesem Fall bedeutet Spark-TPS `0` fehlende Regionsmessungen, nicht einen
Nachweis für eine blockierte globale Wartung.

Im Profil enthalten die zusätzlichen Plattformdaten `tessera-regions` die
Regions-ID, Dimension, publizierte Spieler-/Entity-/Chunk-Zähler und je
Zeitfenster TPS sowie MSPT-Mittelwert, Minimum, Median, Maximum und Utilisation.
Die globale Wartung wird separat ausgewiesen. Die Daten sind ungefähr eine
Sekunde gecachte Momentaufnahmen; Splits, Merges, Transfers und Unloads können
zwischen zwei Abfragen stattfinden. Es werden keine Regionsobjekte dauerhaft
im Cache gehalten und keine zusätzlichen Chunks geladen.
Junge beziehungsweise neu geschnittene Regionen haben zunächst nur eine
kürzere Messhistorie als das angefragte maximale Zeitfenster.
Utilisation ist dabei ein Faktor pro Tick-Worker (`1.0` entspricht `100 %`),
nicht die CPU-Auslastung des gesamten Prozesses.

Während `/tick freeze` laufen Profiler, Verbindungspflege, globale Koordination
und interne Besitzer-Aufgaben weiter; Gameplay wird dadurch nicht freigegeben.
Scheduler-TPS messen auch diese Wartungstakte und sind **kein Beleg für
fortlaufende Spielsimulation**. Der Freeze-Zustand steht in den Metadaten.
Spieler bleiben entsprechend Vanilla vom normalen Entity-Freeze ausgenommen.
Auch ein geparkter `Server thread` oder ein wartender Netty-Selector ist allein
kein Lag-Beweis; Thread-Zeitanteile sind nicht automatisch CPU-Auslastung.

## Bewusste Grenzen

- Kein gemeinsamer Server-Tick-Hook: `tickmonitor`, `tpsmonitor`, `--tick` und
  `--only-ticks-over` werden mit einer englischen Erklärung abgelehnt.
- Keine erfundenen gepoolten MSPT-Perzentile. Spark meldet die globale
  Dauerstatistik als nicht unterstützt. Für die regionale Anzeige `/tps`,
  `/tps all` oder `/tps region <id>` verwenden.
- Papers Live-Enumeration fremder Chunk-Entity-Listen, Blockentity-Maps,
  Gamerules und Datapacks ist nicht aktiv. Entsprechende Spark-Weltdetails
  fehlen, statt als falsche Nullwerte dargestellt zu werden. Die genannten
  publizierten Regionszähler bleiben in den Metadaten verfügbar.
- Automatische Broadcasts an beliebige Spieler werden nicht ausgeführt,
  damit Spark deren Permission-Maps nicht nebenläufig liest. Der anfragende
  Spieler und die Konsole erhalten weiterhin ihre Antworten. Spielerantworten
  folgen dem aktuellen Besitzer, laufen auch bei Freeze und werden bei
  Disconnect/Retirement oder Profiler-Deaktivierung verworfen.
- Befehle unterstützen Spieler, Konsole und RCON; Commandblocks und andere
  Senderarten werden mit einer Diagnose abgelehnt. Berechtigungen werden im
  ursprünglichen Befehlskontext kopiert, nicht vom Spark-Arbeiter später aus
  einer fremden Permission-Map gelesen.
  RCON-Befehle können asynchron enden; bei zeitversetzten Antworten die
  Serverkonsole beziehungsweise die gespeicherte Profil-Datei prüfen.
- Stackprofile zeigen Rechenarbeit und Wartepfade, beweisen aber allein keine
  Paketverluste oder Routingprobleme zwischen Client und Server. Alle-Thread-
  Sampling hat zusätzlichen Aufwand; Aufnahmen zeitlich begrenzen.

Die interne Brücke verwendet bewusst die gebündelte Spark-Version. Ein Update
des Spark-Kerns braucht erneut die Adapter-, Berechtigungs-, Freeze-,
Transfer-, Profilformat- und Shutdown-Tests; es ist kein öffentlicher neuer
Tessera-API-Vertrag für Plugins.

Build und durchgeführte Abnahme: [Build 020](BUILD-26.3-020.md).
