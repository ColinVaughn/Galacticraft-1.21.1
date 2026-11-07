# Galaxies and star systems

A galaxy contains root stars. Each star points to its galaxy, and planets point to their parent body. There is no separate star-system file.

## 1. Add the galaxy

`data/galacticraft-test/galacticraft/galaxy/andromeda.json`

[andromeda.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/galacticraft/galaxy/andromeda.json)

```json
{
  "name": {
    "text": "Andromeda"
  },
  "description": {
    "text": "An example galaxy from the Galacticraft example addon."
  },
  "position": {
    "type": "galacticraft:static",
    "config": {
      "x": 600.0,
      "y": -450.0
    }
  },
  "display": {
    "type": "galacticraft:icon",
    "config": {
      "texture": "galacticraft-test:icon.png",
      "u": 0,
      "v": 0,
      "width": 460,
      "height": 460,
      "scale": 1.0
    }
  }
}
```

A galaxy has `name`, `description`, `position`, and `display` fields. Its position and display each have a `type` and `config`; the galaxy itself has no outer wrapper.

The static position sets its map placement. The icon points to `assets/galacticraft-test/icon.png`, which must be included in the client resource pack when using this example.

## 2. Add the star

`data/galacticraft-test/galacticraft/celestial_body/alpheratz.json`

[alpheratz.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/galacticraft/celestial_body/alpheratz.json)

```json
{
  "type": "galacticraft:star",
  "config": {
    "description": {
      "text": "Home star of the example Andromeda system."
    },
    "display": {
      "type": "galacticraft:icon",
      "config": {
        "height": 8,
        "scale": 1.5,
        "texture": "galacticraft:textures/gui/celestialbodies/sol.png",
        "u": 0,
        "v": 0,
        "width": 8
      }
    },
    "galaxy": "galacticraft-test:andromeda",
    "gravity": 28.0,
    "luminance": 1.0,
    "name": {
      "text": "Alpheratz"
    },
    "photospheric_composition": {
      "composition": {
        "galacticraft:helium": 248500.0,
        "galacticraft:hydrogen": 734600.0,
        "galacticraft:neon": 1200.0,
        "galacticraft:nitrogen": 900.0,
        "galacticraft:oxygen": 7700.0
      },
      "pressure": 28.0,
      "temperature": 15.0
    },
    "position": {
      "type": "galacticraft:static",
      "config": {
        "x": 0.0,
        "y": 0.0
      }
    },
    "ring": {
      "type": "galacticraft:default",
      "config": {}
    },
    "surface_temperature": 5772
  }
}
```

The star's `galaxy` field points to `galacticraft-test:andromeda`. A root star needs a valid galaxy to appear in the system list.

The required star settings are `name`, `description`, `position`, `display`, `ring`, `photospheric_composition`, `gravity`, `luminance`, and `surface_temperature`. Alpheratz reuses the Sol map icon included with Galacticraft.

## 3. Add planets

Set the planet's `parent` to `galacticraft-test:alpheratz`. [Andromeda Prime](Planets-and-Dimensions) includes the body, dimension, and dimension-type files.

An orbital position uses `orbit_time`, `distance`, and `phase_shift`; its optional `planet` flag defaults to `true`. Static positions use `x` and `y`. These settings control the map; use [flight entries](Physical-Spaceflight) to position bodies in physical space.

## Map and sky

The selection map groups stars by galaxy and planets by their parent chain. Galaxies without a valid root star are omitted. The Systems control appears when more than one system is available.

For local skies, provide flight entries for the star and visible planets, including their texture references. The map icon alone does not supply a sky body.

Galaxy membership adds no travel tier. Set each planet's `access_weight` and the rocket parts' [travel predicates](Travel-Predicates) to control access.

Restart the world or server after changing galaxies, stars, or their parent references.