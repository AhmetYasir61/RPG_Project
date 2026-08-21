package net.aethel.core.modules.hud;

import net.aethel.core.api.PlaceholderService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kendi HUD motorumuz (BetterHUD karsiligi). Katmanlar negatif bosluk fontu ile
 * ust uste bindirilir ve tek action bar satiri olarak gonderilir; entity kullanilmaz.
 */
@ModuleInfo(id = "hud", name = "HUD", depends = {"content", "placeholder"})
public final class HudModule implements Module {

    /** HUD guncelleme sikligi; 4 tick akici gorunur ve paket trafigi dusuk kalir. */
    private static final long UPDATE_TICKS = 4L;

    private final List<HudLayout> layouts = new ArrayList<>();
    private final Map<UUID, String> activeLayout = new ConcurrentHashMap<>();
    private final MiniMessage mini = MiniMessage.miniMessage();
    private ConfigFile config;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.config = ctx.config().open("hud.yml", 1, null, ConfigMigration.NONE);

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("hud.enabled", true, "Ekran ustu arayuz");
        features.declare("hud.bars", true, "Can ve mana cubuklari");
        features.declare("hud.target-info", true, "Hedef bilgisi");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        loadLayouts();
        // Kapaliyken gorev hic kurulmaz: her 4 tick'te bos donmek yerine hic donmez.
        if (ctx.feature("hud.enabled")) {
            ctx.scheduler().repeating("hud", UPDATE_TICKS, UPDATE_TICKS, this::tick);
        }
    }

    @Override
    public void onDisable(CoreContext ctx) {
        layouts.clear();
        activeLayout.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        loadLayouts();
    }

    private void loadLayouts() {
        layouts.clear();
        ConfigurationSection root = config.yaml().getConfigurationSection("layouts");
        if (root == null) {
            writeExample();
            root = config.yaml().getConfigurationSection("layouts");
            if (root == null) return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;

            List<HudLayout.Layer> layers = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("layers")) {
                layers.add(new HudLayout.Layer(
                        net.aethel.core.util.Yamls.string(raw, "text", ""),
                        net.aethel.core.util.Yamls.integer(raw, "offset-x", 0),
                        raw.get("show-when") == null ? null : String.valueOf(raw.get("show-when"))));
            }
            layouts.add(new HudLayout(id,
                    section.getString("condition"),
                    section.getInt("priority", 0),
                    section.getInt("update-ticks", 4),
                    layers));
        }
        layouts.sort(Comparator.comparingInt(HudLayout::priority).reversed());
        ctx.logger().info("HUD duzeni: " + layouts.size());
    }

    /** Ilk acilista ornek bir HUD yazilir; sistem bos gelmesin. */
    private void writeExample() {
        var yaml = config.yaml();
        yaml.set("layouts.varsayilan.priority", 0);
        yaml.set("layouts.varsayilan.update-ticks", 4);
        List<Map<String, Object>> layers = new ArrayList<>();
        layers.add(Map.of("text", "<white>%aethel_player_health%</white>", "offset-x", 0));
        layers.add(Map.of("text", "<gray>|</gray>", "offset-x", 6));
        layers.add(Map.of("text", "<aqua>%aethel_server_online%</aqua>", "offset-x", 6));
        yaml.set("layouts.varsayilan.layers", layers);
        config.save();
    }

    /**
     * Her oyuncu icin en yuksek oncelikli uygun duzen secilir ve action bar gonderilir.
     * Oyuncu sayisi 100 civari oldugu icin dogrudan dongu yeterli; is basi maliyet
     * yalnizca metin birlestirme ve bir paket.
     */
    private void tick() {
        if (layouts.isEmpty()) return;
        var placeholders = ctx.services().optional(PlaceholderService.class);

        for (Player player : ctx.plugin().getServer().getOnlinePlayers()) {
            HudLayout layout = select(player);
            if (layout == null) continue;
            activeLayout.put(player.getUniqueId(), layout.id());

            StringBuilder line = new StringBuilder();
            for (HudLayout.Layer layer : layout.layers()) {
                line.append(SpaceEncoder.shift(layer.offsetX()));
                String text = placeholders
                        .map(service -> service.apply(player, layer.text()))
                        .orElse(layer.text());
                line.append(text);
            }
            player.sendActionBar(mini.deserialize(line.toString()));
        }
    }

    /** Kosul su an icin izin bazlidir; ileride bolge/durum kosullari eklenecek. */
    private HudLayout select(Player player) {
        for (HudLayout layout : layouts) {
            if (layout.condition() == null || layout.condition().isBlank()) return layout;
            if (player.hasPermission(layout.condition())) return layout;
        }
        return null;
    }

    /** Panelin canli onizleme yapabilmesi icin. */
    public String activeLayoutOf(Player player) {
        return activeLayout.getOrDefault(player.getUniqueId(), "-");
    }
}
