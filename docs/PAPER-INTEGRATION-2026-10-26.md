# Paper-/Moonrise-Integration vom 26. Oktober 2026

Tessera `26.3-022-beta` integriert die vollständige funktionale Empfehlung nach
`99867f8f9207d3164696e469435a067330473743` bis zum geprüften Paper-main-Kopf
`e415f5e030435d3108d7b623b41b9e47901e9d12` vom 25. Oktober 2026.
Der Vergleich umfasst 54 Commits. Ausgangspunkt ist Tessera-Commit
`c1f9128c388cea89c3ef479dff6091fe780c685b` auf `ver/26.3.x` plus die hier
versionierten Änderungen. Es wurde kein Root-Commit oder Push automatisch erstellt.
`paperRef` bleibt die ursprüngliche Importprovenienz, nicht eine vorgetäuschte
vollständige Ersetzung der gewachsenen Folia-/Sinopia-Basis.

## Commitzuordnung

### Laufzeitkorrekturen

| Commit | Originaltitel | Umsetzung |
| --- | --- | --- |
| [e415f5e](https://github.com/PaperMC/Paper/commit/e415f5e030435d3108d7b623b41b9e47901e9d12) | Don't refresh entity data if its removed (#14423) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [e3ba7b1](https://github.com/PaperMC/Paper/commit/e3ba7b1dc801e0c385051c34781ab30b226abee3) | Regen missing worldgen settings file from server.properties (#14233) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [05c2798](https://github.com/PaperMC/Paper/commit/05c2798ec061ebfc61d27954923dce49d1b33d5a) | Fix cancelled running scheduled task state (#14109) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [02378ab](https://github.com/PaperMC/Paper/commit/02378ab5827b3500e3876fdbdd129dc9483fb4ab) | Fire PrepareItemCraftEvent for Crafter preview (#14320) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [325f907](https://github.com/PaperMC/Paper/commit/325f9071f089ffcd06c928fe5afdc401f285d816) | Clear scoreboard team entries on unregister (#14159) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [d86d427](https://github.com/PaperMC/Paper/commit/d86d427405093f92e4097bd611d4dd447238b2f5) | Call PlayerBucketEmptyEvent for Sulfur Cube in Bucket (#14150) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [03af8b4](https://github.com/PaperMC/Paper/commit/03af8b42915773171808f20fe01b763f847a0d7d) | Fix shutdown thread deadlock if it tries to close async (#14410) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [a90108f](https://github.com/PaperMC/Paper/commit/a90108f43cabdf058548f9b27b9319bf7842c49d) | Prevent negative max stack sizes in inventories (#14407) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [3bfa02f](https://github.com/PaperMC/Paper/commit/3bfa02fd65bc5171848e974d0a188f0b4e1b898e) | Fix death with zero health in non-ticking Armorstands (#14332) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [4091869](https://github.com/PaperMC/Paper/commit/4091869eedbacb42c806fd670974ecfcb18d721f) | Fix repeated cookie requests for the same key (#14245) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [683e60c](https://github.com/PaperMC/Paper/commit/683e60c0923ddafd642c43997f9d023655e96f99) | Fix datapack GUIs relying on synchronous container opening (#14084) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [3988767](https://github.com/PaperMC/Paper/commit/39887673fbf5a8a9de81050359f5f86771d617d8) | Move cook speed multiplier application to getTotalCookTime (#14401) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [be4b895](https://github.com/PaperMC/Paper/commit/be4b895635c1989275569ef6d376bc9f88ed0217) | Use vanilla recipe order for recipe matching (#14389) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [a48059a](https://github.com/PaperMC/Paper/commit/a48059a2d48dea06523f42ffeba543286c7d505e) | Correctly serialise decorated pod decorations (#14388) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [56cab99](https://github.com/PaperMC/Paper/commit/56cab99fbfc8dc90513869144a3dfac222de19b4) | Mitigate jline shutdown race (#14382) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [a2d844f](https://github.com/PaperMC/Paper/commit/a2d844f71e275a91656448d5a0eb1142359230b9) | Fix player removal from maps (#14328) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [6829d9e](https://github.com/PaperMC/Paper/commit/6829d9e6cb4f09ce5f34035f774e2b4b19f164dd) | Fix fire immune entities being lightning immune (#14357) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [fbec96a](https://github.com/PaperMC/Paper/commit/fbec96a8fadeb9fbff36b903ec44907576115d17) | Improve EntityAirChangeEvent calls (#14155) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [abe55c5](https://github.com/PaperMC/Paper/commit/abe55c52423d3bacc32fdbbe10184ffdf8d6af9e) | Reset client world load status on respawn/cross-dimension teleport (#14191) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |

### Moonrise und weiterer Unterbau

| Commit | Originaltitel | Umsetzung |
| --- | --- | --- |
| [45fac22](https://github.com/PaperMC/Paper/commit/45fac22871a08d5b6496518cf183b574a6b16b79) | Sync latest Moonrise changes (#14409) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [dd585fd](https://github.com/PaperMC/Paper/commit/dd585fda4d49fb09ec824b1467dbdea1ef242c56) | Sync latest Moonrise changes (#14408) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [1b443e0](https://github.com/PaperMC/Paper/commit/1b443e0ac8193f8f90e10c117802a325e64d251b) | Explicitly add remaining anti-xray preset values | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [f68a55a](https://github.com/PaperMC/Paper/commit/f68a55a8964e02b11a0951b971951963da5f0276) | Fix duplicate palette entry handling in anti xray | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [09ee222](https://github.com/PaperMC/Paper/commit/09ee222482646f8ed84413aea89d0108653af938) | Add the ability to throttle inactive goal selector ticking for mobs (#12781) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [ec0cd5c](https://github.com/PaperMC/Paper/commit/ec0cd5c5aed17e8770f645230dfe4317f72f2ee5) | Server-side translations everywhere (#14120) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [cc7ee9a](https://github.com/PaperMC/Paper/commit/cc7ee9aebdfc9e4acc3ef8f38c35944872fd1f87) | Lazy initialization of formattedDisplayName (#14039) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [6e1bb67](https://github.com/PaperMC/Paper/commit/6e1bb67eb12d4a456a242b7a12d1d9737f67b616) | Rewrite Adventure Book calls to BookMeta (#14081) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |

### Öffentliche APIs und Kompatibilität

| Commit | Originaltitel | Umsetzung |
| --- | --- | --- |
| [1b9bf5c](https://github.com/PaperMC/Paper/commit/1b9bf5c8e9cd8afa33117fd74d592247191bbb4a) | Add EntityPotentSulfurLaunchEvent (#14237) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [2f1ce01](https://github.com/PaperMC/Paper/commit/2f1ce01e875377c27ed7456c0f4d7e4e27ca0e26) | Improve swing hand API to respect item ATTACK_ANIMATION component (#14363) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [e65c076](https://github.com/PaperMC/Paper/commit/e65c0765c63576e808cbe518a4416251e408cc8e) | Add permissions for reset and sequence arguments in /random (#14128) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [ac9a1b2](https://github.com/PaperMC/Paper/commit/ac9a1b2c72ebffd15531749f0b998214ab221c5e) | Make Color implement adventure ARGBLike (#14349) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [0db5c93](https://github.com/PaperMC/Paper/commit/0db5c93a3dbc8eeec333a51830e1ecf578faa7d6) | Add helper methods for vanilla-style command feedback (#14331) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [2766dd3](https://github.com/PaperMC/Paper/commit/2766dd30b1d463fd5df942b8df9ad4ba31ac5d27) | Add API for explorer maps using specific structures (#14290) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [bc68acd](https://github.com/PaperMC/Paper/commit/bc68acd74104584b13aac76261f64fbe65358876) | Add API Player.canCriticalAttack(Entity) (#13915) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [e13b4b3](https://github.com/PaperMC/Paper/commit/e13b4b3d41bb1a9b7032ef80c9a197f0002d21ab) | Add Roll EnchantmentOffer API (#12345) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [29aeb60](https://github.com/PaperMC/Paper/commit/29aeb6081b6ae0c0175dede883d50859179a9301) | Add Allies and additional rule API (#13853) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [22c663d](https://github.com/PaperMC/Paper/commit/22c663d327b9b29b1eada244112e46651e63e5d6) | Add PrepareHopperTransferEvent (#14368) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [db93f62](https://github.com/PaperMC/Paper/commit/db93f621b3f2160ffc8879c7bea1c7fc5d7710c4) | Add AttachableDirectional Interface (#12932) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [7ec11b2](https://github.com/PaperMC/Paper/commit/7ec11b289ecb93c0728994399e5db84690ef1aa9) | Fix unix domain socket support (#13731) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [b69f87e](https://github.com/PaperMC/Paper/commit/b69f87e5ac4596f98ae8d67c75a30bda5a5cbb32) | [ci skip] Remove nullable 'to' ctor param in PlayerMoveEvent and inheritors (#14398) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [a0ded75](https://github.com/PaperMC/Paper/commit/a0ded751dca6867902e4741426d2b03838170064) | Add event for Entity deciding whether an item belongs in a Container (#13811) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [4738ffe](https://github.com/PaperMC/Paper/commit/4738ffe3354f0b0714b2f395014a24073171f508) | Add API to get chunk generation state (#14373) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [7ff575c](https://github.com/PaperMC/Paper/commit/7ff575c99bb8036461ba1383416044dd302f05b8) | feat: add PlayerBundleItemSelectEvent (#13898) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [e8a1f8f](https://github.com/PaperMC/Paper/commit/e8a1f8fd686e79fa728528c8b92882f0cddb853b) | Configurable plugin message channel limit (#14160) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |
| [03226a5](https://github.com/PaperMC/Paper/commit/03226a58389b1327f749567b809ef13f31adac09) | Add `Entity#isInvulnerableTo` method (#12826) | Übernommen; die Folia-Anpassungen sind unten beschrieben. |

### Bereits gleichwertig vorhanden

| Commit | Originaltitel | Umsetzung |
| --- | --- | --- |
| [2d602d6](https://github.com/PaperMC/Paper/commit/2d602d68c10ca5bf0e760e63a3c20f9f72db2143) | Split TreeType.POPLAR into per-color poplar tree types (#14416) | Farbige Poplar-Zuordnung bereits vorhanden; POPLAR-Alias und lokaler Grower-Kontext bleiben erhalten. |
| [06b855f](https://github.com/PaperMC/Paper/commit/06b855f0d37a72c853fe8f827ffc08904f907a8e) | Catch async potion brewer reset mixes | Bereits strengerer globaler Rezept-Schreibvertrag; kein schwächerer Main-Thread-Guard. |

### Build, Dokumentation und Paper-eigene Metadaten

| Commit | Originaltitel | Umsetzung |
| --- | --- | --- |
| [67912d0](https://github.com/PaperMC/Paper/commit/67912d0827e66264e14fef4874801d6b1139cd99) | [ci skip] Re-enable using JBR for local run tasks again (#14318) | Optionaler Sinopia-Buildhelfer übernommen; Java 25 bleibt erforderlich, JBR wird nicht verpflichtend. |
| [0d9bf08](https://github.com/PaperMC/Paper/commit/0d9bf08a23822ee83b98ce033dfb1f0bf2a21942) | [ci skip] Fix broken test plugin refs | Paper-eigene Testplugin-Referenzen nicht in Tesseras separates Testplugin kopiert. |
| [8c0b839](https://github.com/PaperMC/Paper/commit/8c0b8396d676f6abef3df8ab992ac726f6e01c9c) | [ci skip] Trim agent instructions (#14174) | Paper-eigene Agentenhinweise nicht als Produktänderung übernommen. |
| [c927bd3](https://github.com/PaperMC/Paper/commit/c927bd34ab4b544cc125367ea62c7ea2f1a2196d) | [ci skip] Remove advice on not using entity scheduler if not targeting Folia (#14384) | Javadoc der geerbten Scheduler-Klasse übernommen; Folia-Kontext bleibt maßgeblich. |
| [b5eb5e0](https://github.com/PaperMC/Paper/commit/b5eb5e0bfd1270eba92fa9cb7470921b4517b89c) | [ci skip] Add manual dispatch trigger | Paper-eigene CI-Dispatch-Änderung nicht übernommen. |
| [bd9daa8](https://github.com/PaperMC/Paper/commit/bd9daa8fba19cb288d690c1621ad1749de3e0a78) | Promote build channel to stable | Paper-Stable-Promotion nicht übernommen; Tessera bleibt beta. |
| [dba08d2](https://github.com/PaperMC/Paper/commit/dba08d2f25a07ec1b4a72d130795053ffc50e148) | Avoid startup failure when UnixSystem UID lookup fails in JDK 27 (#14395) | Kompatibler UID-Fallback im Plattformadapter enthalten; kein Java-27-Upgrade. |

## Umsetzung und Regionsgrenzen

Die alten und neuen Paper-Referenzen wurden separat vollständig gepatcht.
Der native semantische Java-Vergleich wurde dreiseitig in Tessera integriert;
kein Austausch des Folia-Servers gegen Papers Main-Thread-Implementierung.

- Leafpile-BOM und alle verwendeten Module einschließlich Folia-Profiler auf
  `1.3.2`; `ioutil` ergänzt. Beide Moonrise-Stufen sind enthalten, einschließlich
  Fast-NBT, Palette-Korrekturen, Vanilla-Biomsampling-Reihenfolge sowie
  konsolidierter Region-Dateireparatur und externer Oversized-Chunks.
- Strukturstück-Generierung für Stronghold und Nether-Festung synchronisiert
  den jeweiligen Vanilla-Aufbauzustand. Daraus folgen keine Rechte auf fremde
  Blockregionen. Per-Region-Baumkontexte bleiben erhalten.
- Die neue Chunk-Status-API liest veröffentlichte Metadaten oder natives
  asynchrones I/O. Kein Chunk-Ticket und keine neue Generierung. Disk-Fallback
  der synchronen Methode ist auf Tick-/Netzwerkthreads verboten. Admission
  serialisiert mit Weltzustandswechseln; Fehler beenden Futures ausdrücklich.
- Der Task-Abbruchzustand wird in Async-, Global-, Entity- und dem echten
  Folia-Regionsscheduler nach dem Callback final. Keine Unterbrechung laufenden Codes.
- Neue Entity-Allianz-, Schadens-, Krit- und Animationszugriffe prüfen den
  Besitzer ausdrücklich auch bei Spielern mit altem ungeprüftem Handle-Override.
- Neue Hopper-, Bundle-, Sortier-, Schwefel- und Luft-Callbacks prüfen vor
  weiterer Mutation den noch gültigen Besitzer/Container. Bucket-Ergebnisdaten
  bleiben verschachtelbar aufrufgebunden statt in einem globalen statischen Feld.
- Rezeptpriorität wird aus dem unveränderlichen Katalog gelesen. Laufzeitrezepte
  werden auch in Batch-/Ersetzungspfaden markiert; Schreiber bleiben global,
  Client-Resets auf dem aktuellen Spielerbesitzer und auch bei Tick-Freeze aktiv.
- Client-Ladestatus wird im tatsächlich verwendeten Folia-Dimensions-/Respawnpfad
  zurückgesetzt, nicht nur in Papers hier nicht verwendeter Teleportmethode.
- Map-Cleanup benutzt den gemeinsamen Server-Store mit weiterhin synchronisierten
  Maps. Team-Cleanup und verzögerte Anzeigenamen ändern nicht das Scoreboard-
  Ownership-Modell.
- JLine erhält keinen konkurrierenden eigenen JVM-Hook. Nach Log-Drain werden
  dessen Aufgaben kontrolliert ausgeführt; der vorhandene synchronisierte,
  idempotente Logging-Shutdown bleibt erhalten. Das Entfernen des letzten
  manuell verwalteten Hooks entfernt keinen null-JVM-Hook.
- Fehlende primäre Worldgen-Einstellungen werden nur im passenden Startpfad
  rekonstruiert. Vorhandene korrupte Dateien werden nicht still überschrieben.
  Runtime-/Restore-Zielwelten behalten ihre gespeicherten Generator-/Biombindungen.

Die zusätzliche Optimierung für inaktive Goal-Selector ist standardmäßig
abgeschaltet. Sie ist kein pauschaler Vanilla-Timing-Wechsel und wird nicht
zur Behebung einer ungeklärten Spieler-Netzwerklatenz erklärt.
Unix-Socket-APIs ändern keine vorhandene Windows-/TCP-Serverkonfiguration.

## Dauerhafte Quellen und Tests

- [Native Integrationspatch](../folia-server/minecraft-patches/features/0069-Integrate-October-Paper-and-Moonrise-updates-with-re.patch)
  plus weitere Owner-Callback-Absicherung in der anschließenden Patchkette.
- [API-Referenz](api/index.md), [Chunk-Vertrag](api/chunk-generation-state.md)
  und [Paper-API-Ergänzungen](api/paper-api-022.md).
- [Sinopia-Sicherung](../upstream-patches/sinopia/2026-10-26-paper-backports.patch).
- [Buildbericht und konkrete Prüfergebnisse](BUILD-26.3-022.md).

Der Buildbericht trennt Java-Tests, native Protokolltests und nicht ausgeführte
Prüfungen. Ein nativer Test ersetzt keine Abnahme des vollständigen
MCC-/MVE-/TAB-/LuckPerms-Stacks oder eines Linux-Unix-Socket-Transports.
