pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

// Proje adi gradle.properties -> serverName degerinden turetilir (AethelCore).
rootProject.name = providers.gradleProperty("serverName").get() + "Core"

include("nms:api")
include("nms:v1_21_11")
