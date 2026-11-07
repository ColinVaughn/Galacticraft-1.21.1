# Galacticraft datapacks

Guides for adding rocket parts, recipes, research, planets, and star systems to Galacticraft for Minecraft 1.21.1.

Start with the [ion engine tutorial](Datapack-Quickstart). It includes the engine definition, crafting recipes, schematic, workbench hint, and translation file.

| Task | Guide |
| --- | --- |
| Set up folders, namespaces, and pack metadata | [Datapack structure](Architecture-and-Registries) |
| Add a rocket part or a complete tier | [Rocket parts](Rocket-Parts) |
| Set destination access rules | [Travel predicates](Travel-Predicates) |
| Configure storage and explosive upgrades | [Rocket upgrades](Rocket-Upgrades) |
| Unlock parts and describe where to find schematics | [Research and schematics](Research-and-Schematics) |
| Add crafting, machine, and workbench recipes | [Recipes and workbench](Recipes-and-Workbench) |
| Add a planet and its terrain | [Planets and dimensions](Planets-and-Dimensions) |
| Add a galaxy and star system | [Galaxies and star systems](Galaxies-and-Star-Systems) |
| Place bodies in physical space and the sky | [Physical spaceflight](Physical-Spaceflight) |
| Configure sky colors and hazards | [Skies and hazards](Skies-and-Hazards) |
| Add models, textures, and names | [Models and resources](Rendering-and-Resources) |
| Change oxygen production from blocks | [Oxygen blocks](Other-Integration-Points) |
| Diagnose loading and gameplay problems | [Testing and troubleshooting](Testing-and-Troubleshooting) |

Datapacks configure the types provided by Galacticraft and any installed addons. New Java behavior requires a mod. Custom names, textures, and models go in a resource pack installed on each client.

Examples use the repository's `examplepack` ion engine, `galacticraft-test` planet system, and generated stock data. Copy the files into your own pack; the example namespaces are development content and are not bundled with the released mod.

Restart the world or server after changing parts, celestial bodies, galaxies, flight entries, or dimensions. Recipes, loot, tags, oxygen data, and research hints support `/reload`.