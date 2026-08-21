package net.aethel.core.modules.dungeon;

import net.aethel.core.api.DungeonService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.i18n.LangService;
import net.aethel.core.modules.dungeon.instance.ActiveInstance;
import net.aethel.core.modules.dungeon.instance.CollapseSequence;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orneklerin saniyelik yasam dongusu: cokme geri sayimi, bos ornek temizligi ve
 * cikis noktasi secimi. Tum zamanlama tek bir gorevde toplanir.
 */
final class DungeonLifecycle {

    private final CoreContext ctx;
    private final DungeonModule module;
    private final DungeonSettings settings;
    private final CollapseSequence collapse;
    private final Map<UUID, Long> emptySince = new ConcurrentHashMap<>();
    private final Random random = new Random();

    DungeonLifecycle(CoreContext ctx, DungeonModule module, DungeonSettings settings) {
        this.ctx = ctx;
        this.module = module;
        this.settings = settings;
        this.collapse = new CollapseSequence(ctx);
    }

    /** Saniyede bir calisir. */
    void tick() {
        for (ActiveInstance instance : List.copyOf(module.activeInstances().values())) {
            if (instance.collapsing()) {
                tickCollapse(instance);
                continue;
            }
            tickEmpty(instance);
        }
    }

    private void tickCollapse(ActiveInstance instance) {
        if (instance.collapseFinished()) {
            // Sure doldu: icerde kalanlar olur ve dunya, esyalariyla birlikte silinir.
            collapse.finish(instance);
            ctx.scheduler().later("dungeon", 40L, () -> module.destroy(instance.id()));
            instance.state(DungeonService.State.DESTROYED);
            return;
        }
        collapse.tick(instance);
    }

    /**
     * Bos kalan ornek hemen degil, bir sure sonra silinir: oyuncunun baglantisi
     * kopup geri gelmesi ya da kisa bir ayrilma dungeon'u aninda yok etmemeli.
     */
    private void tickEmpty(ActiveInstance instance) {
        boolean empty = instance.players().stream()
                .map(uuid -> ctx.plugin().getServer().getPlayer(uuid))
                .noneMatch(player -> player != null && player.getWorld().equals(instance.world()));

        if (!empty) {
            emptySince.remove(instance.id());
            return;
        }
        long since = emptySince.computeIfAbsent(instance.id(), key -> System.currentTimeMillis());
        if (System.currentTimeMillis() - since >= settings.emptyTimeoutSeconds * 1000L) {
            emptySince.remove(instance.id());
            module.destroy(instance.id());
        }
    }

    /** Cekirdek kirildiginda tum oyunculara duyurur. */
    void announceCollapse(ActiveInstance instance, int seconds) {
        instance.players().forEach(uuid -> {
            Player player = ctx.plugin().getServer().getPlayer(uuid);
            if (player == null) return;
            ctx.lang().send(player, "dungeon.core-broken",
                    LangService.of("minutes", seconds / 60));
        });
    }

    /**
     * Cikis noktasi: girisin yakini DEGIL, uzagindaki bir orman. Oyuncu dungeon'dan
     * ciktiginda kendini baska bir yerde bulmali; cikisin girise acilmasi, dungeon'un
     * tek yonlu olma kuralini anlamsizlastirir.
     */
    Location findExitLocation() {
        World world = ctx.plugin().getServer().getWorld(settings.exitWorld);
        if (world == null) world = ctx.plugin().getServer().getWorlds().get(0);

        List<String> biomes = settings.exitBiomes.stream().map(String::valueOf).toList();
        Location spawn = world.getSpawnLocation();

        // Once orman aranir; bulunamazsa rastgele bir nokta kullanilir. Aramayi
        // sinirli tutuyoruz: uygun biyom yoksa sonsuza kadar chunk yuklemek olmaz.
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = spawn.getBlockX() + random.nextInt(settings.exitScatter * 2) - settings.exitScatter;
            int z = spawn.getBlockZ() + random.nextInt(settings.exitScatter * 2) - settings.exitScatter;
            int y = world.getHighestBlockYAt(x, z);
            Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5);

            String biome = candidate.getBlock().getBiome().getKey().getKey().toUpperCase(java.util.Locale.ROOT);
            if (biomes.isEmpty() || biomes.stream().anyMatch(biome::contains)) {
                return candidate;
            }
        }
        int x = spawn.getBlockX() + random.nextInt(200) - 100;
        int z = spawn.getBlockZ() + random.nextInt(200) - 100;
        return new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
    }
}
