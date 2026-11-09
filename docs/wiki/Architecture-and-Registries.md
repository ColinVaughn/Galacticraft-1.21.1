# Datapack structure

Use a namespace of your own for new entries. The examples use `examplepack` and `galacticraft-test`; replace those names consistently in paths and references.

## Pack layout

```text
my-pack/
  pack.mcmeta
  data/
    examplepack/
      galacticraft/
        rocket_engine/
          ion.json
      recipe/
        ion_engine_module.json
```

Put `pack.mcmeta` and `data/` at the ZIP root. Minecraft 1.21.1 uses singular directory names such as `recipe`, `loot_table`, and `advancement`.

The [quickstart](Datapack-Quickstart) includes metadata for both the datapack and its resource pack.

## Galacticraft directories

| Content | Path |
| --- | --- |
| Celestial bodies, including planets and stars | `data/<namespace>/galacticraft/celestial_body/<name>.json` |
| Galaxies | `data/<namespace>/galacticraft/galaxy/<name>.json` |
| Physical flight and sky bodies | `data/<namespace>/galacticraft/spaceflight/<name>.json` |
| Arrival behavior | `data/<namespace>/galacticraft/celestial_teleporter/<name>.json` |
| Rocket cones | `data/<namespace>/galacticraft/rocket_cone/<name>.json` |
| Rocket bodies | `data/<namespace>/galacticraft/rocket_body/<name>.json` |
| Rocket fins | `data/<namespace>/galacticraft/rocket_fin/<name>.json` |
| Rocket boosters | `data/<namespace>/galacticraft/rocket_booster/<name>.json` |
| Rocket engines | `data/<namespace>/galacticraft/rocket_engine/<name>.json` |
| Rocket upgrades | `data/<namespace>/galacticraft/rocket_upgrade/<name>.json` |
| Schematic acquisition hints | `data/<namespace>/galacticraft/research_source/<name>.json` |
| Oxygen-producing block states | `data/<namespace>/oxygen/blocks.json` |

Keep the second directory name, `galacticraft`, when adding entries under your namespace. For example, `data/examplepack/galacticraft/rocket_engine/ion.json` defines `examplepack:ion` in the engine registry.

## Types and entries

A part or celestial body usually contains a `type` and a `config` object. `type` selects behavior already supplied by a mod. `config` provides its settings.

The ion engine uses `galacticraft:basic`. Its own entry ID is `examplepack:ion`. Each rocket slot has its own registry, so `galacticraft:basic` has different required settings for engines, bodies, and fins. See [rocket parts](Rocket-Parts).

Galaxies and flight entries use their own formats without an outer `type`/`config` wrapper.

## IDs used by a part

| Use | Ion engine value |
| --- | --- |
| Part ID in module data | `examplepack:ion` |
| Research ID in a schematic | `examplepack:rocket_engine/ion` |
| Translation key | `rocket_engine.examplepack.ion` |
| Part file | `data/examplepack/galacticraft/rocket_engine/ion.json` |
| Model resource | `galacticraft:models/misc/rocket_engine_tier_2.json` |

A part ID includes its namespace and filename path. A research ID also includes the slot name. Keep IDs stable once players have saved parts or research.

## Updating a pack

| Change | Apply it with |
| --- | --- |
| Parts, bodies, galaxies, flight entries, dimensions, or dimension types | Reopen the world or restart the server |
| Recipes, loot, advancements, tags, oxygen data, or research hints | `/reload` |
| Client models, textures, or translations | Reload client resources with F3+T |

Install datapacks in the world's `datapacks/` folder and resource packs through the client's Resource Packs menu. The same JSON formats are used on Fabric and NeoForge.

To replace a stock entry, use its original namespace and path. To extend a stock tag, use the tag's namespace and `"replace": false`; see [hazard tags](Skies-and-Hazards).