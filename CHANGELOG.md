# Changelog

## 5.4.21 - 2026-11-28

Minecraft 1.21.1. Separate builds are provided for Fabric and NeoForge. Java 21 is required.

### Additions

- Colored aluminum wires. Existing wires become gray wires.
- Status display on the oxygen bubble distributor and fill gauges on storage modules.
- The oxygen compressor shows the tank inside it.
- A creative tab per world; machines are grouped in the Machines tab.
- Sculk sensors hear further in thick atmospheres and only through contact in a vacuum.

### Fixes

- Rockets keep their leftover fuel when packed into an item.
- Arriving at a space station returns your rocket, launch pad and cargo, without fall damage.
- The bubble distributor stops its bubble when it loses power.

### Changes

- Removed the alpha warning screen.

## 5.4.20-prealpha - 2026-11-11

Minecraft 1.21.1. Separate builds are provided for Fabric and NeoForge. Java 21 is required.

### Fixes

- Fixed a rocket launch crash in dimensions without a registered Galacticraft celestial body, including dimensions added by Custom Dimensions.
- Fixed slow pickaxe mining and missing drops for decorative stairs, slabs, and walls.
- Fixed double decorative slabs dropping only one slab instead of two.
- Enabled default machine item automation: hoppers insert from above and extract from below. Custom port configurations are preserved.
- Allowed hoppers to supply fuel to coal-powered compressors.

### Updating

- Update Galacticraft on both clients and servers; the celestial-screen packet format changed.

## 5.4.19-prealpha - 2026-11-08

Minecraft 1.21.1. Separate builds are provided for Fabric and NeoForge. Java 21 is required.

### Fixes

- Fixed a startup crash in packs with ModernFix (`MixinTargetAlreadyLoadedException` for `ExtraCodecs$TagOrElementLocation`). 5.4.18 shipped an older Dynamic Dimensions build whose Sable compatibility code loaded Minecraft classes too early.
- Restored the Dynamic Dimensions build from 5.4.17. This also brings back NeoForge level load and unload events for dynamic dimensions such as space stations.

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