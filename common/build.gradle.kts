plugins {
    java
    id("dev.architectury.loom")
    id("architectury-plugin")
}

base {
    archivesName.set("${rootProject.property("mod.name")}-${project.name}")
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

loom {
    silentMojangMappingsLicense()
}

architectury {
    common(rootProject.property("enabled_platforms").toString().split(','))
}

repositories {
    mavenLocal()
    maven("https://repo.terradevelopment.net/repository/maven-releases/") {
        content { includeGroup("dev.galacticraft") }
    }
}

val localMachineLib = listOf("libs", "devlibs")
    .flatMap { rootProject.file("../MachineLib/common/build/$it").listFiles()?.asList().orEmpty() }
    .filter { it.name.startsWith("MachineLib-common-") && it.extension == "jar" }
    .filterNot { it.name.contains("-sources") || it.name.contains("-javadoc") || it.name.contains("-transformProduction") }
    .maxByOrNull { it.lastModified() }

dependencies {
    minecraft("com.mojang:minecraft:${rootProject.property("minecraft.version")}")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${rootProject.property("loader.version")}")
    modApi("dev.architectury:architectury:${rootProject.property("architectury.version")}")
    if (localMachineLib != null) {
        modApi(files(localMachineLib))
    } else {
        modApi("dev.galacticraft:MachineLib-common:${rootProject.property("machinelib.version")}")
    }
}
