# Physical spaceflight

Flight entries place celestial bodies in physical space and supply the bodies drawn in local skies. Put them in `data/<namespace>/galacticraft/spaceflight/`.

Map positions are configured separately in each body's `position` field. A planet also needs a dimension before players can land on it.

## Star entry

`data/galacticraft-test/galacticraft/spaceflight/alpheratz.json`

[alpheratz.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/galacticraft/spaceflight/alpheratz.json)

```json
{
  "body": "galacticraft-test:alpheratz",
  "center": [
    60000,
    1024,
    0
  ],
  "radius": 1200,
  "sky_radius": 50,
  "texture": "galacticraft:textures/environment/solar_system/sun.png",
  "rotation_period": 25
}
```

## Planet entry

`data/galacticraft-test/galacticraft/spaceflight/andromeda_prime.json`

[andromeda_prime.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/galacticraft/spaceflight/andromeda_prime.json)

```json
{
  "body": "galacticraft-test:andromeda_prime",
  "center": [
    68000,
    1024,
    2000
  ],
  "radius": 60,
  "texture": "galacticraft:textures/environment/solar_system/mars.png",
  "rotation_period": 1.025,
  "axial_tilt": 20
}
```

The Alpheratz file supplies the system's star. The Prime file supplies the planet's arrival and departure target. Both reuse textures included with Galacticraft.

## Fields

| Field | Required/default | Meaning |
| --- | --- | --- |
| `body` | Required | Celestial-body ID. |
| `center` | Required | `[x, y, z]` in the shared physical-space dimension. Each coordinate must be finite and inside +/-29000000. |
| `radius` | Required, 1-1000000 | Half-width of the rendered cube, also used for atmospheric entry distance. |
| `texture` | Required | Client texture resource. |
| `rotation_period` | `0`, range 0-1000000000 | Rotation period in game days; zero stops rotation. |
| `axial_tilt` | `0`, range -360 to 360 | Spin-axis tilt in degrees. |
| `phase` | `0`, range -360 to 360 | Starting rotation in degrees. |
| `sky_radius` | `42`, range 0.01-1000000 | Star radius in surface skies, scaled by the observer's `sun_size / 2`. |

## Place a system

Use one flight entry per body. Leave enough space between centers for their full radii, rotation, and approach paths. Physical space is shared, so a new system must also avoid the stock bodies.

The parent chain determines which star and planets appear in a surface sky. Include flight entries for the root star and the planets you want visible. The star's map icon does not set its sky texture.

A body without landable behavior can be drawn, but cannot be an atmospheric-entry destination. An unresolved body ID is omitted from flight targets.

## Try the route

Restart the world or server after editing flight entries. Launch from the planet, check its position in space, then approach and land. Also check the surface sky and the planet's appearance from high altitude.

See [planets and dimensions](Planets-and-Dimensions) for the matching body and world files, and [skies and hazards](Skies-and-Hazards) for surface-sky settings.