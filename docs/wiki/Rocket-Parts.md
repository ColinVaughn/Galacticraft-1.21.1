# Rocket parts

A complete rocket needs a cone, body, fin, and engine. Boosters and upgrades are optional. Each part has its own file under `data/<namespace>/galacticraft/`.

| Part | Directory | Required settings for `galacticraft:basic` |
| --- | --- | --- |
| Cone | `rocket_cone/` | `predicate` |
| Body | `rocket_body/` | `predicate`, `max_passengers` |
| Fin | `rocket_fin/` | `predicate`, `can_maneuver` |
| Engine | `rocket_engine/` | `predicate`, `fuel_capacity` |
| Booster | `rocket_booster/` | `predicate`, `max_velocity`, `acceleration`, `fuel_usage` |

The five structural slots support `galacticraft:basic`. [Upgrades](Rocket-Upgrades) use their own types.

## Configuration fields

| Field | Applies to | Required/default | Meaning |
| --- | --- | --- | --- |
| `predicate` | All basic parts | Required | [Travel rule](Travel-Predicates). |
| `layout` | All basic parts | Stock dimensions | Optional `height` and `width` overrides in model units. |
| `paintable` | All basic parts | `false` | Enables paint controls for a model that supports them. |
| `recipe` | All basic parts | Absent | Module-crafting pattern for the rocket workbench. |
| `model` | All basic parts | Absent | Full model resource path. |
| `max_passengers` | Body | Required | Total seats, including the pilot. |
| `can_maneuver` | Fin | Required | Enables steering during powered flight. |
| `fuel_capacity` | Engine | Required | Tank capacity in droplets. |
| `fuel_usage` | Engine | `20` | Droplets burned per powered-flight tick. |
| `max_velocity` | Booster | Required | Climb-velocity setting. |
| `acceleration` | Booster | Required | Climb-acceleration setting. |
| `fuel_usage` | Booster | Required | Fuel burn added to the engine's burn. |

Use positive dimensions, seat counts, capacities, and flight settings. Many of these fields accept unrestricted numbers during loading.

## Tier-4 engine

`data/galacticraft-test/galacticraft/rocket_engine/tier_4.json`

[tier_4.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/galacticraft/rocket_engine/tier_4.json)

```json
{
  "type": "galacticraft:basic",
  "config": {
    "fuel_capacity": 243000,
    "fuel_usage": 9,
    "paintable": true,
    "predicate": {
      "type": "galacticraft:access_weight",
      "config": {
        "weight": 4
      }
    },
    "model": "galacticraft:models/misc/rocket_engine_tier_3.json",
    "recipe": {
      "type": "galacticraft:centered_patterned",
      "config": {
        "height": 36,
        "slots": [
          {
            "ingredient": {
              "item": "minecraft:diamond"
            },
            "x": -9,
            "y": 0
          },
          {
            "ingredient": {
              "item": "galacticraft:heavy_plating_t3"
            },
            "x": -9,
            "y": 18
          }
        ]
      }
    }
  }
}
```

The engine holds 243000 droplets, burns 9 per tick, and permits access weight 4. Its `model` reuses the tier-3 artwork. Its `recipe` adds a diamond-and-plating module pattern to the rocket workbench after the player researches the part.

## Add the other parts

The tier-4 examples use the same entry name in four different registries:

| Part file | Settings |
| --- | --- |
| [Cone](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/galacticraft/rocket_cone/tier_4.json) | Height 40 model units, paintable, weight 4. |
| [Body](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/galacticraft/rocket_body/tier_4.json) | Three seats, weight 4. |
| [Fin](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/galacticraft/rocket_fin/tier_4.json) | Steering enabled, width 48 model units, weight 4. |
| [Engine](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/galacticraft/rocket_engine/tier_4.json) | Three-bucket tank, burn 9 per tick, weight 4. |

Give each part a [module recipe](Recipes-and-Workbench) and include its research ID in a [schematic](Research-and-Schematics). An engine module uses `engine: "galacticraft-test:tier_4"`; its research ID is `galacticraft-test:rocket_engine/tier_4`.

## Layout

There are 16 model units per block. Set `height` and `width` independently inside `layout`; omitted values keep their stock dimensions.

The assembled rocket stacks its engine, fin section, body, and cone vertically. Increasing a part's height raises the parts above it. Boosters mount at the fin section and can increase width, but add no vertical segment. The widest structural part determines the rocket's width.

Check the model alongside its collision box. Scaling the artwork alone does not change collision dimensions.

## Fuel

One bucket is 81000 droplets. In GC4 fuel mode, the fitted engine supplies tank capacity and burn, and a fitted booster adds its burn. Upgrades may add capacity. Uniform fuel mode uses the server's global capacity and burn settings instead.

Stock tier-1 engines hold 81000 droplets and burn 20 per tick. Tier 2 holds 121500 and burns 14; tier 3 holds 121500 and burns 11.

## Module components

Use `galacticraft:rocket_module` with one part inside `galacticraft:rocket_data`. The ion engine module carries only `{ "engine": "examplepack:ion" }`. Available part fields are `cone`, `body`, `fin`, `booster`, `engine`, and `upgrade`.

Complete rockets combine the required modules at the workbench. See the [quickstart](Datapack-Quickstart) for a full crafting result and [models and resources](Rendering-and-Resources) for artwork and translations.