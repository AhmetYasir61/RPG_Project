package net.aethel.core.modules.motd;

import net.aethel.core.api.PlaceholderService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sunucu listesi gorunumu (MOTD) modulu. Ping olayi ana thread disinda gelebilir,
 * bu yuzden burada yalnizca hazir metinler kullanilir; hicbir agir is yapilmaz.
 */
@ModuleInfo(id = "motd", name = "MOTD", softDepends = {"placeholder"})
public final class MotdModule implements Module, Listener {

    private final MotdSettings settings = new MotdSettings();
    private final List<MotdEntry> entries = new ArrayList<>();
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Random random = new Random();
    private ConfigFile config;
    private CoreContext ctx;

    /** Iki satirlik bir MOTD ve listede gosterilecek oyuncu ornegi. */
    private record MotdEntry(String line1, String line2, List<String> hover, String playerCount) {}

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.config().open("modules/motd.yml", 1, settings, ConfigMigration.NONE);
        this.config = ctx.config().open("motd.yml", 1, null, ConfigMigration.NONE);
        ctx.commands().register("motd", new MotdCommand(ctx, this));

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("motd.enabled", true, "Ozel sunucu listesi gorunumu");
        features.declare("motd.hover", true, "Listede fare ile beklendiginde cikan metin");
        features.declare("motd.fake-count", false, "Sahte oyuncu sayisi gosterimi");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        reload();
        ctx.listener("motd.enabled", this);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        entries.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        reload();
    }

    /** motd.yml okunur; tanim yoksa ornek yazilir. */
    public int reload() {
        entries.clear();
        ConfigurationSection root = config.yaml().getConfigurationSection("motds");
        if (root == null) {
            writeExample();
            root = config.yaml().getConfigurationSection("motds");
            if (root == null) return 0;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            entries.add(new MotdEntry(
                    section.getString("line1", ""),
                    section.getString("line2", ""),
                    section.getStringList("hover"),
                    section.getString("player-count", "")));
        }
        ctx.logger().info("MOTD tanimi: " + entries.size());
        return entries.size();
    }

    private void writeExample() {
        var yaml = config.yaml();
        yaml.set("motds.varsayilan.line1",
                "<gradient:#f0c040:#e08020><bold>AETHEL</bold></gradient> <dark_gray>»</dark_gray> <white>Fantasy MMORPG</white>");
        yaml.set("motds.varsayilan.line2",
                "<gray>Yeni sezon basladi!</gray> <dark_gray>|</dark_gray> <aqua>1.21.11</aqua>");
        yaml.set("motds.varsayilan.hover", List.of(
                "<gold>AETHEL</gold>",
                "<gray>Dungeon · Meslek · Yetenek agaci</gray>",
                "",
                "<yellow>Hos geldin!</yellow>"));
        yaml.set("motds.varsayilan.player-count", "");

        yaml.set("motds.bakim.line1",
                "<red><bold>BAKIM</bold></red> <dark_gray>»</dark_gray> <gray>Kisa sure sonra</gray>");
        yaml.set("motds.bakim.line2", "<dark_gray>Sabrin icin tesekkurler</dark_gray>");
        yaml.set("motds.bakim.player-count", "<red>Bakimda</red>");
        config.save();
    }

    /**
     * Ping olayi. MiniMessage cozumu burada yapiliyor ama placeholder cozumu
     * OYUNCUSUZ calisir: ping atan taraf bir oyuncu degildir, kisiye ozel veri yoktur.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPing(ServerListPingEvent event) {
        if (entries.isEmpty()) return;
        MotdEntry entry = settings.random
                ? entries.get(random.nextInt(entries.size()))
                : entries.get(0);

        event.motd(render(entry.line1()).append(Component.newline()).append(render(entry.line2())));

        if (settings.fakeMaxPlayers > 0 && ctx.feature("motd.fake-count")) {
            event.setMaxPlayers(settings.fakeMaxPlayers);
        }
        if (settings.hoverEnabled && ctx.feature("motd.hover")) {
            applyHover(event, entry);
        }
    }

    /**
     * Listede fare ile beklendiginde gorunen satirlar. Bukkit bunu "oyuncu ornegi"
     * uzerinden sundugu icin gercek oyuncu listesi temizlenip yerine metin konur;
     * bu ayni zamanda cevrimici oyuncu adlarinin disariya sizmasini da onler.
     */
    private void applyHover(ServerListPingEvent event, MotdEntry entry) {
        if (entry.hover().isEmpty()) return;
        var iterator = event.iterator();
        while (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private Component render(String text) {
        String resolved = ctx.services().optional(PlaceholderService.class)
                .map(service -> service.apply(null, text))
                .orElse(text);
        return mini.deserialize(resolved);
    }

    /** Sunucu ikonu dosyasinin yolu; panel bunu gostermek icin kullanir. */
    public File iconFile() {
        return new File(ctx.config().dataFolder(), settings.iconFile);
    }

    int count() {
        return entries.size();
    }
}
