# Models and resources

Put models, textures, and translations in a resource pack. Every client needs that pack when it connects to a server using the corresponding datapack.

## Choose a part model

Set `config.model` to a full resource path, including `models/misc/` and `.json`. The ion engine uses `galacticraft:models/misc/rocket_engine_tier_2.json`; the tier-4 parts reuse the stock tier-3 models.

Galacticraft uses this model when no mod has registered a renderer for that part. A missing model leaves the part invisible and writes a warning to the log.

## Model descriptor

The tier-2 engine's descriptor is:

[rocket_engine_tier_2.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/main/resources/assets/galacticraft/models/misc/rocket_engine_tier_2.json)

```json
{
  "galacticraft:type": "galacticraft:obj",
  "model": "galacticraft:models/misc/rocket_engine_tier_2.obj",
  "mtl": "galacticraft:models/misc/rocket_engine_tier_2.mtl",
  "paint_texture": "galacticraft:obj/rocket_engines_paint"
}
```

`galacticraft:type` selects the model loader. This OBJ descriptor points to an `.obj`, an `.mtl`, and a paint texture. Include each referenced file when making your own model.

```text
assets/<namespace>/
  models/misc/<model>.json
  models/misc/<model>.obj
  models/misc/<model>.mtl
  textures/<texture>.png
```

Use the paths referenced by your descriptor and material file; the names above show the folder layout. Check texture references inside the MTL as well as the JSON.

## Size and paint

A part's `layout` controls its dimensions and where the other parts mount. Model geometry controls its appearance. Keep both in proportion; see [rocket parts](Rocket-Parts) for layout units.

Set `paintable: true` when the model has suitable paint materials. Stock descriptors show the `paint_texture` format. Try the model on an assembled rocket and in the paint screen to check the finish.

## Names

Part translation keys include the slot, namespace, and entry path. The ion engine's key is `rocket_engine.examplepack.ion`:

`assets/examplepack/lang/en_us.json`

[en_us.json](https://github.com/ColinVaughn/Galacticraft-1.21.1/blob/main/fabric/src/test/resources/assets/examplepack/lang/en_us.json)

```json
{
  "rocket_engine.examplepack.ion": "Ion Engine"
}
```

Galaxies and celestial bodies use text components in their `name` and `description` fields, which can contain literal text or translation keys.

## Check the resource pack

Reload client resources with F3+T after editing assets. Check the module item, assembled rocket, and refit result for missing textures or misplaced geometry. Look for missing-model and texture-path errors in `logs/latest.log`. Test on each loader your pack supports.