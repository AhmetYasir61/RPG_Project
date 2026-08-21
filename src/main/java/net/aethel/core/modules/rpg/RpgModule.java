package net.aethel.core.modules.rpg;

import net.aethel.core.api.PlayerProfile;
import net.aethel.core.api.ProfileService;
import net.aethel.core.api.RpgService;
import net.aethel.core.api.StatType;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.i18n.LangService;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPG modulu: seviye, XP, statlar, mana ve yetenek agaci. Veriler profil nitelikleri
 * uzerinde tutulur; ayri bir tablo yerine profille birlikte yazilir ve yuklenir.
 */
@ModuleInfo(id = "rpg", name = "RPG", depends = {"profile"}, softDepends = {"skill"})
public final class RpgModule implements Module, RpgService {

    private static final String XP_KEY = "rpg:xp";
    private static final String POINTS_KEY = "rpg:points";
    private static final String NODES_KEY = "rpg:nodes";
    private static final String MANA_KEY = "rpg:mana";

    private final RpgSettings settings = new RpgSettings();
    private final Map<UUID, Double> mana = new ConcurrentHashMap<>();
    private SkillTree tree;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.config().open("modules/rpg.yml", 1, settings, ConfigMigration.NONE);
        this.tree = new SkillTree(ctx.logger());
        ctx.services().register(RpgService.class, this, "rpg");
        ctx.commands().register("rpg", new RpgCommand(ctx, this));

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("rpg.skill-tree", true, "Yetenek agaci");
        features.declare("rpg.stat-points", true, "Stat puani dagitimi");
        features.declare("rpg.mana", true, "Mana havuzu ve yenilenmesi");
        features.declare("rpg.stat-combat", true, "Statlarin hasar ve can havuzuna etkisi");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        tree.load(new File(ctx.config().dataFolder(), "skilltree.yml"));
        ctx.listener("rpg.stat-combat", new RpgListener(this, ctx, settings));

        // Mana yenilenmesi saniyede bir; daha sik yenilemek gorsel fark yaratmaz.
        // Ozellik kapaliysa gorev hic kurulmaz, bos tick harcanmaz.
        if (ctx.feature("rpg.mana")) {
            ctx.scheduler().repeating("rpg", 20L, 20L, this::regenerateMana);
        }
        registerPlaceholders();
    }

    @Override
    public void onDisable(CoreContext ctx) {
        mana.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        tree.load(new File(ctx.config().dataFolder(), "skilltree.yml"));
    }

    /** RPG degerlerini HUD ve chat'te kullanilabilir hale getirir. */
    private void registerPlaceholders() {
        ctx.services().optional(net.aethel.core.api.PlaceholderService.class)
                .ifPresent(service -> service.register("rpg", (player, key) -> {
                    if (player == null) return null;
                    UUID uuid = player.getUniqueId();
                    return switch (key) {
                        case "level" -> String.valueOf(level(uuid));
                        case "xp" -> String.format("%.0f", experience(uuid));
                        case "xp_next" -> String.format("%.0f",
                                experienceForLevel(level(uuid) + 1));
                        case "points" -> String.valueOf(availablePoints(uuid));
                        case "mana" -> String.format("%.0f", mana(uuid));
                        case "max_mana" -> String.format("%.0f", maxMana(uuid));
                        default -> {
                            try {
                                yield String.valueOf(stat(uuid,
                                        StatType.valueOf(key.toUpperCase(java.util.Locale.ROOT))));
                            } catch (IllegalArgumentException e) {
                                yield null;
                            }
                        }
                    };
                }));
    }

    @Override
    public int level(UUID player) {
        return ExperienceCurve.levelOf(experience(player), settings.maxLevel);
    }

    @Override
    public double experience(UUID player) {
        return profile(player).map(p -> p.attributeDouble(XP_KEY, 0)).orElse(0.0D);
    }

    @Override
    public double experienceForLevel(int level) {
        return ExperienceCurve.totalFor(level);
    }

    /**
     * XP eklenir ve seviye atlanip atlanmadigi kontrol edilir. Puan, atlanan HER
     * seviye icin verilir: tek seferde cok XP kazanan oyuncu puan kaybetmemeli.
     */
    @Override
    public void addExperience(Player player, double amount, String source) {
        profile(player.getUniqueId()).ifPresent(profile -> {
            int before = level(player.getUniqueId());
            profile.attribute(XP_KEY, profile.attributeDouble(XP_KEY, 0) + amount);
            int after = level(player.getUniqueId());

            if (after > before) {
                int gained = (after - before) * settings.pointsPerLevel;
                profile.attribute(POINTS_KEY, profile.attributeInt(POINTS_KEY, 0) + gained);
                ctx.lang().send(player, "rpg.level-up",
                        LangService.of("level", after), LangService.of("points", gained));
            }
        });
    }

    @Override
    public int stat(UUID player, StatType type) {
        return profile(player).map(p -> p.attributeInt(type.attributeKey(), 0)).orElse(0);
    }

    @Override
    public void setStat(UUID player, StatType type, int value) {
        profile(player).ifPresent(p -> p.attribute(type.attributeKey(), Math.max(0, value)));
    }

    @Override
    public int availablePoints(UUID player) {
        return profile(player).map(p -> p.attributeInt(POINTS_KEY, 0)).orElse(0);
    }

    @Override
    public boolean spendPoint(Player player, StatType type) {
        if (!ctx.feature("rpg.stat-points")) return false;
        return profile(player.getUniqueId()).map(profile -> {
            int points = profile.attributeInt(POINTS_KEY, 0);
            if (points < 1) return false;
            profile.attribute(POINTS_KEY, points - 1);
            profile.attribute(type.attributeKey(),
                    profile.attributeInt(type.attributeKey(), 0) + 1);
            return true;
        }).orElse(false);
    }

    @Override
    public boolean unlockNode(Player player, String nodeId) {
        if (!ctx.feature("rpg.skill-tree")) return false;
        UUID uuid = player.getUniqueId();
        Set<String> unlocked = unlockedNodes(uuid);
        SkillTree.UnlockCheck check = tree.canUnlock(nodeId, level(uuid),
                availablePoints(uuid), unlocked);

        if (check != SkillTree.UnlockCheck.OK) {
            ctx.lang().send(player, "rpg.unlock-" + check.name().toLowerCase(java.util.Locale.ROOT)
                    .replace('_', '-'));
            return false;
        }
        return profile(uuid).map(profile -> {
            SkillTree.Node node = tree.node(nodeId);
            profile.attribute(POINTS_KEY, availablePoints(uuid) - node.cost());
            Set<String> updated = new LinkedHashSet<>(unlocked);
            updated.add(nodeId);
            profile.attribute(NODES_KEY, String.join(",", updated));
            ctx.lang().send(player, "rpg.node-unlocked",
                    LangService.of("node", node.displayName()));
            return true;
        }).orElse(false);
    }

    @Override
    public Set<String> unlockedNodes(UUID player) {
        return profile(player)
                .flatMap(p -> p.attribute(NODES_KEY))
                .map(raw -> raw.isBlank()
                        ? new LinkedHashSet<String>()
                        : new LinkedHashSet<>(java.util.List.of(raw.split(","))))
                .orElseGet(LinkedHashSet::new);
    }

    /** Temel statlar + acilan dugumlerin bonuslari. */
    @Override
    public Map<StatType, Integer> effectiveStats(UUID player) {
        Map<StatType, Integer> result = new EnumMap<>(StatType.class);
        Map<String, Double> bonuses = tree.bonusesOf(unlockedNodes(player));
        for (StatType type : StatType.values()) {
            double bonus = bonuses.getOrDefault(type.name().toLowerCase(java.util.Locale.ROOT), 0.0);
            result.put(type, stat(player, type) + (int) Math.round(bonus));
        }
        return result;
    }

    @Override
    public double maxMana(UUID player) {
        return settings.baseMana
                + effectiveStats(player).get(StatType.INTELLIGENCE) * settings.manaPerIntelligence;
    }

    @Override
    public double mana(UUID player) {
        return mana.computeIfAbsent(player, key -> profile(key)
                .map(p -> p.attributeDouble(MANA_KEY, maxMana(key))).orElse(0.0D));
    }

    @Override
    public boolean consumeMana(UUID player, double amount) {
        // Mana kapaliysa yetenekler bedelsiz calisir; ayri bir kod yolu gerekmez.
        if (!ctx.feature("rpg.mana")) return true;
        double current = mana(player);
        if (current < amount) return false;
        mana.put(player, current - amount);
        return true;
    }

    private void regenerateMana() {
        for (Player player : ctx.plugin().getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            double max = maxMana(uuid);
            double current = mana(uuid);
            if (current >= max) continue;
            mana.put(uuid, Math.min(max, current + settings.manaRegen));
        }
    }

    private Optional<PlayerProfile> profile(UUID uuid) {
        return ctx.services().optional(ProfileService.class)
                .flatMap(profiles -> profiles.cached(uuid));
    }

    SkillTree tree() {
        return tree;
    }

    RpgSettings settings() {
        return settings;
    }
}
