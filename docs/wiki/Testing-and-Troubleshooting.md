# Testing and troubleshooting

## Load the pack

1. Put the datapack in the world's `datapacks/` folder. A ZIP must have `pack.mcmeta` and `data/` at its root.
2. Enable its resource pack on each client.
3. Reopen the world or restart the server after adding or editing parts, bodies, galaxies, flight entries, or dimensions.
4. Use `/datapack list enabled` to check that the datapack loaded. Read `logs/latest.log` for invalid JSON, missing fields, and unknown IDs.

Recipes, loot, advancements, tags, oxygen data, and research hints support `/reload`. Client models, textures, and translations reload with F3+T.

## Test a rocket part

Use a fresh survival player so creative mode does not hide missing research.

1. Obtain the module and schematic through their intended recipes or loot.
2. Confirm assembly is blocked before research, then spend the schematic at the workbench.
3. Fit the module and check the output. Try removing and replacing it through refit.
4. Check the module name, assembled model, paint, seats, steering, and collision size as applicable.
5. Fuel the rocket and test both an allowed destination and one above its access weight.
6. Save and reopen the world, then check that the fitted parts, upgrades, and research remain correct.

## Test a planet

Check its map grouping, dimension loading, terrain, gravity, atmosphere, day/night cycle, sky, and hazards. Fly from it into physical space and approach it again to check landing. Repeat on each loader your pack supports.

## Common problems

| Problem | Check |
| --- | --- |
| Engine or other part is missing | File path under `data/<namespace>/galacticraft/rocket_<slot>/`, required fields, enabled pack, and server restart. |
| Unknown `type` | The type exists in Galacticraft or an installed addon. A datapack cannot register a new behavior type. |
| Part is invisible | `model` resource path, `.json` extension, referenced OBJ/MTL files, and enabled client resource pack. |
| Missing textures | JSON and material-file paths, namespaces, and texture files in the resource pack. |
| Module fits the wrong well | Keep one part field in its `galacticraft:rocket_data` component. |
| Upgrade module is rejected | Use one resolvable upgrade module and leave the other upgrade wells empty. |
| Recipe gives a blank module | Put the component on the result stack. Use `id` for the result item. |
| Schematic does nothing | Use `galacticraft:rocket_schematic` with a nonempty `galacticraft:rocket_part_unlocks` list containing slot-qualified research IDs. |
| Missing research has no useful hint | Add a `galacticraft/research_source` file and run `/reload`. |
| A high-tier engine cannot reach a planet | Check every fitted part, including optional boosters and upgrades, for a blocking travel rule. |
| All rules return `pass` and travel fails | At least one fitted part must return `allow`. |
| Engine capacity or burn is unexpected | Check whether the server uses GC4 fuel mode or uniform fuel mode. |
| Planet is missing from the map | Parent star, galaxy reference, landable body type, and valid dimension. |
| Planet appears on the map but cannot be entered | `world`, dimension and dimension-type files, teleporter entry, and travel predicates. |
| Planet is absent from physical flight | Matching `spaceflight` entry, resolvable body, landable behavior, loaded dimension, and server restart. |
| Custom sun or nearby planet is absent from the sky | Flight entries, root-star parent chain, textures, and nonzero `sun_size`. |
| Hazard does not apply | Use the stock tag namespace and add the celestial-body ID, not a biome or dimension ID. |
| Sky settings do not apply | Dimension type uses `galacticraft:space`, and the body's `world` points to that dimension. |
| Oxygen value does not apply | Exact block state, all relevant properties, duplicate entries, and `replace: true` when overriding. |

When reporting a problem, include the Minecraft version, Galacticraft build, loader, affected JSON files, reproduction steps, and relevant log messages. For visual problems, include the model descriptor and its referenced assets.