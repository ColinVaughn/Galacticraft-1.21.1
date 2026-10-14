/*
 * Copyright (c) 2019-2026 Team Galacticraft
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("architectury-plugin") version "3.4.164"
    id("dev.architectury.loom") version "1.7.416" apply false
    id("com.gradleup.shadow") version "8.3.6" apply false
    id("com.diffplug.spotless") version "7.0.4" apply false
    id("org.ajoberstar.grgit") version "5.3.2"
}

architectury {
    minecraft = property("minecraft.version").toString()
}

val baseVersion = property("mod.version").toString()
version = buildString {
    append(baseVersion)
    if (System.getenv("PRE_RELEASE") == "true") append("-pre")
    append('+')
    val runNumber = System.getenv("GITHUB_RUN_NUMBER")
    if (runNumber != null) {
        append(runNumber)
    } else {
        val repository = extensions.findByType<org.ajoberstar.grgit.Grgit>()
        val head = repository?.head()
        if (head != null) {
            append(head.id.substring(0, 8))
            if (!repository.status().isClean) append("-dirty")
        } else {
            append("unknown")
        }
    }
}


// A running dev client must not load classes from directories a later build can replace.
allprojects {
    tasks.withType<JavaExec>().configureEach {
        doFirst {
            val buildRoots = rootProject.allprojects.map { it.layout.buildDirectory.get().asFile.toPath().toAbsolutePath().normalize() }
            val snapshot = rootProject.file(".gradle/run-classpaths/${project.name}/$name/${java.util.UUID.randomUUID()}")
            val replacements = linkedMapOf<String, String>()
            fun mutable(file: File) = buildRoots.any { file.toPath().toAbsolutePath().normalize().startsWith(it) }
            fun freeze(file: File): File {
                replacements[file.absolutePath]?.let { return File(it) }
                val target = snapshot.resolve("${replacements.size}/${file.name}")
                if (file.isDirectory) project.copy { from(file); into(target) }
                else { target.parentFile.mkdirs(); file.copyTo(target) }
                replacements[file.absolutePath] = target.absolutePath
                return target
            }
            val runtime = classpath.files.map { if (it.exists() && mutable(it)) freeze(it) else it }
            val metadata = rootProject.allprojects.flatMap { module ->
                val loom = module.file(".gradle/loom-cache").listFiles().orEmpty().filter {
                    it.isFile && it.extension in listOf("cfg", "txt", "xml")
                }
                val architectury = module.file(".gradle/architectury").listFiles().orEmpty().filter {
                    it.isFile && it.name in listOf(".main_class", ".transforms", ".properties")
                }
                loom + architectury
            }
            val properties = metadata.filter { it.name == ".properties" }.associateWith { file ->
                java.util.Properties().apply { file.inputStream().use { load(it) } }
            }
            properties.values.forEach { values ->
                values.values.forEach { value ->
                    val file = File(value.toString())
                    if (file.isFile && mutable(file)) freeze(file)
                }
            }
            metadata.forEach { freeze(it) }
            fun rewrite(value: String) = replacements.entries.sortedByDescending { it.key.length }.fold(value) { result, (from, to) ->
                result.replace(from, to).replace(from.replace('\\', '/'), to.replace('\\', '/'))
            }
            metadata.forEach { file ->
                val target = File(replacements.getValue(file.absolutePath))
                val values = properties[file]
                if (values == null) target.writeText(rewrite(file.readText()))
                else {
                    values.replaceAll { _, value -> rewrite(value.toString()) }
                    target.outputStream().use { values.store(it, "Isolated dev runtime") }
                }
            }
            setClasspath(project.files(runtime))
            // Loom constructs its classpath argument file lazily from the classpath above.
            jvmArguments.set(jvmArguments.getOrElse(emptyList()).map { rewrite(it) })
            setJvmArgs(jvmArgs.orEmpty().map { rewrite(it.toString()) })
            setSystemProperties(systemProperties.mapValues { rewrite(it.value.toString()) })
            val argumentProviders = jvmArgumentProviders.toList()
            jvmArgumentProviders.clear()
            argumentProviders.forEach { provider ->
                jvmArgumentProviders.add(org.gradle.process.CommandLineArgumentProvider { provider.asArguments().map { rewrite(it) } })
            }
            logger.lifecycle("$path: isolated dev runtime in $snapshot")
        }
    }
}

val runtimeIsolationClasses = layout.buildDirectory.dir("runtime-isolation-probe")
val compileRuntimeIsolationProbe = tasks.register<JavaCompile>("compileRuntimeIsolationProbe") {
    source("tools/RuntimeClasspathIsolationProbe.java")
    sourceCompatibility = "21"
    targetCompatibility = "21"
    classpath = files()
    destinationDirectory.set(runtimeIsolationClasses)
    options.release.set(21)
}
tasks.register<JavaExec>("verifyRuntimeClasspathIsolation") {
    group = "verification"
    dependsOn(compileRuntimeIsolationProbe)
    classpath = files(runtimeIsolationClasses)
    mainClass.set("RuntimeClasspathIsolationProbe")
    args(runtimeIsolationClasses.get().asFile.absolutePath)
}

allprojects {
    group = rootProject.property("mod.group").toString()
    version = rootProject.version
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "pmd")

    extensions.configure<org.gradle.api.plugins.quality.PmdExtension> {
        toolVersion = "7.26.0"
        ruleSets = emptyList()
        ruleSetFiles = rootProject.files("config/pmd/complexity.xml")
        isConsoleOutput = true
    }

    tasks.withType<org.gradle.api.plugins.quality.Pmd>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    afterEvaluate {
        tasks.named<org.gradle.api.plugins.quality.Pmd>("pmdMain") {
            setSource(project.fileTree("src/main/java"))
        }
        tasks.named<org.gradle.api.plugins.quality.Pmd>("pmdTest") {
            enabled = false
        }
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        lineEndings = com.diffplug.spotless.LineEnding.UNIX
        java {
            target(project.fileTree("src") { include("**/*.java") })
            // No licenseHeader step. Fork-authored files carry a second copyright line that a
            // single enforced header cannot express; LICENSE remains the record of both holders.
            leadingTabsToSpaces()
            removeUnusedImports()
            trimTrailingWhitespace()
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        // Mixins, accessors and registry plumbing make up most of the public surface here, and a doc
        // comment on every shadowed field would say nothing. Drop doclint's "missing" group so the
        // groups that catch real errors (bad @link targets, malformed HTML) stay visible.
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:all,-missing", true)
    }

    tasks.withType<Jar>().configureEach {
        from(rootProject.file("LICENSE")) {
            rename { "${it}_${rootProject.property("mod.name")}" }
        }
        manifest {
            attributes(
                "Specification-Title" to rootProject.property("mod.name"),
                "Specification-Vendor" to "Team Galacticraft",
                "Specification-Version" to baseVersion,
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Team Galacticraft",
                "Implementation-Timestamp" to LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME),
                "Maven-Artifact" to "${project.group}:${rootProject.property("mod.name")}-${project.name}:${project.version}"
            )
        }
    }
}
