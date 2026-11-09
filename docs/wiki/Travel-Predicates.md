# Travel predicates

Each fitted part has a travel rule. A destination is allowed when at least one part returns `allow` and no part returns `block`. A rocket whose parts all return `pass` cannot travel.

Cone, body, fin, and engine must all resolve to valid entries. Optional boosters and upgrades can also restrict a destination.

## Rule types

Put the rule inside the part's `config.predicate` field.

| Type | `config` | Behavior |
| --- | --- | --- |
| `galacticraft:access_weight` | `{ "weight": 4, "default": "pass" }` | Allows tiered bodies up to that weight and blocks higher weights. Uses `default` for bodies without a tier; omitted `default` is `pass`. |
| `galacticraft:constant` | `"pass"`, `"block"`, or `"allow"` | Always returns that value. |
| `galacticraft:default` | `{}` | Always returns `pass`. |
| `galacticraft:and` | Array of rules | Returns `block` if any child blocks, otherwise `allow` if any child allows, otherwise `pass`. |
| `galacticraft:or` | Array of rules | Returns `allow` if any child allows, otherwise `block` if any child blocks, otherwise `pass`. |

An allowance inside `or` wins over a block in the same `or`. A block from another fitted part still refuses the trip.

## Ion engine rule

From [ion.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/examplepack/galacticraft/rocket_engine/ion.json), inside `config.predicate`:

```json
{
  "type": "galacticraft:access_weight",
  "config": {
    "weight": 2
  }
}
```

This engine permits access weights up to 2. A lower-tier cone, body, or fin can still block those destinations. Raising an engine's weight alone does not raise the whole rocket's travel capability.

## Destination weights

Set `access_weight` in a planet's configuration. Andromeda Prime uses 4, so its rocket needs fitted parts that allow weight 4.

Galaxy membership and body names do not add travel permissions. Rules are embedded in part files; there is no standalone `travel_predicate/` datapack directory.