package net.aethel.core.bootstrap;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.aethel.core.command.CommandRegistrar;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.config.ConfigService;
import net.aethel.core.event.EventBus;
import net.aethel.core.feature.FeatureRegistry;
import net.aethel.core.i18n.LangService;
import net.aethel.core.module.ModuleManager;
import net.aethel.core.packet.PacketBridge;
import net.aethel.core.service.ServiceRegistry;
import net.aethel.core.storage.Database;
import net.aethel.core.storage.DatabaseSettings;
import net.aethel.core.storage.SchemaManager;
import net.aethel.core.util.CoreScheduler;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Tek giris noktasi. Tum altyapiyi onLoad'da kurar, modulleri onEnable'da acar;
 * hicbir modul kendi basina bir Bukkit plugin degildir.
 */
@SuppressWarnings("UnstableApiUsage")
public final class CorePlugin extends JavaPlugin {

    private static final int CONFIG_SCHEMA = 1;

    private CoreContext context;
    private ModuleManager modules;
    private CoreScheduler scheduler;
    private Database database;
    private PacketBridge packets;
    private final CoreSettings settings = new CoreSettings();
    private boolean bootFailed;

    @Override
    public void onLoad() {
        saveDefaultResources();

        ConfigService configService = new ConfigService(getDataFolder());
        ConfigFile main = configService.open("config.yml", CONFIG_SCHEMA, settings, ConfigMigration.NONE);

        // PacketEvents onEnable'dan ONCE yuklenmeli; aksi halde sunucu ag katmani
        // kurulduktan sonra enjekte etmeye calisir ve ilk giren oyunculari kacirir.
        this.packets = new PacketBridge(getLogger());
        packets.load(this);

        this.scheduler = new CoreScheduler(this);
        ServiceRegistry services = new ServiceRegistry();
        EventBus events = new EventBus(getLogger(), scheduler.ioExecutor());

        LangService lang = new LangService(getDataFolder(), getLogger());
        lang.load(settings.defaultLanguage, settings.languageCodes());

        DatabaseSettings dbSettings = new DatabaseSettings();
        net.aethel.core.config.ConfigMapper.writeDefaults(dbSettings, main.yaml());
        main.save();
        net.aethel.core.config.ConfigMapper.apply(dbSettings, main.yaml());

        this.database = new Database(dbSettings, getDataFolder(), getLogger(), scheduler.ioExecutor());
        SchemaManager schema = new SchemaManager(database, getLogger());
        CommandRegistrar commands = new CommandRegistrar(lang, getLogger());

        // Ozellik kaydi cekirdegin parcasi: her modul acilista kendi anahtarlarini
        // bildirir ve features.yml kendiliginden dolar.
        ConfigFile featureConfig = configService.open("features.yml", 1, null, ConfigMigration.NONE);
        FeatureRegistry features = new FeatureRegistry(this, featureConfig, events, getLogger());
        services.register(net.aethel.core.api.FeatureService.class, features, "core");
        commands.features(features);

        this.context = new CoreContext(this, getLogger(), services, events, configService,
                lang, commands, scheduler, database, schema, features);

        this.modules = new ModuleManager(context);
        ConfigFile moduleConfig = configService.open("modules.yml", 1, null, ConfigMigration.NONE);

        // Modul grafigi kurulamazsa (orn. zorunlu bagimlilik cevrimi) sessizce devam
        // etmek en kotu sonuctur: sunucu acilir ama hicbir sey calismaz. Acikca
        // isaretleyip onEnable'da net bir mesajla duruyoruz.
        try {
            modules.register(moduleConfig.yaml().getConfigurationSection("modules"),
                    moduleConfig.yaml().getBoolean("fail-soft", true),
                    ModuleCatalog.all(packets));
            modules.loadAll();
        } catch (RuntimeException error) {
            this.bootFailed = true;
            getLogger().severe("Modul grafigi kurulamadi: " + error.getMessage());
            getLogger().severe("Cekirdek devre disi. Duzeltip sunucuyu yeniden baslat.");
        }

        commands.register("core", new CoreCommand(context, modules));
        registerBrigadier(commands);
    }

    @Override
    public void onEnable() {
        if (bootFailed) {
            getLogger().severe("Cekirdek baslatilmadi (modul grafigi hatasi). "
                    + "Hicbir modul acilmayacak.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        packets.enable();
        modules.enableAll();
        context.events().post(new CoreReadyEvent(context));
        getLogger().info("Cekirdek hazir.");
    }

    @Override
    public void onDisable() {
        if (modules != null) modules.disableAll();
        if (packets != null) packets.disable();
        if (scheduler != null) scheduler.shutdown();
        if (database != null) database.close();
        getLogger().info("Cekirdek kapatildi.");
    }

    /**
     * Brigadier kayitlari yalnizca COMMANDS yasam dongusu olayinda yapilabilir; bu yuzden
     * tum modul komutlari onLoad'da kuyruga alinir ve burada tek seferde kaydedilir.
     */
    private void registerBrigadier(CommandRegistrar commands) {
        LifecycleEventManager<org.bukkit.plugin.Plugin> manager = getLifecycleManager();
        manager.registerEventHandler(LifecycleEvents.COMMANDS,
                event -> commands.flush(event.registrar()));
    }

    /**
     * Jar icindeki varsayilan dosyalari veri klasorune acar. Dosyalar ELLE
     * LISTELENMEZ: jar taranir ve config/lang/contents altindaki her sey cikarilir.
     *
     * Elle liste tutmak sessiz bir hata kaynagiydi — yeni bir ornek icerik dosyasi
     * eklenip listeye yazilmayi unutuldugunda dosya diske hic yazilmiyor, modul de
     * "0 tanim yuklendi" deyip gectigi icin sorun fark edilmiyordu.
     */
    private void saveDefaultResources() {
        saveResource("config.yml", false);
        saveResource("modules.yml", false);
        saveResource("features.yml", false);
        extractBundled("lang/");
        extractBundled("contents/");
    }

    /** Jar icindeki verilen on ekli tum dosyalari, yoksa, veri klasorune kopyalar. */
    private void extractBundled(String prefix) {
        java.io.File source = getFile();
        int extracted = 0;
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(source)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                java.util.jar.JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(prefix)) continue;
                if (!entry.getName().endsWith(".yml") && !entry.getName().endsWith(".json")) continue;

                java.io.File target = new java.io.File(getDataFolder(), entry.getName());
                if (target.exists()) continue;   // kullanicinin dosyasi asla ezilmez
                target.getParentFile().mkdirs();
                try (var input = jar.getInputStream(entry)) {
                    java.nio.file.Files.copy(input, target.toPath());
                    extracted++;
                }
            }
        } catch (java.io.IOException e) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Varsayilan dosyalar cikarilamadi: " + prefix, e);
            return;
        }
        if (extracted > 0) getLogger().info("Varsayilan dosya yazildi (" + prefix + "): " + extracted);
    }

    public CoreContext context() {
        return context;
    }
}
