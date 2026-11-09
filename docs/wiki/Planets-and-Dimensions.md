# Planets and dimensions

A landable planet needs a celestial-body entry and a Minecraft dimension. The body's `world` field connects the two.

Andromeda Prime is a flat world orbiting Alpheratz. These example files use types and textures already provided by Galacticraft.

## Files

```text
data/galacticraft-test/
  galacticraft/galaxy/andromeda.json
  galacticraft/celestial_body/alpheratz.json
  galacticraft/celestial_body/andromeda_prime.json
  galacticraft/spaceflight/alpheratz.json
  galacticraft/spaceflight/andromeda_prime.json
  dimension_type/andromeda_prime.json
  dimension/andromeda_prime.json
```

The galaxy and star are covered in [galaxies and star systems](Galaxies-and-Star-Systems). The flight entries are covered in [physical spaceflight](Physical-Spaceflight).

The galaxy icon uses `assets/galacticraft-test/icon.png`. Copy that asset into your resource pack or change the galaxy display to a texture included with Galacticraft. Hazard tags are optional; see [skies and hazards](Skies-and-Hazards).

## 1. Define the planet

`data/galacticraft-test/galacticraft/celestial_body/andromeda_prime.json`

[andromeda_prime.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/galacticraft/celestial_body/andromeda_prime.json)

```json
{
  "type": "galacticraft:planet",
  "config": {
    "parent": "galacticraft-test:alpheratz",
    "access_weight": 4,
    "atmosphere": {
      "composition": {
        "galacticraft:argon": 19000.0,
        "galacticraft:carbon_dioxide": 949000.0,
        "galacticraft:carbon_monoxide": 800.0,
        "galacticraft:nitrogen": 26000.0,
        "galacticraft:oxygen": 1400.0
      },
      "pressure": 0.006,
      "temperature": -63.0
    },
    "celestial_handler": "galacticraft:default",
    "day_length": 24600,
    "day_temperature": 20,
    "description": {
      "text": "Example tier 4 planet; only a rocket with tier 4 parts gets here."
    },
    "display": {
      "type": "galacticraft:icon",
      "config": {
        "height": 16,
        "scale": 1.0,
        "texture": "galacticraft:textures/gui/celestialbodies/neptune.png",
        "u": 0,
        "v": 0,
        "width": 16
      }
    },
    "gravity": 0.38,
    "name": {
      "text": "Andromeda Prime"
    },
    "night_temperature": -73,
    "position": {
      "type": "galacticraft:orbital",
      "config": {
        "distance": 1.0,
        "orbit_time": 1.8811609745025635,
        "phase_shift": 0.16670000553131104,
        "planet": true
      }
    },
    "ring": {
      "type": "galacticraft:default",
      "config": {}
    },
    "sky": {
      "sky_color": 2754629,
      "fog_color": 1706540,
      "sun_size": 3.0,
      "star_brightness": 0.8
    },
    "teleporter": "galacticraft:lander",
    "world": "galacticraft-test:andromeda_prime"
  }
}
```

`parent` names the star's celestial-body entry. `world` names the dimension. `teleporter` names the arrival behavior.

## Planet fields

| Field | Required/default | Meaning |
| --- | --- | --- |
| `name`, `description` | Required | Text components used by the map and UI. |
| `parent` | Optional | Parent celestial body. |
| `position` | Required | Map position; usually an orbital position for a planet. |
| `display` | Required | Map display settings. |
| `ring` | Required | Ring display settings. |
| `world` | Required | Minecraft dimension ID. |
| `teleporter` | Required | Configured arrival-behavior ID. |
| `atmosphere` | Required | Gas `composition`, `temperature`, and `pressure`. |
| `gravity` | Required | Gravity used by gameplay. |
| `celestial_handler` | Required | Existing handler ID; this example uses `galacticraft:default`. |
| `access_weight` | Required | Weight checked by rocket travel predicates. |
| `day_temperature`, `night_temperature` | Required | Day and night thermal conditions. |
| `day_length` | Required | Day length in game ticks. |
| `satellite_recipe` | Optional | Station-building recipe; see the stock body files for its format. |
| `sky` | Optional | [Sky colors, sun size, and effects](Skies-and-Hazards). |

Atmospheric composition uses gas IDs and parts per million. Pressure is measured in atmospheres. Oxygen is breathable when `oxygen_ppm / 1000000 * pressure` is strictly between 0.195 and 0.235 atm. Prime's 1400 ppm at 0.006 atm is unbreathable.

Set atmosphere temperature and day/night thermal settings independently. Sky colors control appearance; they do not make an atmosphere breathable.

## 2. Define the dimension type

`data/galacticraft-test/dimension_type/andromeda_prime.json`

[andromeda_prime.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/dimension_type/andromeda_prime.json)

```json
{
  "ambient_light": 0.1,
  "bed_works": true,
  "coordinate_scale": 1.0,
  "effects": "galacticraft:space",
  "has_ceiling": false,
  "has_raids": false,
  "has_skylight": true,
  "height": 384,
  "infiniburn": "#minecraft:infiniburn_overworld",
  "logical_height": 384,
  "min_y": -32,
  "monster_spawn_block_light_limit": 0,
  "monster_spawn_light_level": {
    "type": "minecraft:uniform",
    "max_inclusive": 7,
    "min_inclusive": 0
  },
  "natural": false,
  "piglin_safe": false,
  "respawn_anchor_works": false,
  "ultrawarm": false
}
```

`effects: "galacticraft:space"` enables the generic space sky. Keep compatible height and `min_y` values when changing the world dimensions.

## 3. Define the terrain

`data/galacticraft-test/dimension/andromeda_prime.json`

[andromeda_prime.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/dimension/andromeda_prime.json)

```json
{
  "type": "galacticraft-test:andromeda_prime",
  "generator": {
    "type": "minecraft:flat",
    "settings": {
      "biome": "minecraft:plains",
      "lakes": false,
      "features": false,
      "structure_overrides": [],
      "layers": [
        {
          "block": "minecraft:bedrock",
          "height": 1
        },
        {
          "block": "minecraft:end_stone",
          "height": 4
        }
      ]
    }
  }
}
```

The `type` here points to the dimension type above. The generator makes a flat plains world from bedrock and end stone.

For noise-based terrain, start with the stock [Mars dimension](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/resources/data/galacticraft/dimension/mars.json). Copy or provide any referenced noise settings, biomes, and features as well as the dimension file. New generator or block types require an installed mod.

## Arrival behavior

Prime uses the stock lander entry:

[lander.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/galacticraft/galacticraft/celestial_teleporter/lander.json)

```json
{
  "type": "galacticraft:lander",
  "config": {}
}
```

Other stock entries include `galacticraft:direct`, `galacticraft:overworld`, and `galacticraft:jupiter`. Choose behavior appropriate for the world's terrain and atmosphere. `celestial_handler` is a separate setting for parachest placement and parachute use; keep `galacticraft:default` unless an installed addon provides another handler.

## Load the planet

Restart the world or server after installing or changing these files. Confirm that the planet appears under the intended star, the dimension loads, and a rocket with sufficient access weight can reach it.

Add the matching [flight entries](Physical-Spaceflight) for physical departure, approach, and local sky bodies. Test landing, day/night lighting, gravity, oxygen, and any enabled hazards in the world.