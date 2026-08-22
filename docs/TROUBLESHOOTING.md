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

## 5. "Modul bagimlilik cevrimi: dialog -> npc -> dialog"

**Belirti:** Boot durdu, `Aktif modul: 0/25`, ardindan "Cekirdek hazir." yazdi ve
sunucu hicbir sey yapmadan calismaya devam etti. `/adminmenu` cevapsiz kaldi.

**Kok sebep:** `DependencyGraph` yumusak bagimliligi (`softDepends`) zorunlu gibi
ele aliyordu. `dialog` NPC'yi, `npc` diyalogu yumusak bagimlilik olarak listeler —
bu tamamen gecerlidir: ikisi de digeri olmadan calisir, yalnizca varsa once
yuklenmesini tercih eder. Boyle bir cevrimde sirayi bir yerden kesmek yeterlidir.

**Cozum:** `visit()` artik kenarin turunu tasiyor:
- **Zorunlu** bagimlilikta cevrim -> hata (gercek tasarim hatasi; sessizce gecilirse
  modul bagimliligi hazir olmadan acilir)
- **Yumusak** bagimlilikta cevrim -> sira burada kesilir, boot devam eder

Regresyon testi eklendi: `DependencyGraphTest` (4 test) — yumusak cevrim kabul
edilir, zorunlu cevrim hata verir, siralama dogru, bilinmeyen bagimlilik atlanir.

## 6. Sessiz basarisizlik: "0 aktif modul" ama sunucu calisiyor

Yukaridaki hatanin en kotu yani, hatanin kendisi degil **nasil bittigiydi**:
`register()` patlayinca `loadAll()` hic calismadi, `enableAll()` bos liste buldu ve
cekirdek "hazir" dedi. Sunucu acik, plugin yuklu, hicbir sey calismiyor.

**Cozum:**
- `register()` hatasi yakalanir, `bootFailed` isaretlenir ve `onEnable` net bir
  mesajla plugini devre disi birakir — yanilticiyi "hazir" mesaji artik yok.
- `enableAll()` sonunda **sifir aktif modul** SEVERE olarak loglanir.
- Basarisiz (`FAILED`) ve atlanan (`SKIPPED`) moduller ayri satirlarda ozetlenir.

## 7. Web paneli: "Static resource directory 'META-INF/resources/webjars' does not exist"

**Belirti:** `web` modulu onEnable'da patliyor, Javalin baslamis ama modul FAILED.

**Kok sebep:** `config.staticFiles.enableWebjars()` cagriliyordu ama jar icinde
`META-INF/resources/webjars` klasoru yok — Javalin bunu baslangicta dogruluyor ve
bulamayinca hata firlatiyor.

**Cozum:** Cagri kaldirildi. Panelin CSS'i `WebPages` icinde gomulu; harici bir
varlik kutuphanesine zaten ihtiyac yok.

## 8. Paket adresi olarak 0.0.0.0 gonderilmesi

**Belirti:** `Kaynak paketi sunuluyor: http://0.0.0.0:8085/generated.zip`

**Kok sebep (iki ayri sey karisiyordu):**
- **bind** adresi `0.0.0.0` = "tum arayuzlerde dinle" — sunucunun kendi ayari.
- **public-host** = oyuncunun baglanacagi adres.

Log satiri bind adresini "sunuluyor" diye yaziyordu; ayrica `public-host` bos
birakildiginda gercekten de 0.0.0.0 gonderilebiliyordu. 0.0.0.0'a istemci
baglanamaz; **zorunlu pack** acikken bu, oyuncunun sunucuya hic girememesi demektir.

**Cozum:**
- Dinlenen adres ve gonderilen adres ayri ayri loglanir.
- `public-host` bos ya da `0.0.0.0` ise 127.0.0.1'e dusulur ve **uyari** verilir:
  bu adres yalnizca ayni makinedeki oyuncular icin calisir.
- Uzaktan baglanti icin `resource-pack.public-host` degerine sunucunun genel IP'si
  ya da alan adi yazilmalidir.

## 9. Ornek icerik dosyalarinin diske hic yazilmamasi

**Belirti:** `Diyalog dugumu: 0` ve `Font parcasi: 5` (dialog.yml'deki 8 parca yok).
Dosyalar jar'in icinde vardi ama `plugins/AethelCore/` altina cikmamisti.

**Kok sebep:** `saveDefaultResources()` dosyalari **elle listeliyordu**. Yeni bir
ornek dosya eklenip listeye yazilmasi unutuldugunda dosya diske hic yazilmiyor,
ilgili modul de "0 tanim yuklendi" deyip sessizce geciyordu.

**Cozum:** Liste kaldirildi. Jar taraniyor ve `lang/` ile `contents/` altindaki tum
`.yml`/`.json` dosyalari cikariliyor. Mevcut dosyalar **asla ezilmiyor**.
Cikarilan dosya sayisi loglaniyor.

## 10. Teshis: komut sayisi yerine komut isimleri

`Kayitli kok komut: 8` satiri, hangi komutun eksik oldugunu soylemiyordu.
Artik isimler de yaziliyor:
`Kayitli kok komut (9): [core, profil, adminmenu, parti, waypoint, rpg, meslek, gorev, dungeon]`

## 11. "Illegal character in authority at index 8: https://<url>" — paket kodlama hatasi

**Belirti:** Oyuncu girer girmez devasa bir `EncoderException` yiginı; mesaj oyuncuya
**hic ulasmaz**.

**Kok sebep:** Dil dosyalarinda su kalip vardi:
```yaml
web-login: "... <click:open_url:'<url>'><underlined><url></underlined></click> ..."
```
MiniMessage bir etiketin **ARGUMANI** icindeki yer tutucuyu **cozmez**. `<url>`
degeri gecirilse bile literal `<url>` metni kaliyor, Paper bunu adres sanip
`https://<url>` kurmaya calisiyor ve URI ayristirmasi patliyor. Hata sohbet paketi
kodlanirken olustugu icin mesaj hic gonderilmiyor.

**Cozum:** Baglanti artik dil dosyasinda degil, KODDA bir bilesen olarak kuruluyor:
```java
LangService.link("url", adres)   // Component + ClickEvent.openUrl
```
Dil dosyasinda yalnizca `<url>` durur. Ayrica `LangService` yuklemede eski bozuk
kalibi tespit edip **bellekte onarir** ve uyari verir — kullanicinin dil dosyalari
asla ezilmedigi icin eski kurulumlarda bu kalip yoksa da kalabilir.

## 12. "Aktif modul: 24/26" ama hicbir sebep yazilmiyor

**Belirti:** Iki modul acilmamis, ne hata ne uyari var. `/adminmenu` calismiyor.

**Kok sebep:** Ozet satiri `FAILED` ve `SKIPPED` modulleri yaziyordu ama
`DISABLED` (modules.yml icinde kapatilmis) olanlari yazmiyordu. `panel` ve `pcoins`
kapaliydi — davranis dogruydu, **rapor eksikti**.

**Cozum:** Ozete `modules.yml icinde kapali: [...]` satiri eklendi.

> Not: `panel` ile `web` bilerek birbirini disler. Web panelini acmak icin
> `modules.yml` icinde `panel.enabled: false` + `web.enabled: true` yapmak
> DOGRU kullanimdir; `/adminmenu` bu durumda oyun ici menu yerine tarayici
> baglantisi vermek uzere `panel` modulune ihtiyac duyar — bu yuzden web modunda
> da `panel` ACIK kalmalidir. Ikisinin ayrimi `admin.mode` ile yapilir, modul
> acik/kapali ile degil.

## 13. Web paneli adresi olarak 0.0.0.0 gosterilmesi

8. maddedeki ile ayni karisiklik: `Web paneli: http://0.0.0.0:8080` satiri **bind**
adresini gosteriyordu. Artik dinlenen adres ve erisim adresi ayri yaziliyor;
`admin.web.public-url` bossa bu acikca belirtiliyor.

## 14. Web paneli: dogru baglantiyla girilse bile "Once oyun icinden giris yapmalisin"

**Belirti:** Oyun ici baglantiya tiklaniyor, tarayici `/panel` adresine yonleniyor
ama 401 hata sayfasi cikiyor.

**Kok sebep — CALISIYORMUS GIBI DURAN OLU KOD.** `WebModule` icinde
`consumeToken(token, player, name, admin)` diye bir metot vardi ve tam olarak
oturumu kurmasi gerekeni yapiyordu. **Ama hicbir yerden cagrilmiyordu.**

Gercek akis suydu:
```
/auth/{token}  ->  jeton dogrulandi ve TUKETILDI (oyun ici dogrulama calisti)
               ->  cerez birakildi
               ->  /panel'e yonlendirildi
/panel         ->  cerezi oturum tablosunda aradi
               ->  tablo BOS (kimse doldurmadi)  ->  401
```

Jeton tuketildigi icin ikinci deneme de calismiyordu — kullanici her seferinde
yeni baglanti almak zorunda kaliyor ve yine ayni hatayi aliyordu.

**Cozum:**
- `AuthModule.consumeWebToken()` artik `boolean` degil `Optional<UUID>` donuyor:
  "gecerli mi" bilgisi tek basina yetmez, **kimin girdigi** de gerekir.
- Oturum, jetonun tuketildigi yerde (`WebRoutes.authenticate`) kuruluyor.
- Yetkili bayragi oyuncunun izninden okunuyor; oyuncu cevrimdisiysa yetkisiz
  oturum aciliyor ve panelin yetkili uclari o oturuma kapali kaliyor.
- Olu `consumeToken` metodu silindi.

## 15. Mesajlarin Ingilizce gelmesi

**Belirti:** Turkce sunucuda oyuncuya "Register here:" yaziyor.

**Sebep:** `LangService` oyuncunun **istemci dilini** tercih ediyordu; Ingilizce
istemcili oyuncu Ingilizce metin aliyordu. Davranis dogruydu ama tek dilli bir
sunucu icin istenmeyen sonuc uretiyor: ceviri eksikse arayuz yarim gorunur.

**Cozum:** `config.yml` icine `language.follow-client` eklendi (varsayilan **false**).
- `false`: herkese `language.default` gonderilir.
- `true`: istemci dili desteklenen diller arasindaysa o kullanilir.

## 16. Web panelinde giris/kayit ekrani hic cikmiyordu

**Belirti:** Baglantiya tiklaninca panel dogrudan aciliyor; PIN hic sorulmuyor.

**Kok sebep (guvenlik acigi):** `/auth/{token}` jetonu tuketip oyuncuyu DOGRUDAN
`AUTHENTICATED` isaretliyordu. Jeton "kim" sorusunu cevaplar (baglanti oyuna
gonderildi), ama "sifreyi biliyor mu" sorusunu cevaplamaz. Baglantiyi ele geciren
biri PIN bilmeden hesaba girebilirdi; kaydi olmayan oyuncu icin ise hic hesap
olusmuyordu — bu yuzden "kayit sayfasi gelmedi".

**Cozum — akis ikiye ayrildi:**
```
/auth/{token}  -> jeton tuketilir, oturum DOGRULANMAMIS olarak acilir
               -> kaydi yoksa /register, varsa /login
/register      -> PIN + tekrar  -> AuthService.register
/login         -> PIN           -> AuthService.login
               -> basarili ise oturum dogrulanir + oyun ici durum acilir -> /panel
/panel         -> yalnizca DOGRULANMIS oturum
/api/*         -> yalnizca DOGRULANMIS oturum (yetkili uclar ayrica admin ister)
```
`WebSession` artik `authenticated` bayragi tasiyor ve jetonun kendisi yeterli
sayilmiyor. `AuthModule.consumeWebToken` oyuncuyu dogrulamiyor; dogrulama
`markAuthenticated` ile PIN kontrolunden SONRA yapiliyor.

## 17. Web paneli portu iki yerde tanimliydi (yalnizca biri kullaniliyordu)

**Belirti:** `config.yml` icinde `admin.web.port: 8091` yazili ama panel 8080'i
dinliyor. Hicbir hata yok; panel calisiyor, sadece yanlis portta.

**Kok sebep:** Port iki ayri dosyada tanimliydi:
- `modules/web.yml` -> `web.port` (GERCEKTEN kullanilan)
- `config.yml` -> `admin.web.port` (belgelenen ama okunmayan)

Kullanici dogal olarak `config.yml`'yi duzenliyor, hicbir sey degismiyor ve
sebebi anlasilmiyor.

**Cozum:** Tek kaynak. Tum web ayarlari `config.yml -> admin.web.*` altindan
okunuyor; `modules/web.yml` kullanilmiyor. `ConfigService.bind()` eklendi:
bir modul, cekirdek config'inin bir bolumunu kendi holder'ina baglayabiliyor
(ayni dosyayi `open()` ile tekrar acmak onceki baglantiyi dusururdu).

## 18. Acilista yapilandirma dogrulamasi

Yukaridaki sinif hatalar sessizce calisir gorunup baglantiyi kirdigi icin
`WebConfigCheck` eklendi. Acilista kontrol edilenler:

| Kontrol | Sonuc |
|---|---|
| `public-url` portu ile dinlenen port farkli | **SEVERE**: "PORT UYUMSUZLUGU… baglanti calismayacak" |
| `public-url` 80/443 (ters vekil) | sessiz gecer, normal kurulum |
| `public-url` 127.0.0.1 / localhost | uyari: uzaktan acilamaz |
| `public-url` 0.0.0.0 | **SEVERE**: dinleme adresi tarayiciya yazilamaz |
| `resource-pack.public-host` ile panel host'u farkli | uyari: muhtemelen yazim hatasi (.118 / .119) |

## 19. auth.mode PASSWORD iken form "PIN" diyordu

Web formundaki etiketler sabit "PIN" yaziyordu. Artik `auth.mode` degerine gore
"PIN" ya da "Parola" gosteriliyor; PIN modunda sayisal klavye aciliyor, parola
modunda normal klavye.

## 20. "Config'i hic okumuyor" — config.yml bolumleri dekoratifti

**Belirti:** `config.yml` icindeki `resource-pack.public-host` doldurulmus ama log
"public-host bos" diyor. Ayni sey `admin.*` ve `auth.*` icin de gecerli.

**Kok sebep:** Ayarlar `config.yml`'de BELGELENIYOR ama baska dosyadan OKUNUYORDU:

| config.yml bolumu | Gercekte okunan dosya |
|---|---|
| `resource-pack.*` | `modules/content.yml` |
| `auth.*` | `modules/auth.yml` |
| `admin.*` | `modules/panel.yml` |
| `admin.web.*` | `modules/web.yml` (17. maddede duzeltildi) |

Kullanici dogru yeri duzenliyor, kod baska yere bakiyor, hata mesaji da yok.
`admin.mode` ise iki yerden birden okunuyordu: bazi siniflar `config.yml`'den,
`AdminPanelModule` ise `modules/panel.yml`'den — yani ayni ayar iki farkli deger
tasiyabiliyordu.

**Cozum (uc katmanli):**

1. **Tek kaynak.** `resource-pack.*`, `auth.*`, `admin.*` artik `config.yml`'den
   okunuyor. `ConfigService.bind()` ile moduller cekirdek config'inin bir bolumunu
   kendi holder'ina bagliyor.

2. **Otomatik gocs.** `LegacyConfigMigration`: eski `modules/*.yml` dosyalari varsa
   degerleri `config.yml`'ye tasinir ve dosyalar `.tasindi` uzantisiyla saklanir
   (silinmez — hatali bir tasimada geri donulebilmeli). Eski dosyadaki deger daha
   guncel sayilir, cunku o dosya o zamana kadar GERCEKTEN okunan dosyaydi.

3. **Regresyon korumasi.**
   - Calisma zamani: `ConfigService` ayni yolun iki dosyada tanimlandigini
     gorurse uyarir ("config.yml'deki deger YOKSAYILIYOR").
   - Derleme zamani: `ConfigPathTest` kaynak kodunu tarar; `config.yml`'de bulunan
     bir kok bolum baska dosyaya baglanmissa **test kirilir**.
     (Kasitli bir cakisma ile dogrulandi: test gercekten yakaliyor.)

4. **Gorunurluk.** Acilista okunan degerler yaziliyor:
```
[AethelCore] Okunan ayarlar (config.yml):
[AethelCore]   dil: tr (istemci dilini takip: true)
[AethelCore]   panel modu: WEB · web 0.0.0.0:8091
[AethelCore]   giris: PASSWORD · sure asimi 60 sn
[AethelCore]   paket: host='193.164.7.118' port=8085 zorunlu=true
[AethelCore]   veritabani: SQLITE
```

## Beklenen acilis ciktisi (duzeltmelerden sonra)

```
[AethelCore] Paket katmani yuklendi.
[AethelCore] Yuklenen diller: [en, tr]
[AethelCore] Veritabani hazir: SQLITE
[AethelCore] Modul yukleme sirasi: [profile, auth, permissions, economy, ...]
[AethelCore] Kayitli kok komut: N
[AethelCore] Aktif modul: 25/25
[AethelCore] Cekirdek hazir.
```

Paket katmani yine desteklemezse beklenen cikti:
```
[AethelCore] Paket katmani yuklenemedi; hologram, NPC ve waypoint modulleri devre disi kalacak
[AethelCore] Modul atlandi (hologram): paket katmani kullanilamiyor
[AethelCore] Aktif modul: 18/21
```
Sunucu bu durumda da normal calisir.

## 21. Web paneli bembeyaz aciliyor, hicbir sey cizilmiyor

**Belirti:** `/panel` 200 donuyor, HTML geliyor ama sayfa tamamen bos.
Tarayici konsolunda: `[dc] failed to load React or boot`.

**Kok neden:** `Panel.html` bir tasarim tuvali (design canvas) ciktisidir. `support.js`
sayfayi React ile calisma aninda derler ve React'i unpkg.com'dan ceker. Tarayici
unpkg'e ulasamiyorsa (kurum agi, reklam engelleyici, internete kapali makine)
React hic yuklenmez ve `<x-dc>` govdesi bos kalir. Sunucu tarafinda hicbir hata
gorunmez, cunku hata tarayicida olur.

**Cozum:** React ve ReactDOM JAR icine alindi (`web/vendor/`) ve `/vendor/*`
adresinden sunuluyor. `Panel.html` icinde `support.js`'ten ONCE calisan kucuk bir
blok `window.__resources` haritasini kuruyor; `support.js` bu haritaya bakip
unpkg yerine yerel yolu kullaniyor. Panel artik internet olmadan da aciliyor.

**Dikkat:** Panel.html tasarim tuvalinden yeniden disa aktarilirsa bu blok kaybolur
ve panel yine beyaz acilir. Yeni disa aktarimda blogun geri eklenmesi gerekir;
dosyanin icinde bunu soyleyen bir yorum var.

Inter yazi tipi hala Google Fonts'tan cekiliyor ama zorunlu degil: `system-ui`
yedegi devrede, duzen bozulmaz.

## 22. Panel acildi ama butun bolumler ornek veri gosteriyor

**Belirti:** Panelde kayitlar var ama sunucudaki gercek kayitlar degil; ust seritte
"canli veri" rozeti gorunmuyor.

**Kok neden:** `Panel.html`, `/api/records?section=<id>` cagrisi basarisiz olursa
(404, 401 ya da ag hatasi) sessizce kendi gomulu ornek verisine duser --
`catch (e) {}`. Bu bilincli bir tasarim: panel API olmadan da acilir. Ama teshis
koymayi zorlastirir, cunku ekranda hicbir hata gorunmez.

**Kontrol sirasi:**
1. Tarayici ag sekmesinde `/api/records?section=items` cagrisinin durum kodu.
2. **401** ise oturum yok: `/adminmenu` ile yeni baglanti al ve PIN gir.
3. **403** ise oturum var ama `aethel.admin.panel` izni yok.
4. **404** ise bolum kimligi `PanelSchema` icinde tanimli degil. Panel.html'deki
   `SCHEMAS` anahtarlari ile `PanelSchema` bolum kimlikleri BIREBIR ayni olmalidir.
