# Steed

Server-side Fabric mod for Minecraft **26.2** that makes horses actually worth using. Vanilla clients
need nothing installed: no client half, no resource pack.

## What it does

**Horse armor can be enchanted** — at the enchanting table *and* on the anvil. Vanilla builds horse
armor with `new Item.Properties().horseArmor(material)` and forgets to add the `enchantable` component,
so the enchanting table refuses it (`EnchantmentHelper.getEnchantmentCost` returns 0 without
`ENCHANTABLE`). The anvil never had that problem — it only looks at the `enchantments` component, and every
item carries an empty one — so books could already be applied before this mod. The mod adds
`.enchantable(material.enchantmentValue())`
in `Item.Properties.horseArmor` and puts all six 26.2 tiers (leather, copper, iron, golden, diamond,
netherite) into `#minecraft:enchantable/armor`. `EquipmentSlot.BODY` is already part of
`EquipmentSlotGroup.ARMOR`, so `protection` and friends work on a horse exactly like on a player.

**+30% movement speed and jump strength** — an `AttributeModifier` (`ADD_MULTIPLIED_BASE`) is attached to
every horse, including ones that already exist in the world. Multipliers live in the config; set them to
`1.0` to turn the boost off.

**Goat horn recall** — right-click a horse with a goat horn to bind it (the horn gets the enchanted
glint). Right-click with the bound horn to summon: the mod looks for a safe spot, plays two seconds of
enderman particles, then teleports the horse and puts the horn on a 60 second cooldown.

* the horse is found even if its chunk is not loaded, or if it is in another dimension: chunks 3×3
  around its last known position are force-loaded (5×5 if that fails) and the lookup is retried for up
  to 10 seconds — slow servers take seconds to read chunks from disk;
* the last known position stored in the horn is refreshed after every successful summon;
* a horse with a rider is never summoned; neither is one when there is no room next to you (the search
  goes ±5 horizontally and 24 blocks down, so summoning while flying works — the horse lands on the
  ground below you);
* the binding survives ender chests, shulker boxes, portals, death and server restarts. Right-clicking a
  *different* horse with a bound horn re-binds it to that horse (the glint stays), so the binding can be
  moved — the **grindstone** is what turns the horn back into a plain one.
* chunks that were force-loaded for the search are released as soon as the horse is found, the summon is
  cancelled, or the server stops.

No chat messages and no text above the hotbar: success, binding and failure are sounds only. Failures
are logged on the server (`Steed: …`) with the exact reason.

## Config

`config/steed.json`, created on first start:

| key | default | meaning |
|---|---|---|
| `enchant_horse_armor` | `true` | horse armor is enchantable at the table and on the anvil |
| `goat_horn_recall` | `true` | goat horn binds to a horse and summons it |
| `horse_speed_multiplier` | `1.3` | movement speed multiplier for every horse |
| `horse_jump_multiplier` | `1.3` | jump strength multiplier for every horse |
| `recall_radius` | `5` | horizontal search radius for the summon spot (blocks) |
| `recall_delay_ticks` | `40` | particle delay before the teleport (20 ticks = 1 s) |
| `recall_cooldown_ticks` | `1200` | horn cooldown after a successful summon |

## Requirements

* Minecraft 26.2, Fabric Loader ≥ 0.19.0, Java 25
* the jar goes into the server's `mods/`; vanilla clients join as usual

## Build

```bash
./gradlew build     # jar in build/libs/
```

`gradle.properties` pins a local Windows JDK (`org.gradle.java.home`); the CI workflow strips that line
and builds on the runner's JDK 25.

## Self-test

The mod ships an in-game self-test that builds a scene 3000 blocks from spawn and exercises every
feature without a client: `-Dsteed.selftest=1`. It is gated by that system property and never runs on a
real world by accident.
