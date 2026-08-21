# FAZ 1 — Cekirdek

Bu fazda uretilen her sinif derlenir ve calisir; hicbir yerde TODO yoktur.

## Ne yazildi

| Paket | Sinif | Isi |
|---|---|---|
| `service` | `ServiceRegistry` | Sahip takipli servis kaydi, hot-disable'da toplu kaldirma |
| `module` | `Module`, `@ModuleInfo`, `ModuleState`, `ModuleContainer`, `DependencyGraph`, `ModuleManager` | Yasam dongusu, topolojik siralama, fail-soft |
| `event` | `CoreEvent`, `@Subscribe`, `EventBus` | MethodHandle tabanli ic event sistemi, sync/async ayrimi |
| `config` | `@ConfigValue`, `ConfigMapper`, `ConfigFile`, `ConfigMigration`, `ConfigService` | Annotation mapping, hot-reload, sema surumu + yedekli migration |
| `i18n` | `LangService` | MiniMessage, istemci diline gore otomatik secim |
| `command` | `@Command`, `@Arg`, `ArgumentResolvers`, `CommandRegistrar`, `CommandInvoker`, `CommandException` | Brigadier cerceve: otomatik tab-complete, yetki, /help |
| `storage` | `Database`, `DatabaseSettings`, `SqlDialect`, `SchemaManager`, `Repository`, `DataAccessException` | HikariCP, MySQL/SQLite, sanal thread I/O, sema migration |
| `util` | `CoreScheduler`, `BudgetedTask` | Sahip takipli zamanlayici, tick butceli is kuyrugu |
| `bootstrap` | `CorePlugin`, `CoreContext`, `CoreSettings`, `CoreCommand`, `CoreReadyEvent`, `ModuleCatalog` | Giris noktasi ve montaj |
| `api` | `AethelApi`, `PlaceholderService`, `PlaceholderResolver` | Addon'lara acik kararli yuzey |
| `nms` | `VersionAdapter`, `VersionAdapters`, `v1_21_11.Adapter` | Surum soyutlamasi (FAZ 2'de genisler) |

## Uc kritik karar

**1. Brigadier kayitlari neden onLoad'da kuyruklaniyor?**
Paper'da komut kaydi yalnizca `LifecycleEvents.COMMANDS` aninda yapilabilir ve bu an
`onEnable`'dan once gecer. Bu yuzden moduller `onLoad` icinde komut nesnelerini
`CommandRegistrar`'a birakir, cekirdek hepsini tek seferde `flush()` eder.
Alternatif — her hot-enable'da yeni komut kaydetmek — API'de mumkun degil; bunun
bedeli, calisma zamaninda acilan bir modulun komutlarinin sunucu yeniden baslayana
kadar agaca girmemesidir. Buna karsilik komut agacinin tutarliligi garanti olur.

**2. Neden MethodHandle, neden refleksiyon degil?**
`EventBus` ve `CommandInvoker` kayit aninda bir kez `unreflect` yapip `bindTo` ile
hedefe baglar. Sonraki her cagri JIT tarafindan dogrudan cagri gibi optimize edilir;
`Method.invoke`'un cagri basi kontrol maliyeti ortadan kalkar. Savas sirasinda saniyede
binlerce event yayinlandiginda bu fark olculebilir hale gelir.

**3. Neden SQLite havuzu 1?**
SQLite tek yazar destekler. Havuzu buyutmek `SQLITE_BUSY` kilit hatalarina yol acar;
1'de tutmak yazmalari sirali hale getirir. Zaten SQLite yalnizca gelistirme ve kucuk
sunucu icin fallback'tir — 100 oyunculu uretim icin MySQL onerilir.

## Kullanim ornegi (FAZ 5'te gercek modul boyle yazilacak)

```java
@ModuleInfo(id = "economy", name = "Ekonomi", depends = {"profile"})
public final class EconomyModule implements Module {

    private final EconomySettings settings = new EconomySettings();

    @Override public void onLoad(CoreContext ctx) {
        ctx.config().open("modules/economy.yml", 1, settings, ConfigMigration.NONE);
        ctx.services().register(EconomyService.class, new EconomyServiceImpl(ctx, settings), "economy");
        ctx.commands().register("economy", new BalanceCommand(ctx));
    }

    @Override public void onEnable(CoreContext ctx) {
        ctx.schema().migrate("economy", EconomySchema.MIGRATIONS);
        ctx.events().register("economy", new EconomyListeners(ctx));
        ctx.scheduler().repeating("economy", 20L * 60, 20L * 60, this::flushDirty);
    }

    @Override public void onDisable(CoreContext ctx) { flushDirty(); }
}
```

## Yerel derleme
```
./gradlew build          # AethelCore.jar -> build/libs/
./gradlew runServer      # 1.21.11 test sunucusu ayaga kalkar
```
