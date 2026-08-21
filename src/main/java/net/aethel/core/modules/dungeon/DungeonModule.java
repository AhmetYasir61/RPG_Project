package net.aethel.core.modules.dungeon;

import net.aethel.core.api.DungeonService;
import net.aethel.core.api.PartyService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.i18n.LangService;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.aethel.core.modules.dungeon.blueprint.RoomTemplate;
import net.aethel.core.modules.dungeon.design.RoomCapture;
import net.aethel.core.modules.dungeon.design.RoomStore;
import net.aethel.core.modules.dungeon.gen.LayoutGenerator;
import net.aethel.core.modules.dungeon.gen.RoomPlacement;
import net.aethel.core.modules.dungeon.gen.RoomWriter;
import net.aethel.core.modules.dungeon.instance.ActiveInstance;
import net.aethel.core.modules.dungeon.instance.InstanceWorlds;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dungeon modulu. Odalar chunk chunk uretilir, her ornek kendi boyutunda yasar ve
 * cekirdek kirilinca cokup icindekilerle birlikte silinir.
 */
@ModuleInfo(id = "dungeon", name = "Dungeon",
        depends = {"content"}, softDepends = {"mob", "loot", "region", "party", "npc", "hologram"})
public final class DungeonModule implements Module, DungeonService {

    private final DungeonSettings settings = new DungeonSettings();
    private final Map<String, DungeonDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveInstance> instances = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerInstance = new ConcurrentHashMap<>();
    private final RoomWriter writer = new RoomWriter();

    private Map<String, RoomTemplate> rooms = new LinkedHashMap<>();
    private RoomStore store;
    private InstanceWorlds worlds;
    private DungeonPopulator populator;
    private DungeonLifecycle lifecycle;
    private DungeonEntrance entrances;
    private ConfigFile config;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.config().open("modules/dungeon.yml", 1, settings, ConfigMigration.NONE);
        this.config = ctx.config().open("dungeons.yml", 1, null, ConfigMigration.NONE);
        this.store = new RoomStore(ctx.config().dataFolder(), ctx.logger());

        ctx.services().register(DungeonService.class, this, "dungeon");
        ctx.commands().register("dungeon", new DungeonCommand(ctx, this));

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("dungeon.enabled", true, "Dungeon sistemi");
        features.declare("dungeon.collapse", true,
                "Cekirdek kirilinca cokme (kapaliysa dungeon bos kalinca silinir)");
        features.declare("dungeon.one-way-exit", true,
                "Cikis tek yonlu: cikan oyuncu ayni ornege geri giremez");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        this.worlds = new InstanceWorlds(ctx.plugin(), ctx.logger());
        this.populator = new DungeonPopulator(ctx);
        this.lifecycle = new DungeonLifecycle(ctx, this, settings);

        worlds.cleanupOrphans(ctx.plugin().getServer().getWorldContainer());
        reload();

        this.entrances = new DungeonEntrance(ctx, this, config);
        ctx.listener("dungeon.enabled", new DungeonListener(ctx, this));
        ctx.listener("dungeon.enabled", entrances);
        // Yasam dongusu saniyede bir: cokme geri sayimi ve bos ornek temizligi.
        ctx.scheduler().repeating("dungeon", 20L, 20L, lifecycle::tick);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        // Sunucu kapanirken tum ornekler yok edilir; artik dunya birakmayiz.
        List.copyOf(instances.keySet()).forEach(this::destroy);
        instances.clear();
        playerInstance.clear();
        definitions.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        reload();
    }

    @Override
    public int reload() {
        rooms = store.loadAll();
        definitions.clear();
        ConfigurationSection root = config.yaml().getConfigurationSection("dungeons");
        if (root == null) {
            writeExample();
            root = config.yaml().getConfigurationSection("dungeons");
            if (root == null) return 0;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            definitions.put(id, new DungeonDefinition(id,
                    section.getString("display", id),
                    section.getString("difficulty", "normal"),
                    section.getInt("min-rooms", 8),
                    section.getInt("max-rooms", 16),
                    section.getInt("grid-radius", 6),
                    section.getStringList("room-pool"),
                    section.getString("core-room", "cekirdek"),
                    section.getString("entrance-room", "giris"),
                    section.getInt("collapse-seconds", settings.collapseSeconds),
                    section.getInt("max-players", 5)));
        }
        ctx.logger().info("Dungeon tanimi: " + definitions.size() + ", oda sablonu: " + rooms.size());
        return definitions.size();
    }

    private void writeExample() {
        var yaml = config.yaml();
        yaml.set("dungeons.golge_mahzeni.display", "<dark_purple>Golge Mahzeni</dark_purple>");
        yaml.set("dungeons.golge_mahzeni.difficulty", "zor");
        yaml.set("dungeons.golge_mahzeni.min-rooms", 8);
        yaml.set("dungeons.golge_mahzeni.max-rooms", 14);
        yaml.set("dungeons.golge_mahzeni.grid-radius", 5);
        yaml.set("dungeons.golge_mahzeni.room-pool",
                List.of("koridor", "salon", "hazine", "mob_odasi"));
        yaml.set("dungeons.golge_mahzeni.core-room", "cekirdek");
        yaml.set("dungeons.golge_mahzeni.entrance-room", "giris");
        yaml.set("dungeons.golge_mahzeni.collapse-seconds", 600);
        yaml.set("dungeons.golge_mahzeni.max-players", 5);
        yaml.set("dungeons.golge_mahzeni.mob-pool", "aethel:alev_muhafizi");
        yaml.set("dungeons.golge_mahzeni.loot-table", "dungeon_temel");
        config.save();
    }

    @Override
    public List<DungeonDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    @Override
    public Optional<DungeonDefinition> definition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    /**
     * Yeni ornek acar ve oyuncuyu (partisiyle birlikte) iceri alir. Dunya olusturma
     * ve harita yazma ana thread'de olmak zorunda; bu yuzden uretim tek seferde
     * degil, oda oda tick'lere yayilarak yapilir.
     */
    @Override
    public Optional<Instance> enter(Player player, String definitionId) {
        if (!ctx.feature("dungeon.enabled")) return Optional.empty();
        DungeonDefinition definition = definitions.get(definitionId);
        if (definition == null) return Optional.empty();
        if (instances.size() >= settings.maxInstances) {
            ctx.lang().send(player, "dungeon.server-full");
            return Optional.empty();
        }
        World world = worlds.create(UUID.randomUUID());
        if (world == null) return Optional.empty();

        ActiveInstance instance = new ActiveInstance(definitionId, world);
        instances.put(instance.id(), instance);

        build(instance, definition);
        instance.state(State.ACTIVE);

        List<Player> group = groupOf(player, definition.maxPlayers());
        group.forEach(member -> admit(instance, member));
        return Optional.of(instance.snapshot());
    }

    /** Yerlesimi uretir ve odalari dunyaya yazar. */
    private void build(ActiveInstance instance, DungeonDefinition definition) {
        LayoutGenerator generator = new LayoutGenerator(rooms);
        LayoutGenerator.Layout layout = generator.generate(definition, System.nanoTime());

        String mobPool = config.yaml().getString("dungeons." + definition.id() + ".mob-pool");
        String lootTable = config.yaml().getString("dungeons." + definition.id() + ".loot-table");

        for (RoomPlacement placement : layout.placements().values()) {
            RoomTemplate template = rooms.get(placement.roomId());
            if (template == null) continue;
            RoomWriter.Written written = writer.write(instance.world(), template,
                    placement.cellX(), placement.cellZ(), placement.quarterTurns());
            populator.populate(instance, written.markers(), mobPool, lootTable);
        }
        if (instance.entrance() == null) {
            instance.entrance(new Location(instance.world(), 8, settings.roomMinY + 2, 8));
        }
    }

    void admit(ActiveInstance instance, Player player) {
        instance.addPlayer(player.getUniqueId());
        playerInstance.put(player.getUniqueId(), instance.id());
        player.teleportAsync(instance.entrance());
        ctx.lang().send(player, "dungeon.entered",
                LangService.of("dungeon", definitions.get(instance.definitionId()).displayName()));
    }

    /** Parti varsa uyeleri de alinir; yoksa oyuncu tek basina girer. */
    private List<Player> groupOf(Player player, int maxPlayers) {
        List<Player> group = new ArrayList<>();
        group.add(player);
        ctx.services().optional(PartyService.class)
                .flatMap(parties -> parties.partyOf(player.getUniqueId()))
                .ifPresent(party -> party.members().stream()
                        .filter(member -> !member.equals(player.getUniqueId()))
                        .map(member -> ctx.plugin().getServer().getPlayer(member))
                        .filter(java.util.Objects::nonNull)
                        .limit(Math.max(0, maxPlayers - 1))
                        .forEach(group::add));
        return group;
    }

    /**
     * Cikis TEK YONLUDUR: oyuncu disari birakilir ve ornek kaydindan dusurulur,
     * ayni ornege geri donemez. Dungeon'a tekrar girmek yeni bir ornek acmak demektir.
     */
    @Override
    public void exit(Player player) {
        UUID instanceId = playerInstance.remove(player.getUniqueId());
        ActiveInstance instance = instanceId == null ? null : instances.get(instanceId);
        if (instance != null) instance.removePlayer(player.getUniqueId());

        Location destination = lifecycle.findExitLocation();
        player.teleportAsync(destination);
        ctx.lang().send(player, "dungeon.exited");
    }

    @Override
    public Optional<Instance> instanceOf(UUID player) {
        UUID instanceId = playerInstance.get(player);
        return instanceId == null ? Optional.empty()
                : Optional.ofNullable(instances.get(instanceId)).map(ActiveInstance::snapshot);
    }

    @Override
    public List<Instance> instances() {
        return instances.values().stream().map(ActiveInstance::snapshot).toList();
    }

    @Override
    public void breakCore(Instance snapshot, Player breaker) {
        ActiveInstance instance = instances.get(snapshot.id());
        if (instance == null || instance.collapsing()) return;
        DungeonDefinition definition = definitions.get(instance.definitionId());
        int seconds = definition == null ? settings.collapseSeconds : definition.collapseSeconds();

        if (!ctx.feature("dungeon.collapse")) {
            instance.state(State.ACTIVE);
            return;
        }
        instance.beginCollapse(breaker.getUniqueId(), seconds);
        lifecycle.announceCollapse(instance, seconds);
    }

    @Override
    public void destroy(UUID instanceId) {
        ActiveInstance instance = instances.remove(instanceId);
        if (instance == null) return;
        instance.players().forEach(playerInstance::remove);
        instance.state(State.DESTROYED);
        worlds.destroy(instance.world());
    }

    /** Tasarim dunyasindaki bulunulan chunk'i oda sablonu olarak kaydeder. */
    @Override
    public boolean exportRoom(Player designer, String roomId) {
        if (!designer.getWorld().getName().equals(settings.designWorld)) {
            ctx.lang().send(designer, "dungeon.not-design-world",
                    LangService.of("world", settings.designWorld));
            return false;
        }
        RoomTemplate template = RoomCapture.capture(roomId, designer.getChunk(),
                settings.roomMinY, settings.roomHeight, List.of());
        store.save(template);
        rooms.put(roomId, template);

        ctx.lang().send(designer, "dungeon.room-exported",
                LangService.of("room", roomId),
                LangService.of("doors", Integer.toBinaryString(template.doorMask())),
                LangService.of("markers", template.markers().size()));
        return true;
    }

    Map<UUID, ActiveInstance> activeInstances() {
        return instances;
    }

    Map<UUID, UUID> playerIndex() {
        return playerInstance;
    }

    /** Panel ve komutlarin giris yerlestirmesi icin. */
    public DungeonEntrance entrances() {
        return entrances;
    }

    Map<String, RoomTemplate> rooms() {
        return rooms;
    }

    DungeonSettings settings() {
        return settings;
    }

    CoreContext context() {
        return ctx;
    }
}
