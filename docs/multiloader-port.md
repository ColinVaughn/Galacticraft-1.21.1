# Multi-loader port plan

Galacticraft is moving from a Fabric-only project to the standard Architectury three-module layout:

- `common`: loader-neutral code and Architectury APIs.
- `fabric`: the Fabric loader adapter and APIs that have not yet been abstracted.
- `neoforge`: the NeoForge loader adapter and native NeoForge integrations.

During development Gradle uses the newest non-classifier loader jar under the adjacent `../MachineLib/<module>/build/libs` directory. Run MachineLib's `build` task after changing it. If the checkout or jars are absent, Gradle resolves the published `MachineLib-common`, `MachineLib-fabric`, and `MachineLib-neoforge` coordinates instead.

## Current milestone

The module/build split and both loader bootstraps are in place. Existing gameplay code remains in `fabric` so the known-good implementation is preserved while subsystems are migrated. The NeoForge module currently proves dependency resolution, transformation, packaging, metadata, and loader startup; it is not yet a playable Galacticraft build.

## Migration order

1. Registries and common initialization
   - Convert direct vanilla/Fabric registration to Architectury `DeferredRegister`/`RegistrySupplier`.
   - Move content definitions and codecs to `common`.
   - Keep loader event-bus wiring in the platform modules.
2. Machine storage and menus
   - Replace Fabric Transfer API types in Galacticraft public/common code with the loader-neutral MachineLib storage API.
   - Register Fabric lookups and NeoForge capabilities in their platform modules.
   - Move extended menus to Architectury `MenuRegistry`.
3. Networking
   - Replace Fabric payload receiver registration with Architectury `NetworkManager`.
   - Keep packet payload records and handlers in `common`.
4. Lifecycle, interaction, commands, and reload listeners
   - Use Architectury events where equivalent.
   - Add small `@ExpectPlatform` bridges where loader semantics differ.
5. Client registration and rendering
   - Move screens, particles, key mappings, entity renderers, and model-layer definitions to `common` where APIs permit.
   - Implement Fabric Renderer API and NeoForge model/render hooks separately.
6. World generation and dynamic dimensions
   - Move codecs and data definitions to `common`.
   - Replace the Fabric-only DynamicDimensions dependency with a loader-neutral interface and a native NeoForge implementation before enabling satellite creation on NeoForge.
7. Datagen, compatibility integrations, and tests
   - Maintain loader-specific datagen/test-mod source sets.
   - Add native NeoForge JEI/REI/EMI/Jade integrations.
   - Require `fabric:build` and `neoforge:build` in CI.

## Definition of supported

A loader is supported only after its client and dedicated-server smoke tests start, content registries freeze successfully, a test world opens, networking connects, machines expose native storage/capabilities, and the distributable jar contains the common classes and resources. Until those checks pass, NeoForge artifacts are development previews.

## Developer commands

Use Java 21.

```text
./gradlew :fabric:build
./gradlew :neoforge:build
./gradlew :fabric:runClient
./gradlew :neoforge:runClient
```
