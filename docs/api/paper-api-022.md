---
title: "Paper-Erweiterungen ab Tessera Build 022"
description: "Entity-Regeln, Inventarereignisse, Befehlsantworten und weitere übernommene Paper-APIs mit ihren Folia-Grenzen."
navTitle: "Paper-APIs 022"
order: 117
updated: 2026-10-26
minecraftVersion: "26.3"
badge: "Paper-Kompatibilität"
---

Die folgenden öffentlichen Erweiterungen stammen aus Paper und sind über Sinopia
in Tessera Build 022 enthalten. Es sind keine neuen Tessera-Capabilities. Alle
Signaturen benötigen die [passende API-Version](project-setup.md); die Existenz
einer Methode ersetzt keine Ownership-Prüfung. Chunk-Metadaten haben einen
eigenen [Artikel](chunk-generation-state.md).

## Entity-Allianzen und Schaden

Auf `org.bukkit.entity.Entity`:

```java
boolean isAlliedTo(Entity other);
Map<Key, Predicate<Entity>> getAdditionalAlliedRules();
void addAdditionalAlliedRule(Key key, Predicate<Entity> predicate);
void removeAdditionalAlliedRule(Key key);
void removeAdditionalAlliedRule(Namespaced namespaced);
boolean isMarkedInvulnerable();
boolean isInvulnerableTo(DamageSource source);
```

`Key` und `Namespaced` stammen aus `net.kyori.adventure.key`, `DamageSource`
aus `org.bukkit.damage`. Die Regeln ergänzen native Team-/Allianzprüfungen;
mindestens ein positives Prädikat kann eine Allianz begründen. `isAlliedTo`
berücksichtigt die native beidseitige Auswertung, nicht bloß den eigenen
zusätzlichen Regelbestand. `other`, Schlüssel, Prädikat und Schadensquelle dürfen
nicht `null` sein (`IllegalArgumentException`). Ein gleicher Schlüssel ersetzt
die alte Regel, die Namespace-Überladung entfernt alle Regeln dieses Namespace.

Lesen, Schreiben und Prädikatauswertung gehören zum aktuellen Entity-Besitzer.
Für eine direkte Paarabfrage müssen beide Entities diesem Thread gehören; eine
Referenz auf eine entfernte Entity erlaubt keinen Fremdzugriff. Die Regel-Map
ist eine nicht veränderbare **lebende Ansicht**, kein threadsicherer Snapshot.
Prädikate müssen kurz, nicht blockierend und frei von fremden Weltzugriffen sein.
Keine Scheduler-Aufträge aus einem Prädikat synchron abwarten.

Ab Build 023 grenzt auch die native KI ihre Kandidaten vor der Auswertung auf
den aktuellen Besitzer ein. Bei gezähmten Tieren bleibt die Besitzerbindung
auch über Regions-/Dimensionswechsel erhalten. Zusätzliche Plugin-Prädikate
eines entfernten Besitzers werden dabei **nicht** auf dem Thread des Tiers
ausgeführt; nach Rückkehr in dieselbe Region stehen sie wieder zur Verfügung.
Das ist keine asynchrone Auswertung fremder Allianzregeln.

Für einen einzelnen Regelaufruf werden die Prädikate vor der Iteration kopiert.
Änderungen am Regelbestand bleiben über die lebende Map-Ansicht sichtbar, machen
aber die laufende Iteration nicht ungültig; neu hinzugefügte Regeln gelten erst
bei einer folgenden Auswertung. Entfernt oder transferiert ein Prädikat einen
Beteiligten, beendet Tessera die alte Auswertung und verwendet ihr Ergebnis
nicht zur Fortsetzung einer Mutation auf dem alten Besitzer. Die direkten
öffentlichen API-Aufrufe bleiben bei anfänglich fremden Entities unzulässig.

Regeln liegen im Bukkit-Wrapper, werden nicht als dauerhafte NBT-Regeln gespeichert
und nicht automatisch einem Plugin zugeordnet. UUID/Wrapper bleiben bei den
unterstützten Entity-Transfers erhalten; eigene zeitweilige Regeln vor Disable
auf den jeweiligen Besitzern entfernen. Ein Prädikat kann zusätzlich ein
threadsichtbares Aktiv-Flag des Plugins berücksichtigen. Nach Retirement keine
Entity-Mutation mehr nachholen. Andere Plugins dürfen dieselben Schlüssel
ersetzen; einen eindeutigen Namespace benutzen.

`isMarkedInvulnerable()` liest das durch `setInvulnerable` gesetzte Flag.
`isInvulnerableTo(source)` bewertet den konkreten nativen Schadenskontext, ohne
Schaden zu verursachen. Das alte `isInvulnerable()` bleibt binär vorhanden, ist
aber zugunsten der differenzierten Methoden deprecated. Ein Prüfwert garantiert
nicht, dass ein späteres Schadensereignis erfolgreich sein wird.

## Kritische Treffer und Schwunganimation

```java
// HumanEntity
boolean canCriticalAttack(Entity entity);
// LivingEntity
void swingHand(EquipmentSlot hand);
void swingHand(EquipmentSlot hand, SwingAnimation swingAnimation);
```

Die erste Methode prüft einen aktuell möglichen kritischen Angriff einschließlich
der Weltoption `disablePlayerCrits`; sie führt keinen Angriff aus. Angreifer und
Ziel müssen auf demselben aktuellen Besitzer liegen, `entity` darf nicht null
sein. Das Ergebnis kann sich im nächsten Tick ändern.

`EquipmentSlot.HAND` und `OFF_HAND` sind erlaubt, andere Werte/null ergeben
`IllegalArgumentException`. `SwingAnimation` ist
`io.papermc.paper.datacomponent.item.SwingAnimation` und muss vorhanden sein.
Ohne explizite Animation verwendet Vanilla die `ATTACK_ANIMATION`-Komponente des
gehaltenen Items, sonst `WHACK` mit sechs Ticks. Die Animation startet auf dem
Entity-Besitzer; sie verursacht keinen Treffer. Bei einem Typ ohne passende
Animation gibt es keinen sichtbaren Effekt. Für spätere Arbeit den
[EntityScheduler](scheduler.md#entityscheduler) verwenden; Disable/Retirement
führen nicht zu einer nachträglichen Animation.

## Inventarereignisse und Entity-Ereignisse

Listener wie gewohnt mit `registerEvents` registrieren. Alle hier genannten
Events sind synchron zum jeweiligen Regions-/Entity-Vorgang, nicht global.
Keine gehaltenen Event-/Inventarreferenzen off-thread mutieren. Nach einem
Transfer neue Arbeit auf dem aktuellen Besitzer planen. Nach Plugin-Disable
keine neuen Folgeaufgaben einreichen.

### Hopper-Auswahl

`io.papermc.paper.event.inventory.PrepareHopperTransferEvent` wird vor der
Slot-Auswahl und vor einem möglichen `InventoryMoveItemEvent` ausgelöst:

```java
Inventory getSource();
Inventory getDestination();
Inventory getInitiator();
boolean isSlotAllowed(int slot);
void setSlotAllowed(int slot, boolean allowed);
```

Die Slotnummer gehört immer zum **Quellinventar**. Anfangs sind Slots erlaubt;
gesperrte Slots behandelt der Hopper als nicht vorhanden. Die Sperre gilt nur
für diese Auswahl, nicht dauerhaft für das Inventar. Ungültige Indizes ergeben
`IllegalArgumentException`. Das Event ist nicht `Cancellable`; bei Bedarf alle
Quellslots sperren. Es wird bei deaktivierten Hopper-Move-Events oder leerer
Quelle nicht aufgerufen. Block-Hopper und Hopper-Minecart verwenden den nativen
Hopper-Pfad. Entfernt/transferiert ein Callback einen beteiligten nativen
Container, setzt Tessera den Transfer nicht auf dem alten Besitzer fort.

### Sortierentscheidung

`io.papermc.paper.event.entity.ItemTransportingEntitySortEvent` ergänzt die
Containerbewertung transportierender Mobs wie Kupfergolems:

```java
Entity getEntity();
ItemStack getItemStack();
Inventory getContainerInventory();
Event.Result getResult();
void setResult(Event.Result result);
```

`DEFAULT` verwendet Vanilla, `ALLOW` akzeptiert und `DENY` verwirft den
Zielcontainer. Kein null-Ergebnis setzen. Das Item ist eine Kopie, das Inventar
eine lebende Owner-Referenz. Ein Vorgang kann vor dem Öffnen und vor der Übergabe
je ein Event auslösen. Eine einmalige Entscheidung ist keine Transfergarantie.

### Bundle-Auswahl

`io.papermc.paper.event.inventory.PlayerBundleItemSelectEvent` beobachtet die
angeforderte Bundle-Auswahl vor ihrer Übernahme im aktuellen Spieler-Inventarmenü:

```java
Player getPlayer();
ItemStack getBundle();
int getRawSlot();
int getSlot();
ItemStack getPreviousItem();
ItemStack getSelectedItem();
int getPreviousIndex();
int getSelectedIndex();
PlayerBundleItemSelectEvent.Direction getDirection();
```

Das Event ist nicht abbrechbar und läuft nicht für Creative-Spielerinventare.
`getSlot()` ist inventarlokal, `getRawSlot()` gehört zur gesamten View.
Vorheriger Index `-1` und ein leeres vorheriges Item bedeuten keine Vorauswahl.
Die beiden Item-Getter für die Auswahl liefern Kopien. `Direction` ist
`FORWARD`, `BACKWARD` oder `UNKNOWN` (unter drei Stacks nicht zuverlässig);
`getDelta(): OptionalInt` liefert `+1`, `-1` oder leer, `flip()` die Gegenrichtung.

### Schwefel-Geysir und Crafting-Vorschau

`io.papermc.paper.event.entity.EntityPotentSulfurLaunchEvent` ist ein nicht
abbrechbares Beobachtungsereignis auf dem Launch-Besitzer. `getSulfurBlock()`
liefert den lebenden Block, `getSourceLocation()` die kopierte Austrittsposition
der Wassersäule und `getLaunchVelocity()` einen kopierten addierten Vektor.
Die Änderung dieser Kopien ändert den Launch nicht. Nach Entfernen/Transfer der
Entity im Callback erfolgt keine Geschwindigkeitsmutation auf dem alten Besitzer.

Der Crafter erzeugt für seine Rezeptvorschau jetzt das bestehende
`PrepareItemCraftEvent`. Dessen Resultat beeinflusst die Vorschau; daraus keine
zusätzliche automatische Ausführung eines Crafter-Rezepts ableiten. Ein im
Callback geschlossenes oder übertragenes Menü wird nicht weiter beschrieben.
Leere SulfurCube-Buckets erzeugen wieder `PlayerBucketEmptyEvent`; resultierende
Items bleiben aufrufgebunden, auch bei verschachtelten Listenern und parallelen
Regionen.

## Befehlsantworten

Auf `io.papermc.paper.command.brigadier.CommandSourceStack`:

```java
void sendReply(ComponentLike message);
void sendRichReply(String message, TagResolver... resolvers);
void sendSuccess(ComponentLike message);
void sendSuccess(ComponentLike message, boolean allowInformingAdmins);
void sendRichSuccess(String message, TagResolver... resolvers);
void sendFailure(ComponentLike message);
void sendRichFailure(String message, TagResolver... resolvers);
```

`ComponentLike` kommt aus Adventure, `TagResolver` aus MiniMessage.
Reply richtet sich an den ausführenden Spieler, sonst den Sender. Success nutzt
Vanilla-Feedback-/Admin-/Konsolenregeln; die kurze Überladung erlaubt die
Admininformation. Failure respektiert unterdrückte Fehlerausgaben und färbt nicht
automatisch rot. Rich-Varianten parsen MiniMessage mit Empfänger-Pointern.
Null-Nachrichten ergeben `IllegalArgumentException`. Nicht vertrauenswürdige
Texte als aufgelöste Komponenten/escaped Platzhalter einsetzen, nicht als
ungeprüftes MiniMessage-Markup.

Im bestehenden Befehlskontext antworten. Diese Methoden planen keine Weltarbeit
um und machen einen gespeicherten CommandSourceStack nicht für spätere
Fremdregionszugriffe gültig. Für spätere Spielerantworten den aktuellen
EntityScheduler benutzen. `/random` hat zusätzlich getrennte Vanilla-
Unterbefehlsberechtigungen, ohne globale Freigabe der übrigen Funktionen.

## Farben, Blockdaten und Verzauberungsangebote

`org.bukkit.Color` implementiert jetzt `ARGBLike`; `alpha()`, `red()`, `green()`
und `blue()` entsprechen den bestehenden Komponentengettern. `fromARGB(...)`
erzeugt eine Farbe, Setter liefern eine neue Farbe statt die alte zu mutieren.
Komponenten müssen in `0..255` liegen, sonst `IllegalArgumentException`. Diese
unveränderlichen Werte benötigen keinen Weltbesitzer und kein Disable-Cleanup.

`org.bukkit.block.data.AttachableDirectional` verbindet `FaceAttachable` und
`Directional`. Der parameterlose `getAttachedBlockFace(): BlockFace` liefert
bei Boden `DOWN`, Decke `UP`, Wand die Gegenrichtung von `getFacing()`. Detached
BlockData kann lokal ausgewertet werden; Lesen/Setzen eines platzierten Blocks
bleibt ownergebunden. Die Methode verändert keine Welt.

```java
// org.bukkit.enchantments.EnchantmentOffer
static EnchantmentOffer[] rollEnchantmentOffers(
    ItemStack targetStack, int seed, int bookshelfCount, int offerAmount);
```

Die Methode berechnet neue Angebote mit nativer Verzauberungsauswahl, ohne
Inventar, XP oder einen Verzauberungstisch zu mutieren. Das Ergebnisarray hat
`offerAmount` Einträge; nicht verfügbare Angebote sind null. Ein vorhandenes
Zielitem, nichtnegative Bücherzahl und eine kleine nichtnegative Angebotszahl
übergeben; negative Arraygrößen sind ungültig. Seed und Bücherzahl beschreiben
den virtuellen Tisch. Itemkopien ohne gleichzeitigen Schreiber verwenden;
lebende Spielerinventare zuerst auf ihrem Owner kopieren. Der Bridge-Zugriff
setzt einen initialisierten Server/Registry voraus, nicht Plugin-Bootstrap vor
Serverinitialisierung. Keine Angebote nach Disable anwenden. `equals`/`hashCode`
vergleichen nun Verzauberung, Stufe und Kosten.

## Explorer-Maps und Socket-Adressen

Auf `Bukkit` und `Server` gibt es zusätzlich die konkreten Struktur-Überladungen:

```java
ItemStack createExplorerMap(World world, Location location,
    org.bukkit.generator.structure.Structure structure, MapCursor.Type mapIcon);
ItemStack createExplorerMap(World world, Location location,
    org.bukkit.generator.structure.Structure structure, MapCursor.Type mapIcon,
    int radius, boolean findUnexplored);
```

Die kurze Variante sucht mit Radius 100 nur unerforschte Strukturen. Kein Treffer
liefert null. Welt, Position, Struktur und Icon müssen gültig sein. Die Suche
und Kartenerzeugung sind synchron und verlangen eine aktive Welt sowie den
Regionsbesitzer des Suchursprungs; sonst `IllegalStateException`. Dies ist
**keine** neue asynchrone Locator-API. Vorhandene native Thread-/Chunk-Grenzen
gelten auch während der Suche weiter; eine große Suche nicht
auf Global/Async verschieben, um Folia-Prüfungen zu umgehen. Nach Disable keinen
verzögerten Kartenauftrag starten. Produktive große Suchradien separat prüfen.

Verbindungen können auf unterstützten nativen Unix-Transports Unix-Domain-Sockets
verwenden; Windows unterstützt damit nicht automatisch Unix-Sockets. Generische
Adressen nicht ungeprüft zu `InetSocketAddress` casten:

```java
// Paper NetworkClient und PlayerConnectionCloseEvent
SocketAddress getSocketAddress();
```

Die konkrete Adresse kann Unix- oder Inet-Adresse sein. Kompatibilitätsgetter für
IP-Adressen verwenden bei Unix-Verbindungen ohne weitergeleitete Client-IP eine
Loopback-Adresse. Diese ist kein zuverlässiger Nachweis des echten Clients.
Close-Events können asynchron sein; reine Adresswerte lesen, keine lebende
Entity vom Netzwerkthread mutieren. Player-Adresszugriff behält seine bestehenden
Owner-Regeln. Es gibt keine verpflichtende Proxy- oder Transportumstellung.

## Unveränderte Grenzen

Die neuen Schnittstellen ändern nicht den nativen Spieler-Restore-Vertrag,
Tesseras Rezept-Schreibschranke oder die Pflicht zu `folia-supported: true`.
Rezeptpriorität ist wieder Vanilla-Reihenfolge für native Rezepte; während der
Laufzeit eingefügte Rezepte übersteuern passende ältere Rezepte in ihrer
Einfügereihenfolge. Details zu atomaren Batches und Reset stehen unter
[Rezepte](recipes.md), Ereignis-Lebenszyklen unter [Events](events.md).
