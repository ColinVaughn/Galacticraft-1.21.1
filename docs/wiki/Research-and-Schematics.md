# Research and schematics

Survival players need research for the structural parts they assemble. A crafting recipe gives them a module; a schematic or advancement unlocks the part.

## Research IDs

A research ID includes the part's slot before its entry path:

| Part ID and slot | Research ID |
| --- | --- |
| `examplepack:ion` engine | `examplepack:rocket_engine/ion` |
| `galacticraft-test:tier_4` cone | `galacticraft-test:rocket_cone/tier_4` |
| `galacticraft-test:tier_4` body | `galacticraft-test:rocket_body/tier_4` |
| `galacticraft-test:tier_4` fin | `galacticraft-test:rocket_fin/tier_4` |
| `galacticraft-test:tier_4` engine | `galacticraft-test:rocket_engine/tier_4` |

Keep these IDs stable after players have unlocked them.

## Craft a schematic

Output `galacticraft:rocket_schematic` with the `galacticraft:rocket_part_unlocks` component. The ion schematic recipe is:

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

Add several research IDs to the list when one schematic should unlock a full tier. Use an existing schematic item; adding unlock components to an arbitrary item does not make it valid in the workbench's schematic slot.

For a loot-based schematic, use the [ion schematic loot table](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/examplepack/loot_table/ion_schematic.json) as a starting point. Its `minecraft:set_components` function attaches the same unlock list to the dropped item.

## Use the schematic

Place it on the workbench's Add New Schematic page and press Unlock. New research consumes one schematic and opens the rocket assembly page. If all its listed parts are already known, the page opens without consuming another copy.

Two schematics made from the same base item can unlock different parts through their component lists. Creative mode bypasses research checks, so use a fresh survival player when testing acquisition.

## Advancement rewards

Add `rocket_parts` to an advancement's `rewards` object to grant research when its criteria are completed. This is the rewards section from the stock [tier-3 schematic advancement](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/minecraft/advancement/galacticraft/tier_3_rocket_schematic.json):

```json
{
  "rewards": {
    "rocket_parts": [
      "galacticraft:rocket_cone/tier_3",
      "galacticraft:rocket_body/tier_3",
      "galacticraft:rocket_fin/tier_3",
      "galacticraft:rocket_engine/tier_3",
      "galacticraft:rocket_booster/tier_3"
    ]
  }
}
```

Use the surrounding advancement's criteria to control when the reward is granted. Vanilla recipe-book rewards unlock recipes; `rocket_parts` unlocks Galacticraft part research.

## Workbench hints

A source hint tells the player where to obtain missing research. Add a file under `data/<namespace>/galacticraft/research_source/`:

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

`research` lists the affected research IDs. `description` is a text component shown by the workbench. Supply the recipe or loot separately, and keep its description up to date.

Hints support `/reload`. Packs override the same hint path by normal pack priority. If different files describe the same research ID, the lexically last resource ID wins.