# Paper-/Moonrise-Integration vom 11. Oktober 2026

Tessera 26.3-019-beta integriert den geprüften Paper-main-Bereich von
`4728a906edb501c3749dfcc437a419094a95a2c7` bis
`99867f8f9207d3164696e469435a067330473743`: 19 Commits, davon ein reiner
Patch-Rebuild und eine bereits gleichwertig vorhandene DragonBattle-Korrektur.
Die lokale Ausgangsbasis ist `c159a4e973747dbe787648d569826124a7b3c308`
einschließlich der bereits vorhandenen Disconnect-Patches. Keine automatische
Root-Commit-/Push-Erstellung; der vollständige lokale Änderungsstand gehört dazu.

## Quellen und Zuordnung

| Commit | Paper-Änderung | Tessera-Umsetzung |
| --- | --- | --- |
| [bfb089d](https://github.com/PaperMC/Paper/commit/bfb089d29d2b59f963bb0f244082c42f792688f5) | Prevent shearing villager display items (#14356) | Übernommen in Sinopia: Villager-Displayitems werden nicht mit Mob-Ausrüstung geschoren; Zombie-Konvertierung beendet den ShowTradesToPlayer-Zustand. |
| [a4b87cf](https://github.com/PaperMC/Paper/commit/a4b87cf896e18419005ab172e8b1694ccc1abe7c) | Add PlayerPostEffects#update (#14355) | API und Adapter übernommen; bestehender Tessera-Owner-/Respawn-Wrapper bleibt erhalten. |
| [b3c6bcb](https://github.com/PaperMC/Paper/commit/b3c6bcb83c9fd6b6475e04be39cd5eb143eda797) | Sync latest Moonrise changes (#14367) | Vollständige fachliche native Moonrise-Differenz in Patch 0062, einschließlich Paletten, Worldgen/Noise, Puffer und Chunk-Ticket-Batching. |
| [65ba985](https://github.com/PaperMC/Paper/commit/65ba985571b2771d27eeac2d273ce43bb3b5d61a) | Allow unrestricting commands (#14358) | Unrestricted-Marker übernommen; das eigentliche Permission-Prädikat bleibt verbindlich. |
| [e8750e1](https://github.com/PaperMC/Paper/commit/e8750e1c009d1a4a932de104c97589032d139fb6) | [ci skip] Resolve Schrödinger's accessor in HumanEntity starvation API (#14364) | Javadoc-Korrektur übernommen. |
| [8097898](https://github.com/PaperMC/Paper/commit/8097898e6dd8be147477e234bd3fa2e59e217547) | Refresh learnable recipes on recipe changes (#14302) | Learnable-Rezepte werden bei jeder Publikation neu aufgebaut (0064). |
| [615e915](https://github.com/PaperMC/Paper/commit/615e915d6baa245f5a0ce0951ccf8ae444b6e336) | Document undefined tracker behavior in track/untrack events. (#14189) | Track-/Untrack-Dokumentation warnt ausdrücklich vor Teleport/Tracker-Mutation im Callback. |
| [16db242](https://github.com/PaperMC/Paper/commit/16db242251ae413fc21e978abbc881ca55610bed) | Add PersistentDataKey API (#14337) | Typisierter Schlüssel, PDC/View-Overloads und Bridge übernommen; Null-Eingaben werden vor Schlüsselkonvertierung geprüft. |
| [e63aca4](https://github.com/PaperMC/Paper/commit/e63aca43379de12f7419ccc3ca2497de87d861a9) | Add Entity#getWeapon (#13949) | Allgemeiner Entity-Getter; kopiertes Bukkit-Item, bestehende Owner-Prüfung. |
| [cc7b98a](https://github.com/PaperMC/Paper/commit/cc7b98af634be07c9b3581306c7b6af33caa3ff2) | Support Batch Recipe (Un)Registration Methods and Improvements (#13945) | Batch-APIs und native Recipe-Konvertierung übernommen, atomare Katalogpublikation und Owner-Resets in 0064/0051. |
| [21a495c](https://github.com/PaperMC/Paper/commit/21a495c94c6a623212202ab7badabcd2ad1523ff) | Expose gravity and air drag (#14196) | Standard-/effektive Schwerkraft und Luftwiderstand über ownergeprüfte Entity-Getter. |
| [d57def0](https://github.com/PaperMC/Paper/commit/d57def08de7ad021e4593a286f2737474af241e0) | [ci skip] Rebuild patches | Reiner Patch-Rebuild; der relevante AbstractArrow-Zugriff ist lokal bereits öffentlich, keine zweite Verhaltensänderung. |
| [e3e060d](https://github.com/PaperMC/Paper/commit/e3e060d74d4447752bc4cb038eb3f9aa2d51c4f7) | Correctly revert SulfurCube on cancelled explode event | Cancelled-Explosion setzt Unverwundbarkeit zurück; fehlende ExplosionData wird sicher behandelt. Tessera-Patch 0065 beendet alte Tick-/Zündungs-/Explosionsarbeit nach Entfernung oder Transfer durch Callbacks. |
| [46d4834](https://github.com/PaperMC/Paper/commit/46d4834e8e2ec4af9dcfa028076fb16dfb8a3911) | Fix deprecation warnings at startup caused by sun.misc.Unsafe usage (#14350) | Spark 1.10.191, JOML ohne Unsafe als Default und Native-Access-Manifest. Explizite JOML-Property bleibt respektiert. |
| [b2d29a4](https://github.com/PaperMC/Paper/commit/b2d29a4fa871443c98226275b56992fa2d5a42ed) | Sync latest Moonrise changes (#14370) | Moonrise-Follow-up vollständig in 0062 enthalten, einschließlich fehlerarmer Zufallstick-Zählung. |
| [821b95f](https://github.com/PaperMC/Paper/commit/821b95fb153b92a78c2a6e599d608383b8745e1f) | Fix hidden_components upgrade of replaced data components | Entsprechende typed DATA_COMPONENTS-Migrationen im aktiven Vanilla-DFU-Pfad 5007/5008/5014 statt Austausch der lokalen DataConverter-Brücke. |
| [1a6b058](https://github.com/PaperMC/Paper/commit/1a6b058db4f82abd38f2759efa529af4d66513d8) | Only set emptyCrystalRespawnAllowed if the fight actually started (fix #14254 regression) (#14333) | Bereits gleichwertig in Tessera: der leere Kristallzustand wird nur für den tatsächlichen Start verwendet; bestehender try/finally-Schutz bleibt. |
| [c3ec3ae](https://github.com/PaperMC/Paper/commit/c3ec3ae0c281658e4b8f994c05de92164e1b59ed) | Fix minimum block light in spawner spawn rules (#14371) | Spawner-API liest den richtigen minimalen Blocklichtwert. |
| [99867f8](https://github.com/PaperMC/Paper/commit/99867f8f9207d3164696e469435a067330473743) | Fix block state upgrading in old structure data | Typisierte STRUCTURE_FEATURE-Migration eingebetteter Blockstates; korrigierte V2701-Pool-Feature-Zuordnung. Keine beliebige Plugin-NBT-Umschreibung. |

## Folia-Anpassungen

- Moonrise wird als fachliche Differenz auf die vorhandenen Quellen portiert.
  Der historische Ganzpatch ersetzt nicht Tesseras Regionsmodell, Ticket-
  Ownership, WorldGen-Threadprüfung oder spätere Paletten-/NBT-Korrekturen.
- Zufalls-/Noise-Puffer sind worker-/threadlokal; Arenen und Cache-Lifecycle
  behalten ihre Freigabeprüfungen. Palette-Zustände und Biome bleiben Minecraft
  26.3-kompatibel; die Anti-Xray-Datenpfade werden passend mit angepasst.
- RecipeMap, Lookup-Daten, Stonecutter-Displays und Learnable-Cache bilden einen
  unveränderlich veröffentlichten Katalog. Eine Kopie wird validiert und komplett
  abgeleitet, bevor ein globaler Schreiber sie einmal veröffentlicht.
- Alle Laufzeit-Rezeptschreiber benutzen denselben Global-Thread-Vertrag. Kein
  neuer Regions-Writer und kein synchrones Warten auf fremde Regions-Threads.
  Alte Rezept-Iteratoren lesen einen Snapshot; ihr Remove mutiert den aktuellen
  Katalog über denselben globalen Schreiber.
- RecipeBook und Advancements werden nicht global mutiert. Interne Owner-Aufgaben
  folgen dem aktuellen Spieler, lösen nach Respawn dessen neues Handle auf und
  laufen auch bei Tick-Freeze. Disconnect/Retirement verhindert verspätete Arbeit.
- Ein Recipe-Paket und der folgende RecipeBook-Reset lesen denselben Katalog.
  Das gilt auch für den initialen Login und den nativen Spieler-Restore.
  Display-IDs entfernter Rezepte werden nicht für andere neue Rezepte recycelt;
  bereits wartende Client-Requests können kein fremdes Rezept auswählen.
- EndCrystal-/DragonBattle-, SulfurCube-, Portal-, Disconnect- und Runtime-World-
  Schutz aus den bestehenden Tessera-Patches bleiben erhalten.
- SulfurCube prüft zusätzlich nach Ignite-, Unleash-, Prime- und Explosions-
  Callbacks, ob dieselbe gültige native Instanz noch der ursprünglichen Welt
  und Region gehört. Entfernung oder Dimensionswechsel beendet die alte Arbeit
  vor weiteren Fuse-, Tick-, Explosions- oder Discard-Mutationen (0065).
- Der initiale Rezeptaufbau erlaubt den tatsächlichen Konstruktor-Thread nur
  vor Start des Server-Threads. Beliebige andere Threads erhalten damit keinen
  Bootstrap- oder Laufzeit-Schreibzugriff. Der normale Serverstart ist nativ
  geprüft; Runtime-Schreiber bleiben an den Global-Thread gebunden.

## Datenmigrationen und Abhängigkeiten

`hidden_components` wird bei der Ersetzung/Entfernung der betroffenen Komponenten
mitmigriert. Verborgene Swing-Animationen bleiben in den neuen Attack-/Interact-
Komponenten verborgen; entfernte Map-/Block-Transformer-Komponenten hinterlassen
keine veralteten Einträge. Unbetroffene Tooltip-Einstellungen werden nicht
überschrieben. Die Quell-NBT bleibt beim Test unverändert.

Alte Structure-Start-Daten und verschachtelte Jigsaw-Pool-Prozessoren erhalten
die 26.3-Blockstate-Felder an den dafür vorgesehenen Stellen. Der V2701-Walker
verarbeitet die Feature-Referenz des verschachtelten Elements, nicht die
übergeordnete Element-Typangabe. Die vorhandene MCDataConverter-/Vanilla-DFU-
Sequenz wird weiterverwendet, kein ungeprüfter Bibliothekswechsel.

Spark ist `1.10.191`; JOML nutzt standardmäßig `joml.nounsafe=true`, sofern nicht
explizit vorgegeben. Die ausführbare Server-JAR erlaubt ihre erforderliche Native-
Access-Nutzung im Manifest. Java 25, Minecraft/API 26.3, Mache, Gradle 9.8.0,
Paperweight beta24 und Leafpile 1.2.4 bleiben erhalten. `paperRef` benennt weiterhin
die ursprüngliche Sinopia-Importprovenienz, nicht den effektiven Backport-Stand.

## Dauerhafte Ablage

Öffentliche Paper-API-/CraftBukkit-Adapteränderungen und kleine Basis-Quellpatches
liegen direkt versioniert unter `sinopia/`. Größere native Änderungen stehen
in Minecraft-Feature-Patches 0062–0065. Server-Patches 0051/0052 sichern die
Folia-Adapter und Regressionstests; API-Patch 0015 dokumentiert den Folia-Vertrag.
Sie werden mit dem bestehenden Workflow wieder angewendet, nicht nur als
generierte Java-Dateien aufbewahrt.

Zusätzlich enthält die [Sinopia-Patchsicherung](../upstream-patches/sinopia/README.md)
alle 43 geänderten Basisdateien einschließlich öffentlicher APIs. Sie ist für
die ursprüngliche Sinopia-Basis geprüft und wird nicht doppelt auf den schon
integrierten Quellstand angewendet.

Die früheren Disconnect-Patches 0061/0050 bleiben Bestandteil dieses Builds.
Die API-Referenz beschreibt neue Signaturen, Owner-Kontexte und Plugin-Disable,
insbesondere [Rezepte](api/recipes.md), [PDC](api/persistent-data.md) und
[PlayerPostEffects](api/player-effects.md).

## Prüfungen und Grenzen

Die tatsächlich ausgeführten Build-/Server-/Protokollprüfungen und der finale
JAR-Hash stehen im [Buildbericht 019](BUILD-26.3-019.md).
Die [Release-Notizen](builds/26.3/0.0.19.md) enthalten den Updatehinweis für
Plugins, die Rezepte bisher von einem Regions-/Async-Thread mutiert haben.
Ein gemessener pauschaler Leistungsgewinn, eine visuelle Vanilla-Client-Abnahme
oder eine vollständige Prüfung aller produktiven Plugins wird nicht behauptet.

