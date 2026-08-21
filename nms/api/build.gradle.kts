// Surumden bagimsiz NMS sozlesmesi. Burada HICBIR net.minecraft importu bulunmaz;
// cekirdek yalnizca bu modulu gorur, remap edilmis implementasyonlari asla gormez.
plugins { java }

val paperApiVersion: String by project

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
}
