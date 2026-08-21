package net.aethel.core.modules.dungeon;

import net.aethel.core.api.LootService;
import net.aethel.core.api.MobService;
import net.aethel.core.api.NpcService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.modules.dungeon.blueprint.RoomTemplate;
import net.aethel.core.modules.dungeon.gen.RoomWriter;
import net.aethel.core.modules.dungeon.instance.ActiveInstance;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.List;
import java.util.Random;

/**
 * Odalara dusen isaretleri gercek icerige cevirir. Dungeon icindeki her sey mevcut
 * modullerimizden gelir: moblar MobService'ten, sandiklar LootService'ten.
 */
final class DungeonPopulator {

    /** Cekirdek blogu; kirilinca cokme baslar. */
    static final Material CORE_BLOCK = Material.CRYING_OBSIDIAN;

    private final CoreContext ctx;
    private final Random random = new Random();

    DungeonPopulator(CoreContext ctx) {
        this.ctx = ctx;
    }

    /**
     * Isaretleri isler. Mob ve sandik tanimlari dungeon tanimindan degil, odanin
     * kendi isaretinden gelir: ayni oda farkli dungeonlarda farkli zorlukta calisir
     * cunku zorluk carpani bolgeden okunur.
     */
    void populate(ActiveInstance instance, List<RoomWriter.PlacedMarker> markers,
                  String mobPool, String lootTable) {
        for (RoomWriter.PlacedMarker marker : markers) {
            switch (marker.type()) {
                case CORE -> placeCore(instance, marker.location());
                case ENTRANCE -> instance.entrance(marker.location());
                case EXIT -> instance.exit(marker.location());
                case SPAWN -> {
                    if (instance.entrance() == null) instance.entrance(marker.location());
                }
                case MOB -> spawnMob(marker, mobPool);
                case LOOT -> placeLoot(marker, lootTable);
                case NPC -> placeNpc(marker);
                case HOLOGRAM -> placeHologram(marker);
            }
        }
    }

    /** Cekirdek: kirilabilir tek blok. Konumu ornekte saklanir ve korunur. */
    private void placeCore(ActiveInstance instance, Location location) {
        location.getBlock().setType(CORE_BLOCK);
        instance.core(location.getBlock().getLocation());
    }

    private void spawnMob(RoomWriter.PlacedMarker marker, String mobPool) {
        String mobId = marker.value().isEmpty() ? mobPool : marker.value();
        if (mobId == null || mobId.isBlank()) return;
        ctx.services().optional(MobService.class)
                .ifPresent(mobs -> mobs.spawn(mobId, marker.location()));
    }

    /**
     * Sandik gercek bir sandik blogu olarak konur ve LootService'e kaydedilir;
     * boylece tehdit bazli nadirlik mekanigi dungeon icinde de aynen calisir.
     */
    private void placeLoot(RoomWriter.PlacedMarker marker, String lootTable) {
        String table = marker.value().isEmpty() ? lootTable : marker.value();
        if (table == null || table.isBlank()) return;

        marker.location().getBlock().setType(Material.CHEST);
        ctx.services().optional(LootService.class).ifPresent(loot ->
                loot.registerChest(new LootService.LootChest(
                        "dungeon-" + random.nextLong(Long.MAX_VALUE),
                        marker.location().getBlock().getLocation(), table, Long.MAX_VALUE)));
    }

    private void placeNpc(RoomWriter.PlacedMarker marker) {
        if (marker.value().isBlank()) return;
        ctx.services().optional(NpcService.class).ifPresent(npcs ->
                npcs.create("dungeon-" + random.nextLong(Long.MAX_VALUE),
                        marker.location(), marker.value()));
    }

    private void placeHologram(RoomWriter.PlacedMarker marker) {
        if (marker.value().isBlank()) return;
        ctx.services().optional(net.aethel.core.api.HologramService.class).ifPresent(holograms ->
                holograms.create("dungeon-" + random.nextLong(Long.MAX_VALUE), marker.location())
                        .lines(List.of(net.kyori.adventure.text.minimessage.MiniMessage
                                .miniMessage().deserialize(marker.value()))));
    }
}
