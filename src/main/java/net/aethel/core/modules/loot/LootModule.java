package net.aethel.core.modules.loot;

import net.aethel.core.api.ItemService;
import net.aethel.core.api.LootService;
import net.aethel.core.api.RegionService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loot sandigi modulu. Nadirlik sandigin bulundugu bolgenin ANLIK tehdit yogunlugundan
 * hesaplanir: bolgeyi temizleyip acmak ile kalabalikken acmak farkli sonuc verir.
 */
@ModuleInfo(id = "loot", name = "Loot", depends = {"content", "region"})
public final class LootModule implements Module, LootService {

    private final Map<String, LootTable> tables = new ConcurrentHashMap<>();
    private final Map<String, LootChest> chests = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private ConfigFile config;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.config = ctx.config().open("loot.yml", 1, null, ConfigMigration.NONE);
        ctx.services().register(LootService.class, this, "loot");

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("loot.threat-scaling", true,
                "Bolgedeki tehdit yogunluguna gore nadirlik artisi");
        features.declare("loot.chest-cooldown", true, "Sandik acma sogumasi");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        ctx.commands().register("loot", new LootCommand(ctx, this));
        loadTables();
        loadChests();
        ctx.listener(new LootListener(this, ctx));
        ctx.logger().info("Loot tablosu: " + tables.size() + ", sandik: " + chests.size());
    }

    @Override
    public void onDisable(CoreContext ctx) {
        tables.clear();
        chests.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        loadTables();
    }

    private void loadTables() {
        tables.clear();
        ConfigurationSection root = config.yaml().getConfigurationSection("tables");
        if (root == null) {
            writeExampleTable();
            root = config.yaml().getConfigurationSection("tables");
            if (root == null) return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            List<LootEntry> entries = new ArrayList<>();
            ConfigurationSection items = section.getConfigurationSection("entries");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection entry = items.getConfigurationSection(key);
                    if (entry == null) continue;
                    entries.add(new LootEntry(
                            entry.getString("item", key),
                            entry.getInt("min", 1),
                            entry.getInt("max", 1),
                            entry.getDouble("weight", 10.0),
                            entry.getInt("rarity-tier", 0)));
                }
            }
            tables.put(id, new LootTable(id, entries,
                    section.getInt("min-rolls", 1), section.getInt("max-rolls", 3)));
        }
    }

    /** Ilk acilista ornek bir tablo yazilir ki sistem bos gelmesin. */
    private void writeExampleTable() {
        var yaml = config.yaml();
        yaml.set("tables.dungeon_temel.min-rolls", 2);
        yaml.set("tables.dungeon_temel.max-rolls", 4);
        yaml.set("tables.dungeon_temel.entries.altin.item", "minecraft:gold_ingot");
        yaml.set("tables.dungeon_temel.entries.altin.min", 2);
        yaml.set("tables.dungeon_temel.entries.altin.max", 6);
        yaml.set("tables.dungeon_temel.entries.altin.weight", 40.0);
        yaml.set("tables.dungeon_temel.entries.altin.rarity-tier", 0);
        yaml.set("tables.dungeon_temel.entries.kilic.item", "aethel:alev_kilici");
        yaml.set("tables.dungeon_temel.entries.kilic.min", 1);
        yaml.set("tables.dungeon_temel.entries.kilic.max", 1);
        yaml.set("tables.dungeon_temel.entries.kilic.weight", 1.5);
        yaml.set("tables.dungeon_temel.entries.kilic.rarity-tier", 2);
        config.save();
    }

    private void loadChests() {
        chests.clear();
        ConfigurationSection root = config.yaml().getConfigurationSection("chests");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            Location location = section.getLocation("location");
            if (location == null) continue;
            chests.put(id, new LootChest(id, location,
                    section.getString("table", "dungeon_temel"),
                    section.getLong("respawn-seconds", 600)));
        }
    }

    @Override
    public double threatAt(Location location) {
        return ThreatCalculator.threat(location);
    }

    @Override
    public double rarityMultiplier(Location location) {
        // Kapaliysa nadirlik sabittir: sandik her zaman ayni dagilimla acilir.
        if (!ctx.feature("loot.threat-scaling")) return 1.0D;
        var difficulty = ctx.services().optional(RegionService.class)
                .map(regions -> regions.difficultyAt(location))
                .orElseGet(net.aethel.core.api.Difficulty::normal);
        return ThreatCalculator.rarityMultiplier(threatAt(location), difficulty);
    }

    /**
     * Sandik acilirken tehdit O AN olculur; onceden hesaplanip onbelleklenmez, cunku
     * bolgeyi temizleyip acmak ile kalabalikken acmak arasindaki fark mekanigin kendisidir.
     */
    @Override
    public List<ItemStack> roll(String tableId, Location location, Player opener) {
        LootTable table = tables.get(tableId);
        if (table == null) return List.of();

        double multiplier = rarityMultiplier(location);
        Optional<ItemService> items = ctx.services().optional(ItemService.class);
        List<ItemStack> result = new ArrayList<>();

        for (LootTable.Roll roll : table.roll(multiplier, random)) {
            ItemStack stack = items.flatMap(service -> service.create(roll.itemId(), roll.amount()))
                    .orElseGet(() -> vanilla(roll));
            if (stack != null) result.add(stack);
        }
        return result;
    }

    /** Custom item degilse vanilla materyal olarak denenir. */
    private ItemStack vanilla(LootTable.Roll roll) {
        Material material = Material.matchMaterial(roll.itemId().replace("minecraft:", ""));
        return material == null ? null : new ItemStack(material, roll.amount());
    }

    @Override
    public void registerChest(LootChest chest) {
        chests.put(chest.id(), chest);
        String base = "chests." + chest.id();
        config.yaml().set(base + ".location", chest.location());
        config.yaml().set(base + ".table", chest.tableId());
        config.yaml().set(base + ".respawn-seconds", chest.respawnSeconds());
        config.save();
    }

    @Override
    public void removeChest(String id) {
        chests.remove(id);
        config.yaml().set("chests." + id, null);
        config.save();
    }

    @Override
    public List<LootChest> chests() {
        return List.copyOf(chests.values());
    }

    Optional<LootChest> chestAt(Location location) {
        return chests.values().stream()
                .filter(chest -> chest.location().getWorld().equals(location.getWorld())
                        && chest.location().distanceSquared(location) < 1.0)
                .findFirst();
    }
}
