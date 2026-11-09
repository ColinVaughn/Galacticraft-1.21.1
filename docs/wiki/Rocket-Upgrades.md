# Rocket upgrades

A rocket carries one configured upgrade. Galacticraft supplies storage and explosive upgrade types. Upgrade files go in `data/<namespace>/galacticraft/rocket_upgrade/`.

## Storage

To change the stock storage upgrade, override `data/galacticraft/galacticraft/rocket_upgrade/storage.json`:

[storage.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/galacticraft/galacticraft/rocket_upgrade/storage.json)

```json
{
  "type": "galacticraft:storage",
  "config": {
    "chests": 3,
    "recipe": {
      "type": "galacticraft:centered_patterned",
      "config": {
        "height": 18,
        "slots": [
          {
            "ingredient": {
              "item": "minecraft:chest"
            },
            "x": -9,
            "y": 0
          }
        ]
      }
    }
  }
}
```

`chests` sets the maximum number of installed storage items used to calculate cargo space. `recipe` defines the upgrade module's workbench pattern.

Players can install storage through the assembly page's upgrade wells. Accepted items come from `galacticraft:rocket_storage_upgrade_items`; the stock tag includes wooden chests and barrels:

[rocket_storage_upgrade_items.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/galacticraft/tags/item/rocket_storage_upgrade_items.json)

```json
{
  "values": [
    "#c:chests/wooden",
    "#c:barrels/wooden"
  ]
}
```

Extend that tag at `data/galacticraft/tags/item/rocket_storage_upgrade_items.json` if your pack should accept other existing items. Cargo depends on the number of occupied upgrade wells.

## Explosives

To change the stock explosive upgrade, override `data/galacticraft/galacticraft/rocket_upgrade/explosive.json`:

[explosive.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/galacticraft/galacticraft/rocket_upgrade/explosive.json)

```json
{
  "type": "galacticraft:explosive",
  "config": {
    "recipe": {
      "type": "galacticraft:centered_patterned",
      "config": {
        "height": 18,
        "slots": [
          {
            "ingredient": {
              "item": "minecraft:tnt"
            },
            "x": -9,
            "y": 0
          }
        ]
      }
    }
  }
}
```

| Setting | Default | Meaning |
| --- | --- | --- |
| `blast_radius_multiplier` | `1.0` | Blast-size multiplier. |
| `fuse_ticks` | `80` | Fuse length in ticks. |
| `recipe` | Required | Workbench module pattern. |

An explosive block goes in the first upgrade well, with the other wells empty. Its item must place a TNT block, including a modded TNT block supplied by an installed mod.

## Upgrade modules

A recipe can output `galacticraft:rocket_module` with an `upgrade` field in its `galacticraft:rocket_data` component. Use the configured upgrade ID. See [module recipes](Recipes-and-Workbench) for the result-stack format.

Install one upgrade module in an otherwise empty set of upgrade wells. Its `upgrade_data` is preserved when fitted or removed. During refit, the first upgrade well exchanges the upgrade; the second exchanges a booster.

Storage and explosive module patterns are available without structural-part research. If several configured upgrades accept the same ingredients, the workbench chooses the first matching upgrade by its full registry ID.

Custom upgrade behaviors, such as the repository's fuel-pod example, require an addon mod that registers their type. A datapack can configure an installed type but cannot add the Java behavior itself.