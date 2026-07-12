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

allprojects {
    group = rootProject.property("mod.group").toString()
    version = rootProject.version
}

subprojects {
    apply(plugin = "maven-publish")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        lineEndings = com.diffplug.spotless.LineEnding.UNIX
        java {
            target(project.fileTree("src") { include("**/*.java") })
            licenseHeader(processLicenseHeader(rootProject.file("LICENSE")))
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

fun processLicenseHeader(license: File): String {
    val text = license.readText()
    return "/*\n * " + text.substring(text.indexOf("Copyright"))
        .replace("\n", "\n * ")
        .replace("* \n", "*\n")
        .trim() + "/\n\n"
}
