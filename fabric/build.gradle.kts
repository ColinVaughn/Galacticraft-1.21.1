plugins {
    java
    id("dev.architectury.loom")
    id("architectury-plugin")
    id("com.gradleup.shadow")
}

val runJei = project.findProperty("jei")?.toString()?.toBoolean() ?: false
val runEmi = project.findProperty("emi")?.toString()?.toBoolean() ?: false
val runRei = project.findProperty("rei")?.toString()?.toBoolean() ?: (!runJei && !runEmi)
val localMachineLib = rootProject.file("../MachineLib/fabric/build/libs").listFiles()
    ?.filter { it.name.startsWith("MachineLib-fabric-") && it.extension == "jar" }
    ?.filterNot { it.name.contains("-sources") || it.name.contains("-javadoc") || it.name.contains("-shadow") }
    ?.maxByOrNull { it.lastModified() }
val localDynamicDimensions = rootProject.file("build/dependency-sources/DynamicDimensions/fabric/build/libs").listFiles()
    ?.filter { it.name.startsWith("dynamicdimensions-fabric-") && it.extension == "jar" }
    ?.filterNot { it.name.contains("-sources") || it.name.contains("-javadoc") }
    ?.maxByOrNull { it.lastModified() }
val localMachineLibVersion = localMachineLib?.name?.removePrefix("MachineLib-fabric-")?.removeSuffix(".jar")
val localDynamicDimensionsVersion = localDynamicDimensions?.name?.removePrefix("dynamicdimensions-fabric-")?.removeSuffix(".jar")

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
        resources {
            srcDir("src/main/generated")
            exclude(".cache/")
        }
    }
}

loom {
    silentMojangMappingsLicense()
    accessWidenerPath.set(file("src/main/resources/galacticraft.accesswidener"))
    mixin.add(sourceSets.main.get(), "galacticraft.refmap.json")
    mixin.add(sourceSets.test.get(), "galacticraft-test.refmap.json")

    runs {
        named("client") {
            name("Fabric Client")
            source(sourceSets.test.get())
            property("fabric-tag-conventions-v2.missingTagTranslationWarning", "VERBOSE")
        }
        named("server") {
            name("Fabric Server")
            source(sourceSets.test.get())
        }
        register("datagen") {
            client()
            name("Fabric Data Generation")
            source(sourceSets.main.get())
            runDir("build/datagen")
            property("fabric-api.datagen")
            property("fabric-api.datagen.modid", rootProject.property("mod.id").toString())
            property("fabric-api.datagen.output-dir", file("src/main/generated").toString())
            property("fabric-api.datagen.strict-validation", "false")
        }
        register("gametest") {
            server()
            name("Fabric GameTest Server")
            source(sourceSets.test.get())
            property("fabric-api.gametest")
            property("fabric-api.gametest.report-file", layout.buildDirectory.file("junit.xml").get().asFile.toString())
        }
    }
}

architectury {
    platformSetupLoomIde()
    fabric()
}

val common: Configuration by configurations.creating
val shadowCommon: Configuration by configurations.creating

configurations["compileClasspath"].extendsFrom(common)
configurations["runtimeClasspath"].extendsFrom(common)
configurations["developmentFabric"].extendsFrom(common)
configurations["testCompileClasspath"].extendsFrom(common)
configurations["testRuntimeClasspath"].extendsFrom(common)
configurations["testRuntimeClasspath"].extendsFrom(configurations["architecturyTransformerRuntimeClasspath"])

repositories {
    mavenLocal()
    if (localMachineLib != null) {
        ivy {
            name = "localMachineLibFabric"
            url = uri(localMachineLib.parentFile)
            patternLayout { artifact("[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeModule("dev.galacticraft.local", "MachineLib-fabric") }
        }
    }
    if (localDynamicDimensions != null) {
        ivy {
            name = "localDynamicDimensionsFabric"
            url = uri(localDynamicDimensions.parentFile)
            patternLayout { artifact("[artifact]-[revision].[ext]") }
            metadataSources { artifact() }
            content { includeModule("dev.galacticraft.local", "dynamicdimensions-fabric") }
        }
    }
    maven("https://repo.terradevelopment.net/repository/maven-releases/") {
        content { includeGroup("dev.galacticraft") }
    }
    maven("https://mvn.devos.one/snapshots/") {
        content { includeGroup("io.github.fabricators_of_create.Porting-Lib") }
    }
    maven("https://maven.shedaniel.me/") {
        content {
            includeGroup("me.shedaniel")
            includeGroup("me.shedaniel.cloth")
            includeGroup("dev.architectury")
        }
    }
    maven("https://maven.modmuss50.me/") { content { includeGroup("teamreborn") } }
    maven("https://maven.terraformersmc.com/releases/") {
        content {
            includeGroup("com.terraformersmc")
            includeGroup("dev.emi")
        }
    }
    maven("https://maven.bai.lol/") {
        content {
            includeGroup("mcp.mobius.waila")
            includeGroup("lol.bai")
        }
    }
    maven("https://maven.blamejared.com/") { content { includeGroup("mezz.jei") } }
    maven("https://maven.ryanliptak.com/") { content { includeGroup("squeek.appleskin") } }
}

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft.version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("loader.version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric.version")}")
    modImplementation("dev.architectury:architectury-fabric:${rootProject.property("architectury.version")}")
    val energyApi = "teamreborn:energy:${rootProject.property("energy.version")}"
    modImplementation(energyApi)
    include(energyApi)

    implementation("de.javagl:obj:${rootProject.property("obj.version")}")
    include("de.javagl:obj:${rootProject.property("obj.version")}")

    if (localDynamicDimensions != null) {
        val dynamicDimensions = "dev.galacticraft.local:dynamicdimensions-fabric:$localDynamicDimensionsVersion"
        modImplementation(dynamicDimensions)
        include(dynamicDimensions)
    } else {
        val dynamicDimensions = "dev.galacticraft:dynamicdimensions-fabric:${rootProject.property("dynamicdimensions.version")}"
        modImplementation(dynamicDimensions)
        include(dynamicDimensions)
    }
    if (localMachineLib != null) {
        val machineLib = "dev.galacticraft.local:MachineLib-fabric:$localMachineLibVersion"
        modImplementation(machineLib)
        include(machineLib)
    } else {
        val machineLib = "dev.galacticraft:MachineLib-fabric:${rootProject.property("machinelib.version")}"
        modImplementation(machineLib)
        include(machineLib)
    }
    modImplementation("lol.bai:badpackets:fabric-${rootProject.property("badpackets.version")}")

    modCompileOnly("com.terraformersmc:modmenu:${rootProject.property("modmenu.version")}")
    modCompileOnly("me.shedaniel.cloth:cloth-config-fabric:${rootProject.property("cloth.config.version")}")
    modCompileOnly("mcp.mobius.waila:wthit:fabric-${rootProject.property("wthit.version")}")
    modCompileOnly("squeek.appleskin:appleskin-fabric:${rootProject.property("appleskin.version")}")

    modCompileOnly("me.shedaniel:RoughlyEnoughItems-api-fabric:${rootProject.property("rei.version")}")
    modCompileOnly("me.shedaniel:RoughlyEnoughItems-default-plugin-fabric:${rootProject.property("rei.version")}")
    if (runRei) modLocalRuntime("me.shedaniel:RoughlyEnoughItems-fabric:${rootProject.property("rei.version")}")

    modCompileOnly("mezz.jei:jei-${rootProject.property("minecraft.version")}-fabric-api:${rootProject.property("jei.version")}")
    if (runJei) modLocalRuntime("mezz.jei:jei-${rootProject.property("minecraft.version")}-fabric:${rootProject.property("jei.version")}")

    modCompileOnly("dev.emi:emi-fabric:${rootProject.property("emi.version")}:api")
    if (runEmi) modLocalRuntime("dev.emi:emi-fabric:${rootProject.property("emi.version")}")

    testImplementation("net.fabricmc:fabric-loader-junit:${rootProject.property("loader.version")}")

    common(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    shadowCommon(project(path = ":common", configuration = "transformProductionFabric")) { isTransitive = false }
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version,
        "fabricVersion" to rootProject.property("fabric.version"),
        "minecraftVersion" to rootProject.property("minecraft.version"),
        "architecturyVersion" to rootProject.property("architectury.version"),
        "machineLibVersion" to rootProject.property("machinelib.version"),
        "energyVersion" to rootProject.property("energy.version")
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") { expand(properties) }
}

tasks.test {
    useJUnitPlatform()
    workingDir = rootProject.file("run")
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    inputFile.set(tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").flatMap { it.archiveFile })
    dependsOn("shadowJar")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    exclude("architectury.common.json")
    configurations = listOf(shadowCommon)
    archiveClassifier.set("dev-shadow")
}

tasks.named<Jar>("sourcesJar") {
    from(project(":common").extensions.getByType<SourceSetContainer>()["main"].allSource)
}
