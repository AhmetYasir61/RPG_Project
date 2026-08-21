# Oyun Tasarim Kararlari (mimariyi baglayan kisitlar)

Sunucu: **fantasy MMORPG**, tek plugin, ~100 oyuncu, proxy yok (Velocity-ready).

## 1. Skill sistemi: yalnizca particle
Skill tanimlarinda **model, display entity, armor stand, item display KULLANILMAZ.**
Her yetenegin gorsel dili particle primitifleri ile kurulur:

    shape (circle / ring / helix / cone / beam / sphere / wave / burst)
  × motion (static / expand / travel / orbit / fall)
  × timing (tick araligi, sure, adim sayisi)
  × particle tipi + renk + hiz + offset

Bunun sonucu: `SkillDefinition` icinde entity spawn API'si yoktur; derleyici
seviyesinde engellidir (skill DSL'i yalnizca `ParticleEffect` uretir).
Neden: entity tabanli efekt her oyuncu icin sunucu tarafinda tick yer ve 100 kisilik
bir savasta entity sayisi patlar. Particle ise **paket** olarak gonderilir, sunucuda
hicbir entity tick'i olusturmaz ve gorunurluk mesafesi ile ucuzca kisilabilir.

## 2. Moblar: entity, gorunum serbest
Moblar gercek entity'dir. Custom gorunum (ModelEngine tarzi modelli mob) desteklenir.
Kisit yalnizca skill tanimindadir. `MobDefinition` -> gorunum katmani serbest,
`SkillDefinition` -> sadece particle.

## 3. Item: 2D custom texture + NBT
Custom item'lar 3D model degil, **2D texture**. Kimlik PDC icinde `aethel:item_id`
olarak kalici tutulur, gorunum `item_model` data component'i ile baglanir.
Resource pack pipeline'i bu yuzden sadelesir: item texture + font/GUI uretimi.

## 4. Seyahat: serbest teleport YOK
MMORPG hissi icin mesafe gercek bir maliyet olmali.

| Klasik EssentialsX | Aethel karsiligi |
|---|---|
| `/tpa` anlik isinma | **Teleport parsomeni** (tuketilir, cast suresi, hasar alinca iptal) |
| `/home` sinirsiz | **Hearthstone** — uzun cooldown, tek bag noktasi |
| `/warp` herkese acik | **Kesfedilmis waypoint** — once yuruyerek gitmis olmak sart |
| `/tp <oyuncu>` | Yok (yalnizca yetkili) |

**Arkadas takibi (party waypoint):** parti uyesinin konumu, dunyada duvar arkasindan
da gorunen **saydam kafa ikonu** + mesafe/koordinat olarak gosterilir. Bu bir HUD
isaretidir, isinma degil: oyuncu ya yuruyecek ya parsomen harcayacak.
Teknik: per-player paket ile gonderilen isaret, oyuncunun kendi istemcisinde cizilir;
sunucuda entity olusmaz.

## 5. Bunun modul haritasina etkisi
- `essentials` modulu **`travel`** olarak yeniden adlandirildi.
- Yeni modul: **`party`** (grup, ortak XP/loot, waypoint paylasimi).
- `travel` bagimliliklari: Profile, Content (parsomen item'i), Party (uye waypoint'i).

## 6. Yonetim: komut degil, panel
Sunucuda **yonetim komutu yoktur**. Tum yonetim tek bir giristen yapilir:

    /adminmenu     (tek komut, yetki: aethel.admin.panel)

Bu komut `admin.mode` ayarina gore davranir ve **iki mod ayni anda acik olamaz**:

```yaml
admin:
  mode: GUI        # GUI | WEB  (birbirini disler, cekirdek boot'ta dogrular)
  permission: aethel.admin.panel
  web:
    bind: 0.0.0.0
    port: 8080
    session-timeout-minutes: 30
```

- **GUI modu:** oyun ici menu tabanli panel. Metin girisi chat'ten DEGIL, **anvil GUI**
  uzerinden alinir (oyuncu chat'e dusmez, panel akisi kesilmez).
- **WEB modu:** tarayici tabanli panel (dahili Javalin sunucusu). `/adminmenu`
  bu modda tek kullanimlik, sureli bir giris baglantisi uretir.
- Mod WEB iken GUI paneli acilmaz, GUI iken web portu hic dinlenmez. Ikisi de acik
  birakilirsa boot uyari verip GUI'ye duser: iki yerden ayni kaydi duzenlemek
  cakisma ve veri kaybi uretir.

**Oyuncu komutlari bundan muaf:** `/parti`, `/ticaret`, `/waypoint` gibi oyuncu
komutlari normal sekilde chat uzerinden `/` ile kullanilir. Kisit yalnizca yonetim
tarafindadir.

## 7. Icerik yonetimi ve varlik (asset) hattı
Panelden (GUI ya da WEB) yonetilen her sey ayni servis katmanini kullanir; panel
yalnizca bir on yuzdur, is mantigi modullerde durur.

| Yonetilen | GUI | WEB |
|---|---|---|
| Item / blok tanimi | var | var |
| NPC yerlestirme ve duzenleme | var | var |
| Dialog / typewriter agaci | var | var (dugum editoru) |
| Mob ve loot tablosu | var | var |
| Model yukleme (.bbmodel) | — | var + **3D onizleme** |
| Texture yukleme (.png / Aseprite ciktisi) | — | var + **onizleme** |

Yukleme yalnizca WEB modunda mantikli (dosya secici gerekir); GUI modunda varliklar
`content/textures/` ve `content/models/` klasorune elle birakilir, panel yalnizca
listeler ve baglar.

**Kabul edilen formatlar:** Aseprite'tan disari verilen `.png` (item/GUI texture) ve
Blockbench `.bbmodel` (mob modeli). `.bbmodel` sunucu tarafinda ayristirilip resource
pack'e uygun model JSON'una cevrilir; kaynak dosya panelde tekrar duzenlenebilsin diye
saklanir.

## 8. Dialog / typewriter sistemi
NPC konusmalari harf harf akan (typewriter) bir diyalog sistemidir: dugumler,
secenekler, kosullar (gorev durumu, seviye, item) ve sonuclar (gorev ver, item ver,
ticaret ac). Tamamen panelden duzenlenir, YAML olarak diske yazilir.
Gorsel katman particle/action bar/boss bar ile kurulur — skill kuralindaki gibi
diyalog da entity gerektirmez.

## 9. Bunun modul haritasina etkisi
Yeni moduller: **`panel`** (GUI admin paneli + anvil metin girisi),
**`web`** (Javalin panel + varlik yukleme/onizleme), **`dialog`** (typewriter),
**`assets`** (png/bbmodel kaydi, dogrulama, resource pack'e baglama).

## 10. Kendi HUD sistemimiz (BetterHUD karsiligi)
Ekran ustu arayuz de disaridan gelmez; `hud` modulu cekirdegin parcasidir.

Teknik temel: **negatif bosluk (negative space) fontu**. `assets/aethel/font/*.json`
icinde her HUD parcasi bir karaktere baglanir; negatif genislikli karakterler imleci
geri iterek parcalari ust uste bindirir. Boylece can bari, mana, XP, hedef bilgisi
ve mini gostergelerin tamami **tek bir action bar / boss bar satiri** ile cizilir.
Entity, scoreboard hilesi veya ekstra paket gerekmez.

Neden bu yol: alternatifler scoreboard (sag ust, sinirli ve titrek), display entity
(sunucuda tick maliyeti — skill kuralimiza da aykiri) ve boss bar (tek satir, stil yok).
Font tabanli HUD istemcide cizilir, sunucu yalnizca kisa bir metin gonderir; 100
oyuncuda maliyet pratikte sifirdir. Bedeli: resource pack zorunlu olur ve HUD
tasarimi piksel hesabi ister — bu hesabi resource pack generator otomatik yapar.

`hud` modulu ozellikleri:
- YAML'den HUD layout (katman, offset, kosul)
- Per-player, placeholder destekli (kendi `%aethel_...%` sistemimiz)
- Durum bazli gosterim (savasta / gorevde / dungeon icinde farkli HUD)
- Panel uzerinden duzenleme ve canli onizleme

## 11. Placeholder sistemi
Kendi motorumuz: `%aethel_<modul>_<anahtar>%`. Modul kendi cozucusunu kaydeder,
cekirdek onbellekler (tick basina tek cozum). PlaceholderAPI kuruluysa iki yonlu
kopru kurulur: bizim placeholder'lar PAPI'ye kaydedilir, PAPI placeholder'lari
bizim metinlerimizde calisir. PAPI yoksa hicbir sey degismez.

## 12. Bolge sistemi ve dinamik loot sandiklari

### 12.1 Bolge secimi: kare degil, sekil
Bolge secimi tek bir kutu ile sinirli degil. Secim aracı su sekilleri destekler:

| Sekil | Kullanim | Veri |
|---|---|---|
| `CUBOID` | oda, bina, klasik claim | iki kose |
| `POLYGON` | dungeon salonu, duzensiz alan | N kose + min/max Y |
| `TRIANGLE` | dar geciş, kama alan | uc kose + min/max Y |
| `LINE` (serit/cubuk) | koridor, kopru, yol | iki nokta + genislik (yaricap) |
| `SPHERE` | boss arena, aura alani | merkez + yaricap |

Icerde-mi testi sekle gore ayrisir; `LINE` icin nokta-dogru parcasi mesafesi,
`POLYGON` icin ray casting kullanilir. Hepsi once **AABB on eleme**den gecer:
pahali geometri testi yalnizca kaba kutuya giren adaylar icin calisir, boylece her
harekette tum bolgeleri gezmek zorunda kalmayiz.

### 12.2 Zorluk: isim + yuzde
Zorluk iki parcadir ve panelden kaydedilir:

```yaml
difficulties:
  zor:
    display: "<red>Zor</red>"
    percent: 78          # 0-200 arasi; 100 = temel
    icon: ""       # sag capraz kucuk ikon (font karakteri)
    hardcore: false
```

Menude **"Zor"** yazar, sag ust caprazinda `%78` degerini tasiyan **kucuk bir ikon**
gorunur. Ikon bir font karakteridir (HUD ile ayni negatif bosluk teknigi), boylece
item lore'una veya menu basligina piksel hassasiyetinde yerlesir.

`percent` sunucuda su carpanlara baglanir: mob can, mob hasar, spawn yogunlugu,
loot nadirligi, XP ve para kazanci. `hardcore: true` ise olumde ek ceza (ekipman
dayanikliligi, gecici debuff) devreye girer. Tumu panelden duzenlenir.

### 12.3 Loot sandigi: tehlike -> odul
Sandik nadirligi sabit degil, **bolgenin anlik tehdit yoguntlugundan** hesaplanir:

    tehdit = Σ (mob.tier^1.5 × mob.canOrani) / bolgeHacmiNormalize
    nadirlikCarpani = 1 + log2(1 + tehdit) × zorluk.percent / 100

Yani bir bolgede **cok sayida mob** ya da **az ama cok guclu mob** olmasi ayni
tehdit puanina cikabilir; ikisi de sandigin nadirligini yukseltir. `log2` kullanmamizin
sebebi: mob yigmakla odulu sinirsiz sismesini engellemek — 10 kat mob, 3-4 kat odul
verir, oyuncular sandigi farm makinesine cevirmez.

Sandik acildiginda tehdit **o an** olculur; onceden hesaplanip onbelleklenmez, cunku
oyuncunun bolgeyi temizleyip sonra acmasi ile kalabalikken acmasi arasindaki fark
mekanigin ta kendisidir.

Yeni modul: **`loot`** (sandik tanimi, loot tablosu, tehdit hesabi, respawn).
`region` modulu sekil ve zorluk verisini saglar.

## 13. Resource pack: zorunlu, kendi sunucumuzdan
Oyuncuya "indirmek ister misin?" diye **sorulmaz**. Pack `required` bayragi ile
gonderilir; istemci reddederse ya da indirme basarisiz olursa oyuncu bilgilendirici
bir mesajla **kicklenir**. Sebep: HUD, custom item ve menu arayuzunun tamami pack'e
bagli; pack'siz oyuncu bozuk bir oyun gorur.

Dagitim kendi altyapimizdan yapilir — harici host yok:

```yaml
resource-pack:
  serve: true
  bind: "0.0.0.0"
  port: 8085
  # Oyuncuya gonderilen adres. Bos birakilirsa sunucunun kendi IP'si kullanilir.
  public-host: ""          # orn. "cdn.sunucum.net" veya dogrudan IP
  force: true              # required=true, reddedince kick
  kick-message-key: "pack.declined"
```

Akis: generator pack'i uretir -> zip -> SHA-1 -> dahili HTTP sunucusu
`http://<ip>:<port>/pack.zip` adresinden servis eder -> `PlayerJoinEvent` yerine
**config asamasinda** gonderilir (oyuncu dunyaya girmeden inmeye baslar) ->
`PlayerResourcePackStatusEvent` sonucu DECLINED/FAILED_DOWNLOAD ise kick.

SHA-1 her uretimde degistigi icin istemci onbellegi kendiliginden tazelenir; pack
degismediginde ayni hash doner ve oyuncu tekrar indirmez.

## 14. Resource pack uretim hatti: `contents/` + `blueprints/` -> `generated.zip`

ItemsAdder'daki gibi klasor tabanli, ama tek fark: **hicbir sey elle JSON yazmaz.**
Sunucu acilirken (ve `/adminmenu > Pack > Yeniden Uret` ile) klasorler taranir,
tum vanilla JSON'lari uretilir, zip'lenir, SHA-1 alinir ve servis edilir.

### 14.1 Klasor duzeni

```
plugins/AethelCore/
├─ contents/                     <- ICERIK TANIMLARI (YAML) + ham varliklar
│  └─ <namespace>/               <- orn. aethel, dungeon_pack, sezon1
│     ├─ items/*.yml             item tanimlari
│     ├─ blocks/*.yml            custom blok tanimlari (note block state)
│     ├─ mobs/*.yml              mob tanimlari
│     ├─ hud/*.yml               HUD layout'lari
│     ├─ fonts/*.yml             emoji / ikon / negatif bosluk tanimlari
│     ├─ gui/*.yml               menu arka plan tanimlari
│     └─ textures/               ham .png (Aseprite ciktisi)
│        ├─ item/kilic_alev.png
│        ├─ gui/panel_arkaplan.png
│        └─ font/ikon_zorluk.png
│
├─ blueprints/                   <- MODEL KAYNAKLARI
│  └─ <namespace>/
│     ├─ *.bbmodel               Blockbench kaynak dosyasi (duzenlenebilir kalir)
│     └─ *.json                  elle yazilmis / disari verilmis model (aynen kopyalanir)
│
├─ generated/
│  ├─ pack/                      <- uretilen acik pack agaci (ayiklama/debug icin)
│  ├─ generated.zip              <- oyuncuya gonderilen pack
│  └─ generated.sha1             <- son hash, degisiklik tespiti icin
│
└─ cache/pack-index.json         <- kaynak dosya -> uretilen dosya + hash haritasi
```

`generated/` klasoru tamamen turetilmistir: silinebilir, bir sonraki acilista
yeniden uretilir. Kaynak yalnizca `contents/` ve `blueprints/` altindadir; yedeklenmesi
gereken de sadece bunlardir.

### 14.2 Uretim adimlari

```
1. TARA        contents/**/*.yml  +  contents/**/textures/**.png
               blueprints/**/*.bbmodel  +  blueprints/**/*.json
2. DOGRULA     - ayni id iki kez tanimlanmis mi
               - texture dosyasi var mi, boyutu 2'nin kuvveti mi
               - note block state havuzu tukendi mi (1149 kombinasyon)
               - bbmodel surumu destekleniyor mu
3. TAHSIS ET   her item -> item_model kimligi (CMD fallback numarasi ile birlikte)
               her blok -> (instrument, note, powered) uclusu   [KALICI, cache'de tutulur]
               her font parcasi -> Unicode ozel kullanim alani karakteri (U+E000+)
4. CEVIR       .bbmodel  -> assets/<ns>/models/**/*.json   (+ gerekiyorsa animasyon verisi)
               .png      -> assets/<ns>/textures/**/*.png  (aynen kopya)
               fonts     -> assets/<ns>/font/*.json        (negatif bosluk dahil)
               items     -> assets/<ns>/items/*.json       (item_model tanimi)
               blocks    -> assets/minecraft/blockstates/note_block.json  (birlestirilmis)
               atlas     -> assets/minecraft/atlases/blocks.json
5. PAKETLE     pack.mcmeta + pack.png yaz -> generated/pack/ -> generated.zip
6. HASH        SHA-1 hesapla -> generated.sha1
7. SERVIS ET   dahili HTTP: http://<public-host>:<port>/generated.zip
```

### 14.3 Kimlik tahsisi neden cache'de kalici tutuluyor
Bir custom bloga atanan `(instrument, note, powered)` uclusu ya da bir item'a atanan
model kimligi **degisemez**. Degisirse dunyada duran bloklar baska bir bloga donusur,
sandiktaki item'lar baska bir gorunume kayar. Bu yuzden tahsis edilen her kimlik
`cache/pack-index.json` icinde saklanir ve yeniden uretimde aynen korunur; yalnizca
yeni eklenen icerikler bos slotlardan tahsis alir. Silinen icerigin slotu ise
"mezarlik"ta tutulur, hemen yeniden kullanilmaz — eski dunya parcalari yanlis
esleme yapmasin diye.

### 14.4 .bbmodel -> vanilla JSON cevirisi
Blockbench dosyasi bizim icin **kaynak**tir, cikti degil. Cevirici sunlari yapar:
- `elements` -> vanilla `elements` (from/to, rotation, uv, faces)
- `textures` -> `assets/<ns>/textures/...` altina yazilir, `#0`, `#1` referanslari baglanir
- `display` -> vanilla `display` (thirdperson, gui, head vb.)
- desteklenmeyen ozellikler (mesh, ozel animasyon) uyari verir, uretim durmaz

`.bbmodel` dosyasi silinmez ve panelde tekrar acilabilir; boylece bir modeli
duzenlemek icin kimsenin uretilen JSON'a dokunmasi gerekmez.

### 14.5 Artimli uretim
`cache/pack-index.json` her kaynak dosyanin hash'ini tutar. Yeniden uretimde yalnizca
degisen dosyalar islenir; 500 item'lik bir pakette tam uretim saniyeler surerken
tek bir texture degisikligi milisaniyelere iner. `--force` ile tam uretim zorlanir.

Bu is **ana thread'de yapilmaz**: tarama, cevirme, zip ve SHA-1 sanal thread'de
calisir, bittiginde pack ana thread'de servis edilmeye baslanir.

## 15. Ekonomi: cift para birimi + PCoins backend

Iki ayri para vardir ve **birbirine karismaz**:

| Para | Nerede kazanilir | Nerede harcanir | Kaynak |
|---|---|---|---|
| **Altin** (oyun ici) | mob, gorev, meslek, ticaret | NPC dukkan, oyuncu pazari, tamir | sunucu veritabani |
| **PCoins** (marka parasi) | **satin alinir** (site/launcher) | market: buff paketi, kozmetik, slot | merkezi PCoins backend |

Altin icin **Vault provider** olarak kendimizi kaydederiz; Vault kuruluysa diger
pluginler `EconomyService`'imizi gorur, kurulu degilse hicbir sey degismez.
PCoins **Vault'a baglanmaz** — kasten: Vault tek para birimi varsayar ve ucuncu
parti bir plugin marka paramizi yanlislikla harcayabilir.

### 15.1 PCoins backend baglantisi
```yaml
pcoins:
  enabled: true
  endpoint: "https://api.pokewing.net/v1"
  api-key-file: "pcoins-key.txt"   # anahtar config icinde DEGIL, ayri dosyada
  sync-interval-seconds: 60
  offline-mode: QUEUE              # QUEUE | REJECT
```

Akis (site/launcher'dan oyun icine):
```
Site: oyuncu PCoins satin alir
  -> backend bakiyeyi yazar, bir "islem" kaydi olusturur
  -> sunucu her 60 sn (ve oyuncu girisinde) bekleyen islemleri ceker
  -> islem id'si ile idempotent uygulanir (ayni islem iki kez islenmez)
  -> oyuncuya bildirim + HUD guncellemesi
```

Harcama tersine calisir: sunucu **once backend'e rezervasyon** yapar, backend
onaylayinca item/buff verilir. Neden bu sira: once item verip sonra dusmeye calismak,
backend erisilemezse bedava item uretir. Rezervasyon zaman asimina ugrarsa
otomatik geri alinir.

`offline-mode: QUEUE` -> backend erisilemezken kazanimlar kuyruga alinir, harcama
reddedilir. `REJECT` -> her iki yon de reddedilir. Varsayilan QUEUE.

**Guvenlik:** API anahtari config icinde tutulmaz (config paylasilir, ekran goruntusu
alinir), ayri bir dosyadadir ve loglara hicbir zaman yazilmaz. Tum istekler HMAC
imzalidir; sunucu ile backend arasindaki saat farki 5 dakikayi asarsa istek reddedilir.

## 16. Jobs (meslek) sistemi
Klasik Jobs mantigi, MMORPG'ye uyarlanmis: her meslek kendi seviyesi, kendi XP egrisi
ve kendi yetenek dalini tasir.

```yaml
jobs:
  madenci:
    display: "<gray>Madenci</gray>"
    max-level: 100
    actions:
      BREAK:
        DIAMOND_ORE: { xp: 12.0, money: 4.5 }
        IRON_ORE:    { xp: 3.0,  money: 1.2 }
    perks:
      10: "cift-dusme-sansi:5"
      25: "kazma-hizi:1"
```
Ayni anda tutulabilecek meslek sayisi sinirlidir (varsayilan 2); meslek birakmak
soguma suresine tabidir. Amaci: oyuncularin her seyi ayni anda yapmasini engellemek,
ekonomide uzmanlasma ve ticaret olusturmak.

## 17. Skill tree (yetenek agaci)
Iki katman:
- **Sinif agaci** — RPG modulu (savasci/buyucu/okcu vb.), yetenek puani ile acilir.
- **Meslek agaci** — Jobs modulu, meslek seviyesi ile acilir.

Dugum tipleri: `PASSIVE` (kalici stat), `ACTIVE` (kullanilabilir skill — **particle**),
`MODIFIER` (baska bir yetenegi degistirir). Agac YAML'de tanimlanir, panelden
duzenlenir; her dugumun on kosulu ve puan maliyeti vardir. Sifirlama bir item ya da
PCoins ile yapilir.

## 18. Profil komutlari ve yetkili denetimi

| Komut | Kim | GUI modu | WEB modu |
|---|---|---|---|
| `/profil` | herkes | oyun ici GUI profil | tarayicida kendi profili |
| `/profiles <oyuncu>` | `aethel.profile.inspect` yetkisi | oyun ici GUI, **salt okunur** | tarayicida tam denetim |

**GUI modunda envanter mudahalesi YOKTUR** — salt okunur gorunum. Sebep: oyun ici
bir GUI'de yanlis tiklama ile oyuncunun esyasini silmek geri alinamaz ve denetim izi
birakmaz.

**WEB modunda** yetkili sunlari yapabilir: envanter ve zirhi gorme, tek item silme,
item'a el koyma (kanit deposu), bakiye duzeltme, ceza gecmisi. Her islem
**denetim kaydina** yazilir: kim, ne zaman, hangi oyuncuda, ne yapti, hangi item.
El konulan esya silinmez, "kanit" tablosuna tasinir ve geri verilebilir.

## 19. Yetki ve rank: kendi LuckPerms'imiz
Ayri plugin yok; `permissions` modulu cekirdegin parcasi.

Ozellikler: grup + kalitim (inheritance), oyuncu bazli izin, gecici izin/rank
(sureli), dunya bazli izin, prefix/suffix ve agirlik (weight), izin cakismalarinda
en spesifik kazanir.

Yonetim yine tek kapidan:
- **WEB modu:** tarayicida grup agaci, surukle-birak kalitim, izin arama, toplu islem.
- **GUI modu:** menu + anvil. Izin ekleme anvil ile yazilir (chat kullanilmaz),
  gruplar menude listelenir, kalitim menu ici secimle kurulur.

Her iki panel de ayni `PermissionService`'i cagirir; is mantigi tek yerdedir.

Yeni moduller: **`jobs`**, **`pcoins`** (backend koprusu). `economy` altin icin
Vault provider olarak kendini kaydeder.

## 20. Giris sistemi (auth) ve veritabani yedegi

### 20.1 Iki akis, tek mantik
`admin.mode` degeri giris akisini da belirler:

- **GUI modu — AuthMe benzeri:** oyuncu girer, **anvil** ekrani acilir, PIN'ini yazar.
  Kayitta PIN iki kez sorulur (yanlis yazilmis bir PIN oyuncuyu hesabindan tamamen
  kilitler). Chat hicbir asamada kullanilmaz.
- **WEB modu:** oyuncuya tiklanabilir, **tek kullanimlik** bir baglanti gonderilir.
  Tarayicida kayit/giris tamamlanir, jeton tuketilir ve oyuncu oyunda otomatik
  dogrulanir — yani "web'e yonlendir, isini bitir, oyuna don" akisi.

Dogrulanmamis oyuncu: hareket edemez (bakinabilir), konusamaz, komut kullanamaz
(yalnizca giris komutlari), blok kiramaz/koyamaz, envanter acamaz ve **hasar almaz** —
giris ekranindayken olup esyasini kaybetmesin diye.

### 20.2 Sir saklama
PIN/parola **duz saklanmaz ve geri donusturulemez**. PBKDF2-HMAC-SHA256, kayit basina
rastgele tuz, 210.000 iterasyon (OWASP'in SHA-256 icin guncel alt siniri).
Duz SHA-256 kullanmiyoruz: GPU ile saniyede milyarlarca deneme yapilabilir ve 4 haneli
bir PIN aninda kirilir. Karsilastirma sabit surelidir (zamanlama saldirisi kapali).

Ek onlemler: N basarisiz denemeden sonra kick, giris suresi asiminda kick, ayni IP'den
kisa sure icinde donen oyuncu icin oturum hatirlama, yetkili sifirlamalari denetim
tablosuna yazilir (kayit asla sessizce silinmez).

### 20.3 Veritabani: uzak varsa uzak, yoksa yerel
Tum sistem tek bir `Database` katmanindan gecer:

```
storage.type: MYSQL   -> uzak MySQL/MariaDB (HikariCP havuzu, onerilen)
storage.type: SQLITE  -> plugin klasorundeki yerel data.db
```

Uzak veritabani **yapilandirilmissa ve erisilebiliyorsa** oradan calisir; erisilemezse
cekirdek yerel SQLite'a duser ve bunu acikca loglar. Boylece sunucu, veritabani
sorunu yuzunden hic acilmamak yerine calismaya devam eder.

SQLite havuzu 1 baglanti ile sinirlidir (tek yazar destegi; buyutmek SQLITE_BUSY
uretir). Sema farklari `SqlDialect` icinde tek yerde toplanir; modul migration'lari
`{id} {uuid} {text}` yer tutucularini kullanarak iki motorda da ayni SQL ile calisir.
