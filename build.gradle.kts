// dkFly - timed flight for the dk suite. Author: direk james
//
//   ./gradlew build   -> build/libs/dkFly-<version>.jar (put on the server next to dkCore)

plugins {
    java
}

group = "com.direk"
version = "1.0.0"

repositories {
    // dkCore (publishToMavenLocal). Only our own group is looked up here.
    mavenLocal {
        content { includeGroup("com.direk") }
    }
    mavenCentral()
    // Purpur API. The Purpur repo rejects lookups for other projects (HTTP 406),
    // so it's only asked for Purpur's own group.
    maven("https://repo.purpurmc.org/snapshots") {
        content { includeGroup("org.purpurmc.purpur") }
    }
    maven("https://repo.papermc.io/repository/maven-public/") // Paper / Adventure
    maven("https://maven.enginehub.org/repo/")                // WorldGuard / WorldEdit
    maven("https://repo.extendedclip.com/releases/")          // PlaceholderAPI
    maven("https://repo.codemc.org/repository/maven-public/") // BentoBox
}

dependencies {
    compileOnly("org.purpurmc.purpur:purpur-api:${property("purpurVersion")}")
    compileOnly("com.direk:dkcore:${property("dkCoreVersion")}")

    compileOnly("me.clip:placeholderapi:${property("placeholderApiVersion")}")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:${property("worldGuardVersion")}") {
        exclude(group = "org.bukkit")
        exclude(group = "org.spigotmc")
        exclude(group = "io.papermc.paper")
    }
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:${property("worldEditVersion")}") {
        exclude(group = "org.bukkit")
        exclude(group = "org.spigotmc")
        exclude(group = "io.papermc.paper")
    }
    compileOnly("world.bentobox:bentobox:${property("bentoBoxVersion")}") {
        isTransitive = false
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
}

tasks.jar {
    archiveFileName.set("dkFly-${project.version}.jar")
}
