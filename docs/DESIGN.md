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

## 21. Ozellik anahtarlari (feature toggle)

Modul acip kapatmanin bir seviye altinda, **ozellik** bazli acma/kapama vardir.
Kural net: **`false` olan ozellik oyunda hic yokmus gibi davranir.**

### 21.1 "Kapali" ne demek
Bir bayragi kontrol edip mesaj basmak yeterli DEGILDIR; bu, mekanigin varligini
oyuncuya sizdirir. Kapali bir ozellik icin:

| Katman | Kapaliyken |
|---|---|
| Komut | Brigadier agacinda **yok**: tab-complete'te cikmaz, "bilinmeyen komut" doner |
| Listener | Bukkit'e **hic kaydedilmez** — olay islenmez, bos is bile yapilmaz |
| Zamanlanmis gorev | Kurulmaz (HUD kapaliysa her 4 tick'te bos donmez, hic donmez) |
| Menu / panel | Ilgili girdi cizilmez |
| Servis davranisi | Ilgili metot notr sonuc doner (orn. mana kapaliysa `consumeMana` daima true) |

**Yetki reddi ile ozellik kapaliligi arasindaki fark:** yetki reddi "bunu yapamazsin"
der ve mekanigin var oldugunu soyler; ozellik kapaliligi "boyle bir sey yok" der.
MMORPG'de bu fark onemlidir — kapatilmis bir mekanigin izini birakmak, oyunculari
olmayan bir sistemi aramaya iter.

### 21.2 Kullanim
```yaml
# features.yml
features:
  travel:
    scroll: true
    hearthstone: false     # /waypoint ocak komutu artik YOK
  rpg:
    skill-tree: true
    mana: false            # yetenekler bedelsiz calisir, mana gorevi hic kurulmaz
```

Calisma zamaninda:
```
/core ozellikler                      # tum anahtarlar, durum ve aciklama
/core ozellik travel.scroll false     # aninda kapanir, restart gerekmez
/adminmenu > Ozellikler               # menuden tiklayarak
```

### 21.3 Kendi kendini dolduran dosya
Moduller `onLoad` icinde kendi anahtarlarini `declare(key, default, aciklama)` ile
bildirir; `features.yml` kendiliginden dolar. **Dosyada zaten bir deger varsa ona
dokunulmaz** — sunucu sahibinin kapattigi bir ozellik guncelleme sonrasi kendiliginden
acilmaz. Dosyada olmayan bir anahtar varsayilan olarak aciktir.

### 21.4 Neden listener'i gercekten dusuruyoruz
Kapali ozellik icin listener'i birakip metodun basinda `if (!enabled) return;` yazmak
kolay yoldur ama her olayda bir metot cagrisi ve bir kontrol maliyeti birakir.
100 oyunculu bir sunucuda `PlayerMoveEvent` saniyede binlerce kez tetiklenir; kapali
bir ozellik icin bu maliyeti odemek anlamsizdir. `FeatureRegistry` listener'lari sahiplenip
`HandlerList`'ten cikarir, ozellik acilinca yeniden baglar.

## 22. Dungeon sistemi

Dungeon bir **modul**dur (`dungeon`) ve icindeki her sey mevcut modullerimizden gelir:
moblar `MobService`'ten, sandiklar `LootService`'ten, zorluk `RegionService`'ten,
NPC ve hologramlar kendi modullerinden.

### 22.1 Uc asamali akis

```
  TASARIM                    URETIM                      OYUN
  ────────                   ──────                      ────
  duz tasarim dunyasi        oda havuzundan              girise basan oyuncu
  chunk chunk oda kur        rastgele + kurallı          yeni bir BOYUTA gecer
  isaret bloklarini koy      yerlesim uretilir           dungeon o an canlanir
  /dungeon kaydet <ad>       odalar chunk chunk yazilir  cekirdek kirilinca coker
```

### 22.2 Tasarim: 1 oda = 1 chunk
Odalar duz bir tasarim dunyasinda, **chunk sinirlarina hizali** kurulur. Bir chunk
bir odadir; boylece yerlesim izgarasi ile dunya koordinatlari birebir ortusur ve
oda birlestirmede hizalama hatasi olmaz.

Tasarimci odaya **isaret bloklari** koyar; bunlar export sirasinda okunur, marker'a
cevrilir ve **blok olarak kaydedilmez**:

| Blok | Anlami |
|---|---|
| Kirmizi yun | mob dogum noktasi (`MobService`) |
| Sari yun | loot sandigi (`LootService`, tehdit bazli nadirlik burada da calisir) |
| Mor yun | **dungeon cekirdegi** |
| Yesil yun | giris / dogum noktasi |
| Acik mavi yun | cikis kapisi |
| Turuncu yun | NPC · Macenta yun: hologram · Beyaz yun: spawn |
| Mavi yun | **kapi** — chunk'in hangi kenarina yakinsa o yon kapi sayilir |

`/dungeon kaydet <oda_adi>` bulunulan chunk'i tarar ve JSON olarak kaydeder.

### 22.3 Depolama: palet + indeks
Oda 16×16×24 = 6144 blok tutar. Her blok icin ham `BlockData` metni saklamak dosyayi
onlarca kat buyutur; bunun yerine **palet** (benzersiz blok metinleri) + **indeks
dizisi** kullaniliyor. Tipik bir odada 20-30 farkli blok olur, dosya kucuk kalir ve
okuma hizli olur.

### 22.4 Uretim: once ana yol, sonra dallar
Tamamen rastgele buyuyen bir labirentte cekirdek erisilemez bir kosede kalabilir ve
dungeon **oynanamaz** hale gelir. Bu yuzden:

1. Giristen cekirdege kendini kesmeyen bir **ana yol** kazilir (cozulebilirlik garanti).
2. Bu yola hazine/mob/cikmaz **dallar** eklenir.
3. Her hucreye, gereken **kapi maskesini tutan** bir oda secilir — 4 donus de denenir.

Kapi maskesi 4 bit (K/D/G/B). Oda dondurulunce maske de donduruluyor; aksi halde
dondurulmus oda komsulariyla eslesmez ve duvara acilan kapilar olusur.

### 22.5 Her ornek ayri bir boyut
Dungeon'a giren oyuncu **yeni bir dunyaya** gecer. Neden ayri dunya:

- **Izolasyon:** iki parti ayni dungeon'u ayni anda, birbirini gormeden oynar.
- **Temiz silme:** cokmede dunya klasoru silinir — bloklar, entity'ler, yerdeki
  esyalar, her sey tek hamlede gider. Ayni dunyada "temizleme" yapmak her zaman
  arkasinda artik birakir.
- **Kural izolasyonu:** dungeon dunyasinda gece akmaz, hava yoktur, dogal spawn
  kapalidir; bunlari ana dunyaya bulastirmayiz.

Dunya `VoidChunkGenerator` ile bostur: vanilla arazi uretimi (gurultu, magara, yapi)
tamamen kapali, cunku uzerine zaten kendi odalarimizi yaziyoruz.

### 22.6 Cikis tek yonlu
Cikis kapisindan gecen oyuncu **girisin yanina degil**, uzaktaki bir **ormana**
birakilir ve ornek kaydindan dusurulur. Ayni ornege geri donemez; tekrar girmek
yeni bir ornek acmak demektir. Cikisin girise acilmasi, dungeon'un tek yonlu olma
kuralini anlamsizlastirirdi.

### 22.7 Cokme
Cekirdek (mor yun ile isaretlenen blok) kirildiginda:

```
0:00   Cekirdek kirildi -> "Mahzen cokuyor, 10 dakikan var"
       her saniye: action bar geri sayimi + moloz particle efekti
5:00 / 3:00 / 1:00 / 30 / 10 / 5 / 3 / 2 / 1   -> baslik + ses uyarisi
10:00  Sure doldu -> icerde kalan HERKES olur
       -> 2 saniye sonra dunya ve icindeki her sey SILINIR
       -> esyalar dunya ile birlikte gider, geri alinamaz
```

Sure `dungeon.collapse-seconds` ile ayarlanir (varsayilan 600 = 10 dk; 300 = 5 dk).
Moloz efekti **particle**tir, gercek blok dusurmez: dunya zaten silinecek, blok
fizigi calistirmak yalnizca sunucuyu yorar.

Cekirdek disindaki bloklar kirilamaz — dungeon bir yapi, insaat alani degil;
duvar kirip kestirmeden gitmek tum yerlesim mantigini bozar.

### 22.8 Guvenlik aglari
- **Bos ornek:** icinde kimse kalmayan ornek 2 dakika sonra kendiliginden silinir
  (baglantisi kopan oyuncu geri gelebilsin diye hemen degil).
- **Artik dunyalar:** cokus sirasinda sunucu kapanirsa klasor diskte kalir; her
  acilista `dungeon_` ile baslayan yuklu olmayan dunyalar suprulur.
- **Silinmis dunyada dogma:** dungeon dunyasinda olen oyuncu daima disariya dogar.

### 22.9 Ozellik anahtarlari
```yaml
dungeon:
  enabled: true          # kapaliysa komut ve girisler yok olur
  collapse: true         # kapaliysa cekirdek kirmak cokme baslatmaz
  one-way-exit: true     # cikisin tek yonlulugu
```

## 23. Diyalog kutusu (MMORPG stili)

Referans: **PROJECT Spellforged** demosundaki acilis diyalogu. Videonun goruntusunu
inceleyemedigim icin bu tur MMORPG sunucularinin standardina gore kuruldu; sapma
varsa olculer ve yerlesim `DialogGlyphs` icinden tek noktadan degistirilebilir.

### 23.1 Gorunum
```
        ┌──────────────────────────────────────────┐
        │  ┌────┐   Yasli Koylu                    │
        │  │port│   Sonunda birileri geldi... Sira,│
        │  │re  │   bu topraklar seni bekliyordu.  │
        │  └────┘                                   │
        │           ▶ Mahzen nerede?                │
        │             Bana ne verirsin?             │
        │             Ilgilenmiyorum.               │
        └──────────────────────────────────────────┘
```

### 23.2 Neden title/subtitle uzerinden ciziliyor
Tasiyici secenekleri ve neden elendikleri:

| Tasiyici | Sorun |
|---|---|
| Action bar | Tek satir; cok satirli kutu sigmaz |
| Boss bar | Ust kenara sabit, portre alani yok |
| Chat | Kaydirilir, kalicilik yok, oyuncunun sohbetini bogar |
| **Title/subtitle** | Ekranin ortasinda, **cok satirli**, her tick yenilenebilir |

Kutu, kalis suresi kisa verilip **her adimda yeniden gonderiliyor**: boylece metin
akarken titremeden yenilenir ve diyalog bitince kendiliginden kaybolur.

### 23.3 Hizalama: negatif bosluk
Kutu, portre, imlec ve metin ayri font karakterleri olarak ust uste bindirilir.
HUD ile ayni `SpaceEncoder` kullanilir (bu yuzden `util` paketine tasindi).
Her parca cizilir, genisligi kadar geri gelinir, sonraki parca ayni piksel uzerine biner.

### 23.4 Metin sarmalama piksel bazli
Vanilla fontta karakterler esit genislikte **degildir**: `i` 2 piksel, `W` 6 piksel.
Karakter sayarak sarmalamak dar harflerle dolu satirlari erken, genis harflerle dolu
satirlari gec keser ve metin kutudan tasar. `TextMeasure` gercek piksel genisligini
olcer. Satirlar diyalog **baslarken bir kez** sarmalanir; her tick yeniden olcmek
akan metinde satir sonlarinin oynamasina yol acardi.

Kirpma da gorunur karakter uzerinden yapilir: MiniMessage etiketlerini sayarak
kirpmak, akan metnin ortasinda etiketi yarida keser ve ekranda ham `<gr` gorunur.

### 23.5 Girdi: chat ve envanter YOK
| Tus | Islev |
|---|---|
| **Fare tekerlegi** | Secenekler arasi gezinme (hotbar kaydirma yakalanir ve iptal edilir) |
| **SHIFT** | Akan metni atla · tamamlanmissa onayla / devam et |
| **Sag tik** | SHIFT ile ayni (fareyle oynayan icin) |

Tekerlek secildi cunku her istemcide var, ekstra tus ogrenmeyi gerektirmiyor ve
chat acmadan calisiyor. Diyalog sirasinda envanter acilmaz, esya dusurulmez ve el
degistirilmez — akis kesilmesin diye.

Hotbar daireseldir: 8'den 0'a gecis "ileri", 0'dan 8'e gecis "geri" demektir.
Duz cikarma bunu ters okur ve secim ters yone atlar; `slotDelta` bunu duzeltir.

### 23.6 Yazma sesi
Her karakterde ses calmak kulakta tirmalayan bir gurultuye donusur; ses **6 tick'te
bir** calinir. Secim degistirmede ayri, onaylamada ayri ses vardir.

### 23.7 Geri dusme
`dialog.box` ozelligi kapaliysa ya da resource pack yuklenmemisse sistem **CHAT**
moduna duser: metin sohbete yazilir, secenekler tiklanabilir satir olur. Diyalog
icerigi ayni kalir, yalnizca tasiyici degisir — pack olmayan bir sunucuda da calisir.

### 23.8 Gerekli texture'lar
`contents/aethel/textures/gui/` altina: `dialog_frame.png` (320×80),
`dialog_frame_top.png`, `dialog_portrait_slot.png` (48×48), `dialog_cursor.png` (8×8),
`dialog_continue.png` (8×8). Portreler `contents/aethel/textures/portrait/` altina,
48×48. Tanimlar `contents/aethel/fonts/dialog.yml` icinde; pack uretimi bunlari
U+E200'den itibaren karaktere baglar.

## 24. Dil: her oyuncu kendi diliyle

Dil **oyuncu bazlidir**, sunucu bazli degil. Turk oyuncuya Turkce, Japon oyuncuya
Japonca, Rus oyuncuya Rusca gider — ayni anda, ayni sunucuda.

### 24.1 Eslesme zinciri
```
oyuncunun istemci dili (or. pt_br)
  -> lang/pt_br.yml var mi?          evet -> kullan
  -> lang/pt.yml var mi?             evet -> kullan
  -> lang/pt_* baska varyant?        evet -> kullan  (pt_pt, ayni dilin varyanti)
  -> lang/<default>.yml                    -> kullan
```
Bolgesel varyanti once denemek onemli: `pt_br` ile `pt_pt`, `zh_cn` ile `zh_tw`
arasindaki fark oyuncular icin belirgindir.

### 24.2 Anahtar bazli geri dusme
Bir dil dosyasinin **tam olmasi gerekmez**. Eksik anahtar varsayilan dile duser ve
bir kez uyarilir. Bu sayede:
- Yeni bir metin eklendiginde 20 dosyayi ayni anda guncellemek zorunlu degil.
- Sunucu sahibi tek bir anahtari cevirip birakabilir.
- Ceviri eksikligi mesaji **kaybettirmez**, yalnizca cevrilmemis gosterir.

### 24.3 Dil listesi yok
`lang/` klasorundeki her `.yml` otomatik yuklenir. Yeni dil eklemek icin dosya
birakmak yeterli; config'te liste tutulmuyor. Dosya adi Minecraft dil kodudur.

Kutuda gelen diller: `tr en de fr es it pt_br nl pl ru uk ja ko zh_cn ar id`
(tr ve en tam; digerleri oyuncunun en sik gordugu ~60 anahtar, gerisi geri duser).

`/core reload` dilleri de tazeler ve ayarlari korur.

## 25. Tab ve Scoreboard modulu

Her oyuncunun **kendi** scoreboard'u vardir: satirlar kisiye ozel placeholder
tasiyabilir ve metin oyuncunun diline gore degisir.

**Satirlar takim on eki olarak yaziliyor.** Vanilla'da skor girdisinin kendisi
benzersiz olmak zorundadir — ayni metin iki satirda gorunemez ve uzunluk sinirlidir.
Girdi olarak gorunmez renk kodu kullanip metni takim on ekine koymak bu iki
sinirlamayi da kaldirir.

```yaml
boards:
  varsayilan:
    priority: 0
    condition: ""                    # izin adi; bos = herkese
    title: "<gradient:#f0c040:#e08020><bold>AETHEL</bold></gradient>"
    lines:
      - "<gray>Seviye</gray> <white>%aethel_rpg_level%</white>"
      - "<gray>Altin</gray> <gold>%aethel_economy_balance%</gold>"
    tab-header: ["", "<bold>AETHEL</bold>", ""]
    tab-footer: ["", "<gray>TPS:</gray> <white>%aethel_server_tps%</white>", ""]
```

Tab listesindeki adin onune rutbe on eki `PermissionService`'ten gelir.
Ozellikler: `scoreboard.sidebar`, `scoreboard.tab`, `scoreboard.tab-prefix`.

Modul kapatilirken oyuncular ana tabloya dondurulur; aksi halde ekranda
guncellenmeyen olu bir tablo asili kalir.

## 26. MOTD modulu

`motd.yml` icinde birden fazla MOTD tanimlanir, her pingde rastgele secilir.

```yaml
motds:
  varsayilan:
    line1: "<gradient:#f0c040:#e08020><bold>AETHEL</bold></gradient> <white>Fantasy MMORPG</white>"
    line2: "<gray>Yeni sezon basladi!</gray>"
    hover: ["<gold>AETHEL</gold>", "<gray>Dungeon · Meslek · Yetenek agaci</gray>"]
  bakim:
    line1: "<red><bold>BAKIM</bold></red>"
    player-count: "<red>Bakimda</red>"
```

Ping olayi ana thread disinda gelebilir; bu yuzden burada **hicbir agir is
yapilmaz**, yalnizca hazir metinler kullanilir. Placeholder cozumu oyuncusuz
calisir — ping atan taraf bir oyuncu degildir, kisiye ozel veri yoktur.

Fare ile beklendiginde gorunen liste, gercek oyuncu adlarini **temizler**: bu hem
ozel metin gostermeyi saglar hem de cevrimici oyuncu adlarinin disariya sizmasini
onler.

Ozellikler: `motd.enabled`, `motd.hover`, `motd.fake-count`. Komut: `/motd yenile`.
