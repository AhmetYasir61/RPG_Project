package net.aethel.core.bootstrap;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.aethel.core.command.CommandRegistrar;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.config.ConfigService;
import net.aethel.core.event.EventBus;
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

        this.context = new CoreContext(this, getLogger(), services, events, configService,
                lang, commands, scheduler, database, schema);

        this.modules = new ModuleManager(context);
        ConfigFile moduleConfig = configService.open("modules.yml", 1, null, ConfigMigration.NONE);
        modules.register(moduleConfig.yaml().getConfigurationSection("modules"),
                moduleConfig.yaml().getBoolean("fail-soft", true),
                ModuleCatalog.all(packets));
        modules.loadAll();

        commands.register("core", new CoreCommand(context, modules));
        registerBrigadier(commands);
    }

    @Override
    public void onEnable() {
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

    /** Jar icindeki varsayilan config, dil ve icerik dosyalarini veri klasorune acar. */
    private void saveDefaultResources() {
        saveResource("config.yml", false);
        saveResource("modules.yml", false);
        saveResource("lang/tr.yml", false);
        saveResource("lang/en.yml", false);
        // Ornek icerik: yalnizca ilk acilista yazilir, sonra kullanicinin malidir.
        saveResource("contents/aethel/items/silahlar.yml", false);
        saveResource("contents/aethel/fonts/hud.yml", false);
        saveResource("contents/aethel/menus/ana_menu.yml", false);
        saveResource("contents/aethel/skills/ornek.yml", false);
        saveResource("contents/aethel/mobs/ornek.yml", false);
    }

    public CoreContext context() {
        return context;
    }
}
