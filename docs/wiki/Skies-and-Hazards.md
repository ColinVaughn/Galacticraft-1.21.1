# Skies and hazards

## Planet sky

Set `effects` to `galacticraft:space` in the dimension type, then add a `sky` object to the planet's configuration. Andromeda Prime uses:

```json
{
  "sky_color": 2754629,
  "fog_color": 1706540,
  "sun_size": 3.0,
  "star_brightness": 0.8
}
```

This is the `config.sky` section of [andromeda_prime.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft-test/galacticraft/celestial_body/andromeda_prime.json).

| Field | Default | Meaning |
| --- | --- | --- |
| `sky_color` | `0` | Packed RGB color. Zero selects an airless-looking sky. |
| `fog_color` | `0` | Packed RGB fog color. |
| `sun_size` | `2.0` | Size multiplier for the sun. Zero hides it. |
| `star_brightness` | `1.0` | Star brightness from 0 to 1. |
| `post_effect` | Absent | Client post-processing JSON resource. |

Sky and fog colors change with daylight. An airless-looking sky leaves stars visible during the day. Breathability still comes from the planet's atmosphere settings.

For post-processing, see the stock [Mars body](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/generated/data/galacticraft/galacticraft/celestial_body/mars.json) and [Mars post effect](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/resources/assets/galacticraft/shaders/post/mars.json). Custom shader resources belong in the client resource pack.

## Sun and nearby bodies

Add [flight entries](Physical-Spaceflight) for the root star and the planets you want visible. Their textures and positions supply the sky bodies. The root star's `sky_radius` sets its apparent radius, and the observer planet's `sun_size` scales it.

Stock planets can use specialized dimension effects. Start with `galacticraft:space` for a new planet unless you need the behavior of a specific stock renderer.

## Hazard tags

Hazards use celestial-body tags:

| Tag | Stock planet | Effect |
| --- | --- | --- |
| `galacticraft:dust_storms` | Mars | Dust storms, exposure, overlay, and sound. |
| `galacticraft:solar_flares` | Mercury | Solar flares and their visual effects. |
| `galacticraft:gas_giant` | Jupiter | Gas-giant entry restrictions, depth pressure and temperature, storms, cloud fog, and related machine behavior. |

To give Prime dust storms, add this file:

`data/galacticraft/tags/galacticraft/celestial_body/dust_storms.json`

[dust_storms.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/data/galacticraft/tags/galacticraft/celestial_body/dust_storms.json)

```json
{
  "replace": false,
  "values": [
    "galacticraft-test:andromeda_prime"
  ]
}
```

Keep `galacticraft` as the first directory after `data/` when extending Galacticraft's tag. Putting the same filename under `data/examplepack/` creates a different tag. `replace: false` keeps the existing members.

Tags enable the stock hazard rules. They do not add terrain or expose per-planet tuning for every hazard. The gas-giant tag applies Jupiter behavior with altitude and terrain assumptions; test it carefully on another world.

## Test the environment

Check the sky at different times of day and heights. Test hazards with and without shelter or protective equipment, and confirm that oxygen and thermal conditions match the planet settings.

Use `/reload` for tag changes and F3+T for client resource changes. Restart the world or server after changing the body's sky configuration or dimension type.