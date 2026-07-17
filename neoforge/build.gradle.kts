plugins {
    java
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}


val localMachineLib = rootProject.file("../MachineLib/neoforge/build/libs").listFiles()
    ?.filter { it.name.startsWith("MachineLib-neoforge-") && it.extension == "jar" }
    ?.filterNot { it.name.contains("-sources") || it.name.contains("-javadoc") || it.name.contains("-shadow") }
    ?.maxByOrNull { it.lastModified() }
val localDynamicDimensionsRoot = rootProject.file(
    rootProject.findProperty("dynamicdimensions.local.path")?.toString() ?: "../DynamicDimensions"
)
val localDynamicDimensions = localDynamicDimensionsRoot.resolve("neoforge/build/libs").listFiles()
    ?.filter { it.name.startsWith("dynamicdimensions-neoforge-") && it.extension == "jar" }
    ?.filterNot { it.name.contains("-sources") || it.name.contains("-javadoc") }
    ?.maxByOrNull { it.lastModified() }
val localMachineLibVersion = localMachineLib?.name?.removePrefix("MachineLib-neoforge-")?.removeSuffix(".jar")
val localDynamicDimensionsVersion = localDynamicDimensions?.name?.removePrefix("dynamicdimensions-neoforge-")?.removeSuffix(".jar")
val neoReplacedFabricSources = setOf(
    "dev/galacticraft/impl/internal/fabric/GalacticraftAPI.java",
    "dev/galacticraft/api/gas/GasFluid.java",
    "dev/galacticraft/mod/client/model/BakedObjModel.java",
    "dev/galacticraft/mod/client/model/CannedFoodBakedModel.java",
    "dev/galacticraft/mod/client/model/GCModelLoader.java",
    "dev/galacticraft/mod/client/model/GCRenderTypes.java",
    "dev/galacticraft/mod/client/model/ParachestBakedModel.java",
    "dev/galacticraft/mod/client/model/PipeBakedModel.java",
    "dev/galacticraft/mod/client/model/VacuumGlassBakedModel.java",
    "dev/galacticraft/mod/client/model/WalkwayCenterModel.java",
    "dev/galacticraft/mod/client/model/types/UnbakedObjModel.java",
    "dev/galacticraft/mod/client/render/entity/model/GCEntityModelLayer.java",
    "dev/galacticraft/mod/client/render/FootprintRenderer.java",
    "dev/galacticraft/mod/client/GCKeyBinds.java",
    "dev/galacticraft/mod/api/wire/Wire.java",
    "dev/galacticraft/mod/api/wire/WireNetwork.java",
    "dev/galacticraft/mod/api/wire/impl/WireNetworkImpl.java",
    "dev/galacticraft/mod/api/block/WireBlock.java",
    "dev/galacticraft/mod/content/block/entity/networked/WireBlockEntity.java",
    "dev/galacticraft/mod/content/item/BatteryItem.java",
    "dev/galacticraft/mod/content/item/InfiniteBatteryItem.java",
    "dev/galacticraft/mod/attachments/GCAttachments.java",
    "dev/galacticraft/mod/attachments/GCServerPlayer.java",
    "dev/galacticraft/mod/attachments/GCClientPlayer.java",
    "dev/galacticraft/mod/particle/GCParticleTypes.java",
    "dev/galacticraft/api/entity/attribute/GcApiEntityAttributes.java",
    "dev/galacticraft/mod/content/GCRegistry.java",
    "dev/galacticraft/api/registry/BuiltInAddonRegistries.java",
    "dev/galacticraft/api/registry/BuiltInRocketRegistries.java",
    "dev/galacticraft/mod/content/GCFluids.java",
    "dev/galacticraft/mod/content/block/entity/decoration/CannedFoodBlockEntity.java",
    "dev/galacticraft/mod/content/block/entity/ParachestBlockEntity.java",
    "dev/galacticraft/mod/api/pipe/FluidPipe.java",
    "dev/galacticraft/mod/api/pipe/PipeNetwork.java",
    "dev/galacticraft/mod/api/pipe/impl/PipeNetworkImpl.java",
    "dev/galacticraft/mod/content/block/special/fluidpipe/PipeBlockEntity.java",
    "dev/galacticraft/mod/content/block/entity/networked/GlassFluidPipeBlockEntity.java",
    "dev/galacticraft/mod/util/FluidUtil.java",
    "dev/galacticraft/mod/content/item/OxygenTankItem.java",
    "dev/galacticraft/mod/content/item/InfiniteOxygenTankItem.java",
    "dev/galacticraft/mod/content/item/FluidCanisterItem.java"
).map { rootProject.file("fabric/src/main/java/$it").canonicalFile }.toSet()

base {
    archivesName.set("${rootProject.property("mod.name")}-${project.name}")
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

sourceSets {
    main {
        java {
            srcDir(rootProject.file("fabric/src/main/java"))
            exclude("dev/galacticraft/mod/compat/ModMenuApiImpl.java")
            exclude("dev/galacticraft/mod/compat/AppleSkinEventHandler.java")
            exclude("dev/galacticraft/mod/compat/waila/**")
            exclude("dev/galacticraft/fabric/**")
            exclude("dev/galacticraft/mod/lookup/FabricEnergyLookupProviders.java")
            exclude("dev/galacticraft/mod/lookup/FabricApiLookupProviders.java")
            // Fabric's client bootstrap and renderer implementation are replaced
            // by NeoForge client event registration.
            exclude("dev/galacticraft/mod/GalacticraftClient.java")
            exclude("dev/galacticraft/mod/events/ClientEventHandler.java")
            exclude("dev/galacticraft/impl/internal/client/fabric/GalacticraftAPIClient.java")
            exclude("dev/galacticraft/mod/client/ClientCapeLoginSync.java")
            exclude("dev/galacticraft/mod/client/event/RocketAtlasCallback.java")
            exclude("dev/galacticraft/mod/client/model/ParachestUnbakedModel.java")
            exclude("dev/galacticraft/mod/client/model/PipeUnbakedModel.java")
            exclude("dev/galacticraft/mod/client/model/VacuumGlassUnbakedModel.java")
            exclude("dev/galacticraft/mod/client/render/dimension/EmptyCloudRenderer.java")
            exclude("dev/galacticraft/mod/client/render/dimension/EmptyWeatherRenderer.java")
            exclude("dev/galacticraft/mod/client/render/dimension/GCDimensionEffects.java")
            exclude("dev/galacticraft/mod/client/render/GCBlockOutlineRenderer.java")
            exclude("dev/galacticraft/mod/client/resources/GCResourceReloadListener.java")
            exclude("dev/galacticraft/mod/client/resources/RocketTextureManager.java")
            exclude("dev/galacticraft/mod/compat/emi/**")
            exclude("dev/galacticraft/mod/storage/CanisterFluidStorage.java")
            exclude("dev/galacticraft/mod/storage/OxygenTankFluidStorage.java")
            exclude("dev/galacticraft/mod/storage/PlaceholderItemStorage.java")
            exclude("dev/galacticraft/mod/util/TransferStorageUtil.java")
            exclude("dev/galacticraft/mod/content/fluid/GCFluidAttribute.java")
            exclude("dev/galacticraft/mod/data/EmiDefaultRecipeProvider.java")
            exclude("dev/galacticraft/mod/api/data/**")
            exclude("dev/galacticraft/mod/client/accessor/BlockModelAccessor.java")
            exclude("dev/galacticraft/mod/mixin/client/BlockModelMixin.java")
            exclude("dev/galacticraft/mod/mixin/client/MinecraftMixin.java")
            exclude { it.file.canonicalFile in neoReplacedFabricSources }
            exclude("dev/galacticraft/mod/data/content/**")
            exclude("dev/galacticraft/mod/data/loot/**")
            exclude("dev/galacticraft/mod/data/model/**")
            exclude("dev/galacticraft/mod/data/recipes/**")
            exclude("dev/galacticraft/mod/data/tag/**")
            exclude("dev/galacticraft/mod/data/GCAdvancementProvider.java")
            exclude("dev/galacticraft/mod/data/GCBlockLootTableProvider.java")
            exclude("dev/galacticraft/mod/data/GCDataGenerator.java")
            exclude("dev/galacticraft/mod/data/GCEntityLoot.java")
            exclude("dev/galacticraft/mod/data/GCLootTableProvider.java")
            exclude("dev/galacticraft/mod/data/GCOxygenBlockProvider.java")
            exclude("dev/galacticraft/mod/data/GCTranslationProvider.java")
            exclude("dev/galacticraft/mod/data/OxygenBlockProvider.java")
            exclude("dev/galacticraft/api/data/TranslationProvider.java")
            exclude("dev/galacticraft/impl/data/**")
            exclude("dev/galacticraft/impl/internal/fabric/GalacticraftAPIData.java")
            exclude("dev/galacticraft/mod/mixin/ModelProviderMixin.java")
        }
        resources {
            srcDir(rootProject.file("fabric/src/main/resources"))
            srcDir(rootProject.file("fabric/src/main/generated"))
            exclude("galacticraft-test.mixins.json")
            exclude("galacticraft-api.mixins.json")
        }
    }
}

loom {
    silentMojangMappingsLicense()
    accessWidenerPath.set(rootProject.file("fabric/src/main/resources/galacticraft.accesswidener"))
    mixin.useLegacyMixinAp = true
    mixin.add(sourceSets.main.get(), "galacticraft.refmap.json")
    enableTransitiveAccessWideners.set(true)
    interfaceInjection {
        getIsEnabled().set(true)
        getEnableDependencyInterfaceInjection().set(true)
    }
    runs {
        named("client") {
            name("NeoForge Client")
        }
        named("server") {
            name("NeoForge Server")
        }
    }
}

architectury {
    platformSetupLoomIde()
    neoForge {}
}

val common: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating

configurations["compileClasspath"].extendsFrom(common)
configurations["runtimeClasspath"].extendsFrom(common)
configurations["developmentNeoForge"].extendsFrom(common)
configurations["testCompileClasspath"].extendsFrom(common)
configurations["testRuntimeClasspath"].extendsFrom(common)

repositories {
    mavenLocal()
    if (localMachineLib != null) {
        ivy {
            name = "localMachineLibNeoForge"
            url = uri(localMachineLib.parentFile)
            patternLayout { artifact("[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeModule("dev.galacticraft.local", "MachineLib-neoforge") }
        }
    }
    if (localDynamicDimensions != null) {
        ivy {
            name = "localDynamicDimensionsNeoForge"
            url = uri(localDynamicDimensions.parentFile)
            patternLayout { artifact("[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeModule("dev.galacticraft.local", "dynamicdimensions-neoforge") }
        }
    }
    maven("https://maven.neoforged.net/releases/")
    maven("https://repo.terradevelopment.net/repository/maven-releases/") {
        content { includeGroup("dev.galacticraft") }
    }
    maven("https://maven.shedaniel.me/") {
        content {
            includeGroup("me.shedaniel")
            includeGroup("me.shedaniel.cloth")
            includeGroup("dev.architectury")
        }
    }
    maven("https://maven.blamejared.com/") { content { includeGroup("mezz.jei") } }
    maven("https://maven.terraformersmc.com/releases/") { content { includeGroup("dev.emi") } }
    maven("https://maven.bai.lol/") {
        content {
            includeGroup("lol.bai")
            includeGroup("mcp.mobius.waila")
        }
    }
    maven("https://maven.terraformersmc.com/releases/") { content { includeGroup("com.terraformersmc") } }
    maven("https://maven.ryanliptak.com/") { content { includeGroup("squeek.appleskin") } }
    maven("https://mvn.devos.one/snapshots/") {
        content { includeGroup("io.github.fabricators_of_create.Porting-Lib") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft.version")}")
    mappings(loom.officialMojangMappings())
    "neoForge"("net.neoforged:neoforge:${rootProject.property("neoforge.version")}")
    // Shared API sources retain Fabric's compile-time-only side annotations.
    // This is an annotation artifact, not a runtime mod dependency.
    compileOnly("net.fabricmc:fabric-loader:${rootProject.property("loader.version")}")
    modImplementation("dev.architectury:architectury-neoforge:${rootProject.property("architectury.version")}")
    if (localMachineLib != null) {
        val machineLib = "dev.galacticraft.local:MachineLib-neoforge:$localMachineLibVersion"
        modImplementation(machineLib)
        include(machineLib)
    } else {
        val machineLib = "dev.galacticraft:MachineLib-neoforge:${rootProject.property("machinelib.version")}"
        modImplementation(machineLib)
        include(machineLib)
    }

    implementation("de.javagl:obj:${rootProject.property("obj.version")}")
    forgeRuntimeLibrary("de.javagl:obj:${rootProject.property("obj.version")}")
    include("de.javagl:obj:${rootProject.property("obj.version")}")

    if (localDynamicDimensions != null) {
        val dynamicDimensions = "dev.galacticraft.local:dynamicdimensions-neoforge:$localDynamicDimensionsVersion"
        modImplementation(dynamicDimensions)
        include(dynamicDimensions)
    } else {
        val dynamicDimensions = "dev.galacticraft:dynamicdimensions-neoforge:${rootProject.property("dynamicdimensions.version")}"
        modImplementation(dynamicDimensions)
        include(dynamicDimensions)
    }
    modImplementation("lol.bai:badpackets:neo-${rootProject.property("badpackets.version")}")

    modCompileOnly("me.shedaniel.cloth:cloth-config-neoforge:${rootProject.property("cloth.config.version")}")
    modCompileOnly("me.shedaniel:RoughlyEnoughItems-api-neoforge:${rootProject.property("rei.version")}")
    modCompileOnly("me.shedaniel:RoughlyEnoughItems-default-plugin-neoforge:${rootProject.property("rei.version")}")
    modCompileOnly("mezz.jei:jei-${rootProject.property("minecraft.version")}-neoforge-api:${rootProject.property("jei.version")}")
    modCompileOnly("dev.emi:emi-neoforge:${rootProject.property("emi.version")}:api")

    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionNeoForge")) { isTransitive = false }

    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.processResources {
    exclude("fabric.mod.json")
    val properties = mapOf(
        "version" to project.version,
        "minecraftVersion" to rootProject.property("minecraft.version"),
        "neoForgeVersion" to rootProject.property("neoforge.version"),
        "architecturyVersion" to rootProject.property("architectury.version"),
        "machineLibVersion" to rootProject.property("machinelib.version"),
        "dynamicDimensionsVersion" to (localDynamicDimensionsVersion ?: rootProject.property("dynamicdimensions.version")),
        "badPacketsVersion" to rootProject.property("badpackets.version")
    )
    inputs.properties(properties)
    filesMatching("META-INF/neoforge.mods.toml") { expand(properties) }
}

tasks.test { useJUnitPlatform() }

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    inputFile.set(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").flatMap { it.archiveFile })
    dependsOn("shadowJar")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    exclude("fabric.mod.json")
    exclude("architectury.common.json")
    configurations = listOf(shadowCommon)
    archiveClassifier.set("dev-shadow")
}

tasks.named<Jar>("sourcesJar") {
    from(project(":common").extensions.getByType<SourceSetContainer>()["main"].allSource)
}
