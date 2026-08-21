# Sunucuda Karsilasilan Hatalar ve Cozumleri

Bu dosya gercek bir Leaf 1.21.11 sunucusunda alinan hatalarin kok sebeplerini
ve uygulanan cozumleri kaydeder.

## 1. "Bu Minecraft surumu icin NMS adapteri yok"

**Belirti:** `waypoint` ve `hologram` modulleri onLoad'da patliyor, adapter bulunamiyor.

**Kok sebep:** Shadow'un `minimize()` islemi yalnizca **statik** referanslari gorur.
`v1_21_11.Adapter` sinifi yalnizca `Class.forName` ile yuklendigi icin minimize onu
"kullanilmiyor" sayip jar'dan **silmisti**. Jar icerigi dogrulandiginda
`net/aethel/core/nms/` altinda sadece arayuz vardi, implementasyon yoktu.

**Cozum:** `build.gradle.kts` icinde minimize disi birakilanlara NMS alt projeleri
ve PacketEvents eklendi:
```kotlin
minimize {
    exclude(project(":nms:api"))
    exclude(project(":nms:v1_21_11"))
    exclude(dependency("com.github.retrooper:.*:.*"))
    ...
}
```
**Dogrulama:** `unzip -l build/libs/AethelCore.jar | grep v1_21_11` -> `Adapter.class` gorunmeli.

## 2. Sunucunun jar'i yanlis mapping ile remap etmesi

**Belirti:** `[PluginRemapper] Remapping plugin 'AethelCore.jar'` — sunucu jar'i
Spigot mapping sanip cevirmeye calisiyor.

**Kok sebep:** paperweight ile Mojang-mapped derliyoruz ama `plugin.yml` bunu soylemiyordu.

**Cozum:** `plugin.yml` icine eklendi:
```yaml
paperweight-mappings-namespace: mojang
```

## 3. PacketEvents 1.21.11'i desteklemiyor -> tum plugin cokuyordu

**Belirti:**
```
Your build of PacketEvents does not support the Minecraft version 1.21.11!
...
Disabling AethelCore
IllegalStateException: zip file closed
```

**Kok sebep zinciri:** PacketEvents `init()` patladi -> Paper plugini devre disi birakti ->
plugin classloader kapandi -> sonraki her sinif yuklemesi `zip file closed` verdi.
Loglardaki `profile`, `menu`, `content`, `placeholder` hatalari **belirti**ydi, sebep degil.

**Cozum (iki parcali):**
1. `packetevents-spigot` 2.13.1-SNAPSHOT'a cekildi (1.21.11 destegi gelistirme yapisinda).
2. `PacketBridge` **fail-soft** yapildi: kutuphane patlarsa `available=false` olur,
   cekirdek calismaya devam eder ve yalnizca paket tabanli moduller (hologram, npc,
   waypoint) atlanir. Bir kutuphanenin yeni bir surumde kirilmasi, tum sunucunun
   acilmamasi icin yeterli bir sebep degildir.

**Not:** Sunucuda ayrica bagimsiz bir `packetevents` plugini kurulu. Bizimki
`net.aethel.core.libs.packetevents` altina relocate edildigi icin cakismaz;
istersen o plugini kaldirabilirsin, AethelCore ona ihtiyac duymaz.

## 4. Modul "hatasi" ile modul "kosulu" ayrimi

Onceden bir modul calisamadiginda tam stack trace basiliyordu ve bu, gercek hatalari
gizliyordu. Artik `ModuleUnavailableException` var:

- **Kosul** (eksik NMS adapteri, kapali paket katmani) -> tek satir uyari, durum `SKIPPED`.
- **Gercek hata** -> tam stack trace, durum `FAILED`.

`/core modules` ciktisinda ikisi ayri gorunur.

## Beklenen acilis ciktisi (duzeltmelerden sonra)

```
[AethelCore] Paket katmani yuklendi.
[AethelCore] Yuklenen diller: [en, tr]
[AethelCore] Veritabani hazir: SQLITE
[AethelCore] Modul yukleme sirasi: [profile, auth, permissions, economy, ...]
[AethelCore] Kayitli kok komut: N
[AethelCore] Aktif modul: 21/21
[AethelCore] Cekirdek hazir.
```

Paket katmani yine desteklemezse beklenen cikti:
```
[AethelCore] Paket katmani yuklenemedi; hologram, NPC ve waypoint modulleri devre disi kalacak
[AethelCore] Modul atlandi (hologram): paket katmani kullanilamiyor
[AethelCore] Aktif modul: 18/21
```
Sunucu bu durumda da normal calisir.
