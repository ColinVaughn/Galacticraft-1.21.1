# Recipes and the rocket workbench

Crafting and machine recipes go in `data/<namespace>/recipe/`. A part's embedded workbench pattern goes inside its `config.recipe` field.

## Module crafting

Use `minecraft:crafting_shaped` or `minecraft:crafting_shapeless` to produce `galacticraft:rocket_module`. Put the configured part ID inside the result's `galacticraft:rocket_data` component. The [ion tutorial](Datapack-Quickstart) includes a complete shaped recipe.

Minecraft 1.21.1 recipe results use `id`, `count`, and `components`. Give a module exactly one part field: `cone`, `body`, `fin`, `booster`, `engine`, or `upgrade`.

## Compressor

`data/galacticraft/recipe/compressing/compressed_bronze.json`

[compressed_bronze.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/galacticraft/recipe/compressing/compressed_bronze.json)

```json
{
  "type": "galacticraft:compressing_shapeless",
  "ingredients": [
    {
      "item": "galacticraft:compressed_copper"
    },
    {
      "item": "galacticraft:compressed_tin"
    }
  ],
  "result": {
    "count": 1,
    "id": "galacticraft:compressed_bronze"
  }
}
```

| Type | Ingredients |
| --- | --- |
| `galacticraft:compressing_shapeless` | `ingredients` array with one to nine nonempty ingredients. |
| `galacticraft:compressing_shaped` | Shaped `pattern` and `key` fields. |

Both types accept optional `time` (default 200 ticks) and `group` (default empty). Results can carry components.

Ingredients can reference item tags. The stock [steel recipe](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/galacticraft/recipe/compressing/compressed_steel_from_ingots.json) uses `{ "tag": "c:ingots/steel" }` twice.

## Circuit fabricator

`data/galacticraft/recipe/fabrication/advanced_wafer.json`

[advanced_wafer.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/galacticraft/recipe/fabrication/advanced_wafer.json)

```json
{
  "type": "galacticraft:fabrication",
  "ingredient": {
    "item": "minecraft:repeater"
  },
  "result": {
    "count": 1,
    "id": "galacticraft:advanced_wafer"
  }
}
```

`ingredient` sets the fifth input. Diamond, two silicon inputs, and redstone remain the machine's fixed inputs. Optional `time` defaults to 300 ticks; `group` defaults to empty.

## Rocket assembly

The stock assembly page is defined by:

`data/galacticraft/recipe/rocket/modular.json`

[modular.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/galacticraft/recipe/rocket/modular.json)

```json
{
  "type": "galacticraft:modular_rocket"
}
```

It has cone, body, fin, and engine wells, plus upgrade wells. Add parts and module recipes to use this existing page. It needs no extra recipe for each engine or tier.

## Module patterns in part files

Set `config.recipe` on a part to give it a workbench crafting page. The [tier-4 engine](Rocket-Parts) has a two-slot diamond-and-plating pattern.

| Pattern type | Configuration |
| --- | --- |
| `galacticraft:centered_patterned` | `height` and a `slots` array. Each slot has `ingredient`, `x`, and `y`. |
| `galacticraft:wrap_patterned` | `height`, `left`, and `right` slot arrays. Each slot has `ingredient`, `x`, and `y`. |

Coordinates position the ingredient wells in the pattern. Wrapped patterns list left-side ingredients first, then right-side ingredients.

The page ID is `<namespace>:rocket_module/rocket_<slot>/<entry>`. Structural module pages appear after the player researches the part. Upgrade pages use their ingredient requirements without structural research.

To override a generated module page, put a recipe at that ID with these fields:

| Field | Value |
| --- | --- |
| `type` | `galacticraft:rocket_module` |
| `part` | Slot-qualified part ID, such as `galacticraft-test:rocket_engine/tier_4`. |
| `recipe` | Centered or wrapped pattern object. |

The referenced part must exist. You can also provide an ordinary crafting-table recipe for the same module as another way to obtain it.

## Vehicle workbench pages

The stock astro-miner recipe shows the `galacticraft:schematic_crafting` format:

`data/galacticraft/recipe/astro_miner.json`

[astro_miner.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/galacticraft/recipe/astro_miner.json)

```json
{
  "type": "galacticraft:schematic_crafting",
  "ingredients": [
    {
      "item": "galacticraft:heavy_plating"
    },
    {
      "item": "galacticraft:orion_drive"
    },
    {
      "item": "galacticraft:heavy_plating"
    },
    {
      "item": "galacticraft:orion_drive"
    },
    {
      "item": "galacticraft:heavy_plating"
    },
    {
      "item": "galacticraft:advanced_wafer"
    },
    {
      "item": "minecraft:chest"
    },
    {
      "item": "minecraft:chest"
    },
    {
      "item": "galacticraft:orion_drive"
    },
    {
      "item": "galacticraft:orion_drive"
    },
    {
      "item": "galacticraft:heavy_plating"
    },
    {
      "item": "galacticraft:orion_drive"
    },
    {
      "item": "galacticraft:beam_core"
    },
    {
      "tag": "galacticraft:flags"
    }
  ],
  "layout": "astro_miner",
  "result": {
    "count": 1,
    "id": "galacticraft:astro_miner"
  },
  "schematic": "galacticraft:astro_miner_schematic",
  "sort_order": 50
}
```

| Field | Required/default |
| --- | --- |
| `result` | Required item stack. |
| `layout` | Required layout name. |
| `ingredients` | Required ordered list matching the layout's well count. |
| `schematic` | Optional registered schematic item ID required to unlock the page. |
| `sort_order` | `0`; ties sort by recipe ID. |
| `group` | Empty string. |

Available layouts are `buggy` (16 ingredient wells), `cargo_rocket` (13), and `astro_miner` (14). The ingredient list follows the layout's well order and excludes separate upgrade wells. New layout types require a mod.

An explicit `galacticraft:rocket` recipe can also define a rocket page. Its required fields are `result`, `body_height`, `body`, `cone`, `engine`, and `fins`; optional fields are `boosters`, `storage`, `schematic`, `sort_order`, and `group`. See [RocketRecipe.java](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/java/dev/galacticraft/mod/recipe/RocketRecipe.java) for the slot structures if you need this format.

Run `/reload` after editing recipe files.