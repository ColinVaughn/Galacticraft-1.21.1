# Galacticraft datapack guide

Start with the [ion engine tutorial](wiki/Datapack-Quickstart.md) to add a part, module recipe, schematic, workbench hint, and translation file.

The [wiki home](wiki/Home.md) links the full set of guides for Minecraft 1.21.1.

| Task | Guide |
| --- | --- |
| Set up a pack | [Folders, namespaces, and reloads](wiki/Architecture-and-Registries.md) |
| Add rocket parts | [Part settings](wiki/Rocket-Parts.md) and [travel rules](wiki/Travel-Predicates.md) |
| Add upgrades | [Storage and explosives](wiki/Rocket-Upgrades.md) |
| Unlock parts | [Research and schematics](wiki/Research-and-Schematics.md) |
| Change crafting | [Recipes and workbench](wiki/Recipes-and-Workbench.md) |
| Add a planet | [Planets and dimensions](wiki/Planets-and-Dimensions.md) |
| Add a star system | [Galaxies and stars](wiki/Galaxies-and-Star-Systems.md) and [physical spaceflight](wiki/Physical-Spaceflight.md) |
| Configure the environment | [Skies and hazards](wiki/Skies-and-Hazards.md) and [oxygen blocks](wiki/Other-Integration-Points.md) |
| Supply client assets | [Models, textures, and names](wiki/Rendering-and-Resources.md) |
| Diagnose a problem | [Testing and troubleshooting](wiki/Testing-and-Troubleshooting.md) |

Example files are linked beside the JSON they illustrate. The ion engine is under `fabric/src/test/resources/data/examplepack/`; the tier-4 parts and Andromeda system are under `fabric/src/test/resources/data/galacticraft-test/`. Copy the needed files into your own pack and change their namespace consistently.
