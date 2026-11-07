# Add an ion engine

This pack adds an engine with a two-bucket tank, a burn rate of six droplets per tick, and access weight 2. It uses Galacticraft's tier-2 engine model.

## 1. Create the folders

```text
ion-addon-data/
  pack.mcmeta
  data/examplepack/
    galacticraft/rocket_engine/ion.json
    galacticraft/research_source/ion.json
    recipe/ion_engine_module.json
    recipe/ion_schematic.json

ion-addon-resources/
  pack.mcmeta
  assets/examplepack/lang/en_us.json
```

Use these metadata files for Minecraft 1.21.1.

Datapack `pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 48,
    "description": "Galacticraft ion engine example"
  }
}
```

Resource pack `pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 34,
    "description": "Galacticraft ion engine names"
  }
}
```

## 2. Define the engine

`data/examplepack/galacticraft/rocket_engine/ion.json`

[ion.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/examplepack/galacticraft/rocket_engine/ion.json)

```json
{
  "type": "galacticraft:basic",
  "config": {
    "fuel_capacity": 162000,
    "fuel_usage": 6,
    "model": "galacticraft:models/misc/rocket_engine_tier_2.json",
    "predicate": {
      "type": "galacticraft:access_weight",
      "config": {
        "weight": 2
      }
    }
  }
}
```

`fuel_capacity` uses droplets: 81000 droplets is one bucket. `fuel_usage` is the engine's burn per powered-flight tick. These settings apply in GC4 fuel mode; servers using uniform fuel mode set tank capacity and burn globally.

The `model` resource is included with Galacticraft. The weight-2 travel predicate allows destinations with an access weight of 2 or less, provided the other fitted parts also permit travel.

## 3. Add the module recipe

`data/examplepack/recipe/ion_engine_module.json`

[ion_engine_module.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/examplepack/recipe/ion_engine_module.json)

```json
{
  "type": "minecraft:crafting_shaped",
  "category": "misc",
  "key": {
    "E": {
      "item": "galacticraft:rocket_engine"
    },
    "R": {
      "item": "minecraft:redstone_block"
    }
  },
  "pattern": [
    "R",
    "E"
  ],
  "result": {
    "id": "galacticraft:rocket_module",
    "count": 1,
    "components": {
      "galacticraft:rocket_data": {
        "engine": "examplepack:ion"
      }
    }
  }
}
```

Place a redstone block above a stock rocket engine in the crafting grid. The result is a `galacticraft:rocket_module` carrying the ion engine in its `galacticraft:rocket_data` component.

Use one part per module. This recipe gives the player the module; the schematic below unlocks its use in survival assembly.

## 4. Add the schematic recipe

`data/examplepack/recipe/ion_schematic.json`

[ion_schematic.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/examplepack/recipe/ion_schematic.json)

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [
    {
      "item": "minecraft:paper"
    },
    {
      "item": "minecraft:redstone_block"
    }
  ],
  "result": {
    "id": "galacticraft:rocket_schematic",
    "components": {
      "galacticraft:rocket_part_unlocks": [
        "examplepack:rocket_engine/ion"
      ],
      "minecraft:item_name": "{\"text\":\"Ion Engine Schematic\"}"
    }
  }
}
```

The unlock is `examplepack:rocket_engine/ion`, including the slot name. Keep the escaping in `minecraft:item_name`: its value is a JSON text component encoded as a string.

The repository also includes an optional [schematic loot table](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/examplepack/loot_table/ion_schematic.json). Copy it to `data/examplepack/loot_table/ion_schematic.json` to use it in loot or try it with `/loot give @s loot examplepack:ion_schematic`.

## 5. Add the workbench hint

`data/examplepack/galacticraft/research_source/ion.json`

[ion.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/examplepack/galacticraft/research_source/ion.json)

```json
{
  "research": [
    "examplepack:rocket_engine/ion"
  ],
  "description": {
    "text": "Craft an Ion Engine Schematic with paper and a redstone block."
  }
}
```

This text tells players how to obtain the schematic when the workbench reports missing research. Update it if you change the recipe.

## 6. Name the engine

`assets/examplepack/lang/en_us.json`

[en_us.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/assets/examplepack/lang/en_us.json)

```json
{
  "rocket_engine.examplepack.ion": "Ion Engine"
}
```

Put this file in the resource pack. Other languages use the same translation key in their own language files.

## 7. Install and try it

Put the datapack in the world's `datapacks/` folder, enable the resource pack, and reopen the world. Check `/datapack list enabled` if the recipes are missing.

Craft the schematic from paper and a redstone block. Spend it on the workbench's Add New Schematic page, then put the crafted module in the engine well on the rocket assembly page. Check its name, model, fuel capacity, and burn rate.

A tier-1 cone, body, or fin can still block a weight-2 destination. [Travel predicates](Travel-Predicates) explains how the fitted parts combine their rules.

To rename the pack, change `examplepack` in every path, part reference, research ID, and translation key.