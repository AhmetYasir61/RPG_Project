// 1.21.11 icin VersionAdapter implementasyonu. paperweight-userdev, Mojang-mapped
// kaynak yazip reobf/mojang-mapped jar uretmemizi saglar; refleksiyon kullanilmaz.
plugins {
    java
    id("io.papermc.paperweight.userdev")
}

val minecraftVersion: String by project

dependencies {
    paperweight.paperDevBundle("$minecraftVersion-R0.1-SNAPSHOT")
    compileOnly(project(":nms:api"))
}

// Paper 1.20.5+ sunucusu Mojang-mapped calisir; ayrica reobf adimina gerek yoktur.
// paperweight'in urettigi standart jar dogrudan shade edilir.
