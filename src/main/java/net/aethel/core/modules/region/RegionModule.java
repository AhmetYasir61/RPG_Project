package net.aethel.core.modules.region;

import net.aethel.core.api.Difficulty;
import net.aethel.core.api.RegionService;
import net.aethel.core.api.RegionShape;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bolge modulu: sekil tabanli alanlar, oncelik, bayrak ve zorluk atamalari.
 * Bolgeler bellekte tutulur; her sorgu once kaba AABB elemesinden gecer.
 */
@ModuleInfo(id = "region", name = "Bolgeler", depends = {"profile"})
public final class RegionModule implements Module, RegionService {

    private final Map<String, Region> regions = new ConcurrentHashMap<>();
    private final Map<String, Difficulty> difficulties = new ConcurrentHashMap<>();
    private RegionStorage storage;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.storage = new RegionStorage(ctx);
        ctx.services().register(RegionService.class, this, "region");

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("region.build-protection", true, "Insaat korumasi");
        features.declare("region.pvp-protection", true, "PvP korumasi");
        features.declare("region.difficulty-scaling", true,
                "Zorluk carpanlari (can / hasar / odul)");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        storage.loadRegions().forEach(region -> regions.put(region.id(), region));
        storage.loadDifficulties().forEach(diff -> difficulties.put(diff.id(), diff));
        if (difficulties.isEmpty()) {
            saveDifficulty(Difficulty.normal());
            saveDifficulty(new Difficulty("zor", "<red>Zor</red>", 78, "", false));
        }
        ctx.listener(new RegionListener(this, ctx));   // ic kontroller ozellik bazli
        ctx.logger().info("Bolge: " + regions.size() + ", zorluk: " + difficulties.size());
    }

    @Override
    public void onDisable(CoreContext ctx) {
        regions.clear();
        difficulties.clear();
    }

    @Override
    public Optional<Region> region(String id) {
        return Optional.ofNullable(regions.get(id));
    }

    @Override
    public List<Region> regions() {
        return List.copyOf(regions.values());
    }

    /**
     * Konum sorgusu: once dunya adi, sonra kaba kutu, en son gercek geometri testi.
     * Cokgen/serit testleri pahalidir; eleme sirasi bu yuzden onemlidir.
     */
    @Override
    public List<Region> at(Location location) {
        String world = location.getWorld().getName();
        return regions.values().stream()
                .filter(region -> region.world().equals(world))
                .filter(region -> region.shape().contains(location))
                .sorted(Comparator.comparingInt(Region::priority).reversed())
                .toList();
    }

    @Override
    public Optional<Region> highestAt(Location location) {
        return at(location).stream().findFirst();
    }

    @Override
    public Difficulty difficultyAt(Location location) {
        // Kapaliysa her yer "normal": mob gucu ve odul carpanlari devreye girmez.
        if (!ctx.feature("region.difficulty-scaling")) return Difficulty.normal();
        return highestAt(location)
                .map(Region::difficultyId)
                .map(difficulties::get)
                .orElseGet(Difficulty::normal);
    }

    /**
     * Bayrak testi en yuksek oncelikli bolgeden baslar: ic ice bolgelerde ic bolge
     * dis bolgenin kuralini ezebilsin diye (dungeon icinde PvP acik, disinda kapali).
     */
    @Override
    public boolean testFlag(Location location, String flag, Player player) {
        for (Region region : at(location)) {
            if (region.flags().contains("-" + flag)) return false;
            if (region.flags().contains(flag)) return true;
            if (player != null && region.owners().contains(player.getUniqueId().toString())) return true;
        }
        return true;   // hicbir bolge kural koymuyorsa varsayilan serbest
    }

    @Override
    public void save(Region region) {
        regions.put(region.id(), region);
        storage.saveRegion(region);
    }

    @Override
    public void delete(String id) {
        regions.remove(id);
        storage.deleteRegion(id);
    }

    @Override
    public List<Difficulty> difficulties() {
        return List.copyOf(difficulties.values());
    }

    @Override
    public Optional<Difficulty> difficulty(String id) {
        return Optional.ofNullable(difficulties.get(id));
    }

    @Override
    public void saveDifficulty(Difficulty difficulty) {
        difficulties.put(difficulty.id(), difficulty);
        storage.saveDifficulty(difficulty);
    }

    /** Panelin secim aracina sekil kurmasi icin yardimci. */
    public static RegionShape cuboid(Location a, Location b) {
        return new RegionShape.Cuboid(
                org.bukkit.util.Vector.getMinimum(a.toVector(), b.toVector()),
                org.bukkit.util.Vector.getMaximum(a.toVector(), b.toVector()));
    }
}
