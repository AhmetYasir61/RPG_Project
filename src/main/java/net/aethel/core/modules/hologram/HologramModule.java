package net.aethel.core.modules.hologram;

import net.aethel.core.api.Hologram;
import net.aethel.core.api.HologramService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.aethel.core.module.ModuleUnavailableException;
import net.aethel.core.nms.VersionAdapter;
import net.aethel.core.nms.VersionAdapters;
import net.aethel.core.packet.PacketBridge;
import net.aethel.core.util.BudgetedTask;
import org.bukkit.Location;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paket tabanli hologram modulu. HologramService'i saglar, gorunurlugu oyuncu
 * mesafesine gore butceli bir gorevle gunceller.
 */
@ModuleInfo(id = "hologram", name = "Hologram")
public final class HologramModule implements Module, HologramService {

    /** Bu mesafenin disindaki oyunculara paket gonderilmez. */
    private static final double VIEW_DISTANCE_SQUARED = 48 * 48;

    private final Map<String, PacketHologram> holograms = new ConcurrentHashMap<>();
    private final PacketBridge bridge;
    private VersionAdapter nms;
    private CoreContext ctx;

    public HologramModule(PacketBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        if (!bridge.isAvailable()) {
            throw new ModuleUnavailableException("paket katmani kullanilamiyor");
        }
        this.nms = VersionAdapters.detect(ctx.logger()).orElseThrow(() ->
                new ModuleUnavailableException("bu surum icin NMS adapteri yok"));
        ctx.services().register(HologramService.class, this, "hologram");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        ctx.commands().register("hologram", new HologramCommand(ctx, this));
        // Gorunurluk her tick degil, saniyede bir taranir: hologram konumu sabit oldugu
        // icin daha sik kontrol etmek yalnizca bos is uretir.
        // Gorunurluk taramasi tick butcesine yayilir: 500 hologramli bir sunucuda
        // tek tick'te hepsini gezmek yerine is tick'lere bolunur, TPS'e dokunmaz.
        BudgetedTask<PacketHologram> scan = new BudgetedTask<>(this::updateVisibility, 1.0D);
        ctx.scheduler().repeating("hologram", 20L, 20L, () -> {
            if (scan.pending() == 0) scan.submit(holograms.values());
            scan.run();
        });
    }

    /** Tek bir hologramin izleyici listesini gunceller. */
    private void updateVisibility(PacketHologram hologram) {
        for (var player : ctx.plugin().getServer().getOnlinePlayers()) {
            boolean near = player.getWorld().equals(hologram.location().getWorld())
                    && player.getLocation().distanceSquared(hologram.location()) <= VIEW_DISTANCE_SQUARED;
            if (near) hologram.show(player); else hologram.hide(player);
        }
    }

    @Override
    public void onDisable(CoreContext ctx) {
        holograms.values().forEach(Hologram::remove);
        holograms.clear();
    }

    @Override
    public Hologram create(String id, Location location) {
        PacketHologram hologram = new PacketHologram(id, location, bridge, nms);
        holograms.put(id, hologram);
        return hologram;
    }

    @Override
    public Hologram temporary(Location location, long ticks) {
        String id = "temp-" + java.util.UUID.randomUUID();
        PacketHologram hologram = new PacketHologram(id, location, bridge, nms);
        holograms.put(id, hologram);
        ctx.scheduler().later("hologram", ticks, () -> remove(id));
        return hologram;
    }

    @Override
    public Optional<Hologram> get(String id) {
        return Optional.ofNullable(holograms.get(id));
    }

    @Override
    public Collection<Hologram> all() {
        return java.util.List.copyOf(holograms.values());
    }

    @Override
    public void remove(String id) {
        PacketHologram hologram = holograms.remove(id);
        if (hologram != null) hologram.remove();
    }
}
