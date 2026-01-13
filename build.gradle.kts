import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

plugins {
    id("com.gradleup.shadow") version "9.3.1"
    id("java")
}

group = "org.winlogon.fastrtp"

fun getTime(): String {
    val sdf = SimpleDateFormat("yyMMdd-HHmm")
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

// Set version to version property if supplied
val shortVersion: String? = if (project.hasProperty("ver")) {
    val ver = project.property("ver") as String
    if (ver.startsWith("v")) {
        ver.substring(1).uppercase()
    } else {
        ver.uppercase()
    }
} else null

// If the tag includes "-RC-" or no tag is supplied, append "-SNAPSHOT"
val version = when {
    shortVersion.isNullOrEmpty() -> "${getTime()}-SNAPSHOT"
    shortVersion.contains("-RC-") -> "${shortVersion.substringBefore("-RC-")}-SNAPSHOT"
    else -> shortVersion
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
        content {
            includeModule("io.papermc.paper", "paper-api")
            includeModule("net.md-5", "bungeecord-chat")
        }
    }

    maven {
        name = "minecraft"
        url = uri("https://libraries.minecraft.net")
        content {
            includeModule("com.mojang", "brigadier")
        }
    }

    maven {
        name = "winlogon"
        url = uri("https://maven.winlogon.org/releases")
    }

    maven {
        name = "jitpack"
        url = uri("https://jitpack.io")
        content {
            includeModule("com.github.walker84837", "JResult")
        }
    }

    maven(url = "https://repo.codemc.org/repository/maven-public/")

    mavenCentral()
}

object Version {
    const val Minecraft = "1.21.11"
    const val CommandAPI = "11.1.0"
    const val JUnit = "6.0.1"
}

dependencies {
    annotationProcessor("dev.jorel:commandapi-paper-annotations:${Version.CommandAPI}")

    compileOnly("com.github.walker84837:JResult:1.4.0")
    compileOnly("dev.jorel:commandapi-paper-annotations:${Version.CommandAPI}")
    implementation("dev.jorel:commandapi-paper-shade:${Version.CommandAPI}")
    compileOnly("io.papermc.paper:paper-api:${Version.Minecraft}-R0.1-SNAPSHOT")
    compileOnly("org.winlogon:asynccraftr:0.1.0")

    testImplementation("io.papermc.paper:paper-api:${Version.Minecraft}-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:${Version.JUnit}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:${Version.JUnit}")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("**/paper-plugin.yml") {
        expand(
            "NAME" to rootProject.name,
            "VERSION" to version,
            "PACKAGE" to project.group.toString()
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    minimize {
        // Keep this hook in case you need exclusions later
        // exclude(dependency("com.github.walker84837:JResult:.*"))
    }
    relocate("dev.jorel.commandapi", "org.winlogon.homemanager.commandapi")
}

// Disable jar and replace with shadowJar
tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.register("printProjectName") {
    doLast {
        println(rootProject.name)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.register("release") {
    dependsOn(tasks.build)

    doLast {
        if (!version.endsWith("-SNAPSHOT")) {
            val shadowJarFile = tasks.shadowJar.get().archiveFile.get().asFile
            val targetFile = File("${layout.buildDirectory.get()}/libs/${rootProject.name}.jar")
            shadowJarFile.renameTo(targetFile)
        }
    }
}
