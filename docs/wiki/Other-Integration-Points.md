# Oxygen-producing blocks

Use `data/<namespace>/oxygen/blocks.json` to set how much oxygen a block state contributes to a collector. The file contains an array of entries.

## Entry format

The first entry in the generated [blocks.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/galacticraft/oxygen/blocks.json) is:

```json
[
  {
    "amount": 1.0,
    "blockState": {
      "Name": "minecraft:oak_leaves",
      "Properties": {
        "distance": "1",
        "persistent": "false",
        "waterlogged": "true"
      }
    }
  }
]
```

| Field | Required/default | Meaning |
| --- | --- | --- |
| `blockState` | Required | Block ID and state properties. |
| `amount` | Required | Oxygen contribution. |
| `replace` | `false` | Replaces an existing entry for the same block state when set to `true`. |

Each entry matches one exact state. The example covers distance 1, nonpersistent, waterlogged oak leaves. Add entries for the other property combinations you want to change.

## Defaults

The stock data gives nonpersistent leaves an amount of 1.0 and crops 0.75. Leaves and crops without an explicit entry fall back to 0.75; other blocks fall back to zero. A modded block with its own oxygen behavior can override the table.

## Replacing values

Set `"replace": true` when overriding an existing state. Without it, the earlier value is kept and a duplicate warning is logged. Keep related overrides in one namespace and avoid depending on the loading order of unrelated namespaces.

Run `/reload` after editing the file. If an entry has no effect, check the exact state properties and the server log for skipped files or duplicate entries.