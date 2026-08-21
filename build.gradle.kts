import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.22" apply false
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

val serverName: String by project
val corePackage: String by project
val paperApiVersion: String by project
val javaVersion: String by project

allprojects {
    group = corePackage
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/")   // PacketEvents
        // 1.21.11 destegi henuz yalnizca gelistirme yapisinda; surum ciktiginda
        // asagidaki bagimlilik sabit surume cekilmeli.
        maven("https://repo.codemc.io/repository/maven-snapshots/")
        maven("https://repo.extendedclip.com/releases/")             // PlaceholderAPI
        maven("https://jitpack.io")                                  // VaultAPI
        maven("https://oss.sonatype.org/content/groups/public/")
    }
}

subprojects {
    apply(plugin = "java")
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion.toInt()))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    // NMS soyutlama: api derleme zamani, implementasyonlar runtime icin shade edilir.
    implementation(project(":nms:api"))
    implementation(project(":nms:v1_21_11"))

    // Shade edilen kutuphaneler (hepsi relocate ediliyor, bkz. shadowJar).
    implementation("com.github.retrooper:packetevents-spigot:2.13.1-SNAPSHOT")
    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.xerial:sqlite-jdbc:3.49.1.0")
    implementation("com.mysql:mysql-connector-j:9.2.0")
    implementation("io.javalin:javalin:6.4.0")                       // resource pack HTTP servisi
    implementation("com.google.code.gson:gson:2.11.0")

    compileOnly("me.clip:placeholderapi:2.11.6")                     // opsiyonel kopru
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")                // opsiyonel kopru

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(javaVersion.toInt())
        options.compilerArgs.add("-parameters")   // command framework parametre isimleri icin sart
    }

    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf(
            "version" to project.version,
            "serverName" to serverName,
            "corePackage" to corePackage,
            "apiVersion" to "1.21"
        )
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }

    // Duz jar ile shaded jar ayni dosya adini paylasamaz; duz ciktiyi isaretliyoruz.
    jar { archiveClassifier.set("dev") }

    named<ShadowJar>("shadowJar") {
        archiveFileName.set("${serverName}Core.jar")
        archiveClassifier.set("")

        val libs = "$corePackage.libs"
        relocate("com.github.retrooper", "$libs.packetevents")
        relocate("io.github.retrooper", "$libs.packetevents.impl")
        relocate("com.zaxxer.hikari", "$libs.hikari")
        relocate("io.javalin", "$libs.javalin")
        relocate("com.google.gson", "$libs.gson")

        mergeServiceFiles()
        minimize {
            // Refleksiyonla yuklenen her sey minimize disinda kalmali; minimize yalnizca
            // STATIK referanslari gorur ve gormedigini siler.
            // NMS adapteri Class.forName ile yuklenir: minimize onu "kullanilmiyor" sanip
            // siler ve sunucuda "surum adapteri yok" hatasi verir.
            exclude(project(":nms:api"))
            exclude(project(":nms:v1_21_11"))
            exclude(dependency("org.xerial:sqlite-jdbc:.*"))
            exclude(dependency("com.mysql:mysql-connector-j:.*"))
            exclude(dependency("io.javalin:.*:.*"))
            exclude(dependency("com.github.retrooper:.*:.*"))
            exclude(dependency("io.github.retrooper:.*:.*"))
        }
    }

    build { dependsOn(shadowJar) }

    test { useJUnitPlatform() }

    runServer {
        minecraftVersion(providers.gradleProperty("minecraftVersion").get())
        jvmArgs("-Xmx4G", "-XX:+UseG1GC", "-Dcom.mojang.eula.agree=true")
    }
}
