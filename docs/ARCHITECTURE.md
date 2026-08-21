# AethelCore — FAZ 0: Mimari

Paper 1.21.11 / Java 21 / Gradle Kotlin DSL + Shadow. Cikti: `AethelCore.jar`.
Tum isimlendirme `gradle.properties` icindeki `serverName` / `corePackage` /
`contentNamespace` degerlerinden turer.

## 1. Dosya agaci

```
AethelCore/
├─ settings.gradle.kts        rootProject adi serverName'den turer
├─ build.gradle.kts           shadow + relocate + runServer
├─ gradle.properties          serverName / corePackage / surumler
├─ nms/
│  ├─ api/                    VersionAdapter sozlesmesi (net.minecraft importu YOK)
│  └─ v1_21_11/               paperweight-userdev, Mojang-mapped implementasyon
├─ docs/ARCHITECTURE.md
└─ src/main/
   ├─ java/net/aethel/core/
   │  ├─ bootstrap/           CorePlugin, CoreBootstrap, LifecyclePhase
   │  ├─ module/              Module, @Module, ModuleManager, ModuleState, DependencyGraph
   │  ├─ service/             ServiceRegistry, @Service, ServiceHandle
   │  ├─ event/               EventBus, CoreEvent, @Subscribe, Dispatch (SYNC/ASYNC)
   │  ├─ config/              @ConfigValue, ConfigMapper, ConfigSchema, Migrator, ConfigWatcher
   │  ├─ i18n/                LangService, MessageKey, MiniMessage render
   │  ├─ command/             @Command/@Arg, CommandRegistrar (Brigadier), ArgumentResolvers
   │  ├─ storage/             Database, HikariProvider, Repository<T,ID>, Dialect, migrations/
   │  ├─ packet/              PacketBridge (PacketEvents facade), PacketSender
   │  ├─ util/                Scheduler (budgeted), ChunkIterator, Cache, Result
   │  ├─ api/                 *** PUBLIC/STABLE addon API — kirilmaz ***
   │  └─ modules/
   │     ├─ profile/ economy/ permissions/ content/ mob/ rpg/
   │     └─ menu/ quest/ region/ chat/ essentials/ hologram/ npc/ placeholder/
   └─ resources/
      ├─ plugin.yml  modules.yml  config.yml
      ├─ lang/tr.yml  lang/en.yml
      ├─ content/{items,blocks,mobs,recipes}/*.yml
      └─ textures/  models/          → resource pack generator girdisi
```

Her modul kendi icinde ayni sablonu tutar:
`XModule.java` (yasam dongusu) · `service/` (arayuz + impl) · `model/` (record'lar) ·
`command/` · `listener/` · `repo/` · `config/`.

## 2. Modul yasam dongusu

```
      SERVER BOOT
          │
   [1] CorePlugin#onLoad
          │  ServiceRegistry olustur · ConfigMapper · NMS VersionAdapter sec
          │  PacketEvents.load()   (onEnable'dan ONCE sart)
          ▼
   [2] CorePlugin#onEnable
          │  Database.connect() (async, virtual thread)  · LangService yukle
          │  ModuleManager.discover()  → modules.yml + @Module taramasi
          │  DependencyGraph.topologicalSort()  → cevrim varsa boot durur
          ▼
   [3] her modul icin sirayla:
          REGISTERED → LOADING  (onLoad: config oku, servis ARAYUZUNU kaydet)
                     → ENABLING (onEnable: listener, komut, repo, task)
                     → ENABLED
          hata → FAILED  (fail-soft: bagimlilari SKIPPED, sunucu ayakta kalir)
          ▼
   [4] POST_ENABLE  → CoreReadyEvent (EventBus) · Vault/PAPI koprulerinin baglanmasi
          ▼
   [5] runtime: /core modules disable <id>
          ENABLED → DISABLING (onDisable: task iptal, listener/komut kaldir,
                    servis deregister, cache flush) → DISABLED
          bagimli modulller once kapatilir (ters topolojik sira)
          ▼
   [6] CorePlugin#onDisable → ters sirada disable → flush → Hikari kapat
```

Kural: `onLoad` icinde **servis arayuzu** kaydedilir, `onEnable` icinde **kullanilir**.
Boylece A modulu B'nin servisini onEnable'da guvenle alir; sira sorunu yasanmaz.

## 3. Modul → servis bagimlilik tablosu

| Modul | Sagladigi servis | Zorunlu bagimlilik | Opsiyonel |
|---|---|---|---|
| Profile | `ProfileService` | Database | — |
| Economy | `EconomyService` | Profile | Vault (provider olarak kayit) |
| Permissions | `PermissionService` | Profile | — |
| Content | `ItemService`, `BlockService`, `PackService` | NMS, Packet | — |
| Mob (MobEngine) | `MobService`, `SkillService` | Content, NMS | RPG |
| RPG/Skills | `StatService`, `LevelService` | Profile | Content, Mob |
| Menu | `MenuService` | Content (font GUI) | Economy, Permissions |
| Quest | `QuestService` | Profile, Menu | Economy, Mob, RPG |
| Region | `RegionService` | Profile | Permissions |
| Chat | `ChatService` | — | Permissions, Placeholder |
| Essentials | `HomeService`, `WarpService`, `TeleportService` | Profile | Economy, Region |
| Hologram | `HologramService` | Packet | Placeholder |
| NPC | `NpcService` | Packet, NMS | Menu, Quest, Hologram |
| Placeholder | `PlaceholderService` | — | PAPI (kopru) |

Cevrim yok: `Mob → RPG` zorunlu degil, RPG tarafi `Optional<MobService>` ile calisir.
`ServiceRegistry.optional(X.class)` bunun icin var.

## 4. Neden bu kararlar

- **Servis arayuzu / impl ayrimi:** modul kapatilinca sadece impl gider, tuketici
  taraf `Optional` gorur ve cokmez. Dogrudan sinif referansi olsaydi hot-disable
  imkansizdi (NoClassDefFoundError).
- **NMS ayri Gradle alt projesi:** paperweight-userdev tek surumu remap eder;
  ileride 1.22 gelince `nms/v1_22` eklenir, cekirdek kod hic degismez. Refleksiyon
  alternatifi hizli yazilir ama her tick maliyet + sessiz kirilma getirir.
- **Shadow relocate:** PacketEvents'i standalone kuran baska bir plugin varsa sinif
  cakismasi olur; relocate bunu tamamen bitirir. Bedeli ~2 MB jar buyumesi.
- **Tek main class:** `plugin.yml` cok main desteklemez; modullerin Bukkit plugin
  olmamasi ayni zamanda ortak classloader, ortak scheduler ve tek reload demek.
