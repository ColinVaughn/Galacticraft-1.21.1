# Changelog

## 5.4.18-prealpha - 2026-11-07

Minecraft 1.21.1. Separate builds are provided for Fabric and NeoForge. Java 21 is required.

### Jupiter

- Added an explorable Jupiter dimension with cloud banks, Amber Belts, Crystal Fronts, Calm Eyes, and Red Vortex regions.
- Added layered atmospheric exploration, pressure and temperature hazards, storms, and protected habitats.
- Added weather stations, research facilities, wrecks, and Stormheart citadels.
- Added the Cloud Diver vehicle with energy, oxygen, hull damage, cargo, resource collection, docking, and emergency behavior.
- Added atmospheric collectors, condensate separators, pressure stabilizers, lightning grounders, storm capacitors, survey beacons, and vehicle docks.
- Added Cloud Rays, Static Jellies, Salvage Drones, and the Stormheart Leviathan encounter.
- Added Jupiter materials, components, building blocks, recipes, loot, models, textures, sounds, and machine interfaces.

### Spaceflight and rockets

- Added physical flight between celestial bodies, with departure and atmospheric-entry transitions, rotating planet surfaces, approach rendering, and destination HUD information.
- Added configured flight entries for body positions, sizes, textures, rotation, and local star-system skies. Datapack planets can participate in physical travel.
- Updated planet lighting, high-altitude surface rendering, and space visuals.
- Expanded rocket refitting, module exchange, paint handling, research checks, and workbench navigation.
- Added workbench crafting pages for patterns embedded in configured rocket parts. Existing crafting-table recipes remain usable.
- Added generic upgrade modules and preserved their saved payloads through assembly and refitting.
- Improved rocket, launch-pad, storage-upgrade, and cargo persistence across saves, reloads, and docking.
- Added automatic-lander support and updated lander behavior and models.

### Gameplay and client changes

- Added the Curiosity Rover entity on Mars.
- Reworked the flag editor with layers, visibility controls, patterns, drawing tools, and undo/redo. Saved flags retain their layers when reopened.
- Updated team-flag synchronization and rendering.
- Improved fitted space-gear rendering and optional GeckoLib compatibility.
- Fixed Mars dust-storm shelter and breathable-air protection checks.
- Updated oxygen bubbles, sealing, airlocks, and vacuum-related behavior.
- Updated boss, dungeon, Moon-villager, and chunk-lifecycle handling.

### Addons and datapacks

- Added reloadable research-source descriptions for component-based schematics, including workbench acquisition hints.
- Added a fuel-capacity hook for rocket upgrades; the example fuel pods now add capacity and preserve their data across saves.
- Fixed recipe-builder namespaces and prefixed recipe advancement rewards.
- Fixed block-wide oxygen data replacement so every generated state receives the replacement flag.
- Fixed wrapped workbench patterns to match their left and right ingredient lists correctly.
- Fixed prefilled module pages so results appear immediately after the ingredient layout changes.
- Changed `or` travel predicates so any child allowance satisfies that composite. Travel across fitted parts still requires at least one allowance and no blocking part.
- Made atmospheric breathability account for oxygen partial pressure.
- Fixed stale celestial-body bindings and added explicit cache invalidation for addon registry changes.
- Made shared recipe-generation helpers available on both loaders and added a native NeoForge addon example.
- Completed the development examples with tier-4 models, module and schematic recipes, ion-engine research hints, and flight data for Andromeda Prime.
- Added datapack guides for parts, recipes, research, upgrades, planets, galaxies, flight, skies, models, and oxygen data.

### Updating

- Install the jar for your loader and Minecraft 1.21.1. Do not install both loader builds together.
- Back up existing worlds before updating this pre-alpha build.
- Restart the world or server after changing configured parts, celestial bodies, galaxies, flight entries, or dimensions. Recipes, loot, tags, oxygen data, and research hints support `/reload`.
- Example addon content under the test namespaces is development material; it is not included as normal release content.