package net.aethel.core.modules.dungeon;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.i18n.LangService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dunyadaki dungeon girisleri. Giris bir yapi gibi durur; uzerine basan oyuncu
 * yeni bir ornege ve dolayisiyla baska bir boyuta gecer.
 */
public final class DungeonEntrance implements Listener {

    /** Girisin merkez blogu; oyuncu bunun uzerine basinca gecis olur. */
    private static final Material PORTAL_BLOCK = Material.SCULK_SHRIEKER;

    /** Ayni oyuncunun tekrar tekrar tetiklemesini onleyen bekleme (ms). */
    private static final long TRIGGER_COOLDOWN_MILLIS = 3000L;

    private final CoreContext ctx;
    private final DungeonModule module;
    private final ConfigFile config;
    private final Map<String, Location> entrances = new LinkedHashMap<>();
    private final Map<UUID, Long> lastTrigger = new ConcurrentHashMap<>();
    private final NamespacedKey dungeonKey;

    public DungeonEntrance(CoreContext ctx, DungeonModule module, ConfigFile config) {
        this.ctx = ctx;
        this.module = module;
        this.config = config;
        this.dungeonKey = new NamespacedKey(ctx.plugin(), "dungeon_entrance");
        load();
    }

    private void load() {
        entrances.clear();
        ConfigurationSection root = config.yaml().getConfigurationSection("entrances");
        if (root == null) return;
        for (String dungeonId : root.getKeys(false)) {
            Location location = root.getLocation(dungeonId);
            if (location != null) entrances.put(dungeonId, location);
        }
        ctx.logger().info("Dungeon girisi: " + entrances.size());
    }

    /**
     * Girisi dunyaya yerlestirir. Yapi kucuk tutuluyor: asil dungeon baska bir
     * boyutta oldugu icin girisin gorevi yalnizca gorunur bir esik olmak.
     */
    public void place(String dungeonId, Location center) {
        Block block = center.getBlock();
        block.setType(PORTAL_BLOCK);

        // Cerceve: girisin tesadufi bir blok olmadigini gosteren gorsel esik.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                block.getRelative(x, 0, z).setType(Material.POLISHED_DEEPSLATE);
                block.getRelative(x, -1, z).setType(Material.DEEPSLATE_BRICKS);
            }
        }
        block.getRelative(0, 2, 0).setType(Material.SOUL_LANTERN);

        entrances.put(dungeonId, center);
        config.yaml().set("entrances." + dungeonId, center);
        config.save();
    }

    public void remove(String dungeonId) {
        Location location = entrances.remove(dungeonId);
        if (location != null) location.getBlock().setType(Material.AIR);
        config.yaml().set("entrances." + dungeonId, null);
        config.save();
    }

    public Map<String, Location> entrances() {
        return Map.copyOf(entrances);
    }

    /**
     * Girise basildiginda ornek acilir. Hareket olayi cok sik tetiklendigi icin
     * once ucuz bir blok kontrolu yapilir; pahali arama yalnizca dogru blok
     * uzerinde calisir.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        var to = event.getTo();
        if (event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockY() == to.getBlockY()
                && event.getFrom().getBlockZ() == to.getBlockZ()) return;

        Block below = to.clone().subtract(0, 1, 0).getBlock();
        if (below.getType() != PORTAL_BLOCK) return;

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastTrigger.get(player.getUniqueId());
        if (last != null && now - last < TRIGGER_COOLDOWN_MILLIS) return;
        lastTrigger.put(player.getUniqueId(), now);

        entrances.forEach((dungeonId, location) -> {
            if (!location.getWorld().equals(below.getWorld())) return;
            if (location.getBlock().getLocation().distanceSquared(below.getLocation()) > 1) return;

            ctx.lang().send(player, "dungeon.entering",
                    LangService.of("dungeon", dungeonId));
            module.enter(player, dungeonId);
        });
    }

    /** Girisin PDC anahtari; ileride yapi tarayicilari icin. */
    public NamespacedKey key() {
        return dungeonKey;
    }
}
