package net.aethel.core.modules.quest;

import net.aethel.core.api.PlayerProfile;
import net.aethel.core.api.ProfileService;
import net.aethel.core.api.QuestService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.i18n.LangService;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.aethel.core.util.Yamls;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gorev modulu. Ilerleme profil niteliklerinde tutulur; oyuncu cikip girse de
 * kaldigi yerden devam eder ve tamamlanan gorev otomatik teslim edilir.
 */
@ModuleInfo(id = "quest", name = "Gorevler", depends = {"profile"}, softDepends = {"menu", "economy"})
public final class QuestModule implements Module, QuestService {

    private static final String ACTIVE_PREFIX = "quest:active:";
    private static final String COMPLETED_KEY = "quest:completed";

    private final Map<String, Quest> quests = new ConcurrentHashMap<>();
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.services().register(QuestService.class, this, "quest");
        ctx.commands().register("quest", new QuestCommand(ctx, this));
    }

    @Override
    public void onEnable(CoreContext ctx) {
        reload();
        ctx.listener(new QuestListener(this));
    }

    @Override
    public void onDisable(CoreContext ctx) {
        quests.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        reload();
    }

    @Override
    public int reload() {
        quests.clear();
        File contents = new File(ctx.config().dataFolder(), "contents");
        File[] namespaces = contents.listFiles(File::isDirectory);
        if (namespaces == null) return 0;

        for (File namespace : namespaces) {
            File folder = new File(namespace, "quests");
            File[] files = folder.listFiles(file -> file.getName().endsWith(".yml"));
            if (files == null) continue;
            for (File file : files) {
                loadFile(namespace.getName(), file);
            }
        }
        ctx.logger().info("Gorev: " + quests.size());
        return quests.size();
    }

    private void loadFile(String namespace, File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) continue;

            List<Objective> objectives = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("objectives")) {
                ObjectiveType type;
                try {
                    type = ObjectiveType.valueOf(
                            Yamls.string(raw, "type", "KILL").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    ctx.logger().warning("Bilinmeyen hedef tipi: " + raw.get("type"));
                    continue;
                }
                objectives.add(new Objective(type,
                        Yamls.string(raw, "target", ""),
                        Yamls.integer(raw, "amount", 1),
                        Yamls.string(raw, "description", "")));
            }
            String fullId = namespace + ":" + id;
            quests.put(fullId, new Quest(fullId,
                    section.getString("display", id),
                    section.getStringList("description"),
                    objectives,
                    section.getStringList("rewards"),
                    section.getString("next"),
                    section.getInt("required-level", 1),
                    section.getBoolean("repeatable", false)));
        }
    }

    @Override
    public Optional<Quest> quest(String id) {
        return Optional.ofNullable(quests.get(id));
    }

    @Override
    public List<Quest> quests() {
        return List.copyOf(quests.values());
    }

    /** Tekrarlanamayan bir gorev ikinci kez baslatilamaz. */
    @Override
    public boolean start(Player player, String questId) {
        Quest quest = quests.get(questId);
        if (quest == null) return false;
        UUID uuid = player.getUniqueId();
        if (!quest.repeatable() && completedQuests(uuid).contains(questId)) return false;
        if (activeQuests(uuid).containsKey(questId)) return false;

        return profile(uuid).map(profile -> {
            profile.attribute(ACTIVE_PREFIX + questId, encodeProgress(new LinkedHashMap<>()));
            ctx.lang().send(player, "quest.started",
                    LangService.of("quest", quest.displayName()));
            return true;
        }).orElse(false);
    }

    @Override
    public Map<String, Map<Integer, Integer>> activeQuests(UUID player) {
        Map<String, Map<Integer, Integer>> result = new LinkedHashMap<>();
        profile(player).ifPresent(profile -> profile.attributes().forEach((key, value) -> {
            if (!key.startsWith(ACTIVE_PREFIX)) return;
            result.put(key.substring(ACTIVE_PREFIX.length()), decodeProgress(value));
        }));
        return result;
    }

    @Override
    public List<String> completedQuests(UUID player) {
        return profile(player).flatMap(profile -> profile.attribute(COMPLETED_KEY))
                .map(raw -> raw.isBlank() ? List.<String>of() : List.of(raw.split(",")))
                .orElse(List.of());
    }

    /**
     * Bir olay tum aktif gorevlerin ilgili hedeflerine islenir. Tek bir olay birden
     * fazla gorevi ilerletebilir; MMORPG'de ayni mobu iki gorev icin avlamak normaldir.
     */
    @Override
    public void progress(Player player, ObjectiveType type, String target, int amount) {
        UUID uuid = player.getUniqueId();
        activeQuests(uuid).forEach((questId, progress) -> {
            Quest quest = quests.get(questId);
            if (quest == null) return;

            boolean changed = false;
            for (int index = 0; index < quest.objectives().size(); index++) {
                Objective objective = quest.objectives().get(index);
                if (objective.type() != type) continue;
                if (!objective.target().equalsIgnoreCase(target)
                        && !objective.target().equals("*")) continue;

                int current = progress.getOrDefault(index, 0);
                if (current >= objective.amount()) continue;
                progress.put(index, Math.min(objective.amount(), current + amount));
                changed = true;
            }
            if (!changed) return;
            profile(uuid).ifPresent(profile ->
                    profile.attribute(ACTIVE_PREFIX + questId, encodeProgress(progress)));
            if (isComplete(quest, progress)) complete(player, quest);
        });
    }

    private boolean isComplete(Quest quest, Map<Integer, Integer> progress) {
        for (int index = 0; index < quest.objectives().size(); index++) {
            if (progress.getOrDefault(index, 0) < quest.objectives().get(index).amount()) {
                return false;
            }
        }
        return true;
    }

    private void complete(Player player, Quest quest) {
        UUID uuid = player.getUniqueId();
        profile(uuid).ifPresent(profile -> {
            profile.removeAttribute(ACTIVE_PREFIX + quest.id());
            List<String> completed = new ArrayList<>(completedQuests(uuid));
            completed.add(quest.id());
            profile.attribute(COMPLETED_KEY, String.join(",", completed));
        });
        quest.rewards().forEach(reward -> giveReward(player, reward));
        ctx.lang().send(player, "quest.completed",
                LangService.of("quest", quest.displayName()));

        if (quest.nextQuest() != null) start(player, quest.nextQuest());
    }

    /** Odul DSL'i: [item], [money], [xp], [command]. */
    private void giveReward(Player player, String reward) {
        int end = reward.indexOf(']');
        if (!reward.startsWith("[") || end < 0) return;
        String type = reward.substring(1, end).toLowerCase(Locale.ROOT);
        String value = reward.substring(end + 1).trim();

        switch (type) {
            case "item" -> ctx.services().optional(net.aethel.core.api.ItemService.class)
                    .flatMap(items -> items.create(value))
                    .ifPresent(stack -> player.getInventory().addItem(stack));
            case "money" -> ctx.services().optional(net.aethel.core.api.EconomyService.class)
                    .ifPresent(economy -> economy.deposit(player.getUniqueId(),
                            Double.parseDouble(value), "quest"));
            case "xp" -> ctx.services().optional(net.aethel.core.api.RpgService.class)
                    .ifPresent(rpg -> rpg.addExperience(player, Double.parseDouble(value), "quest"));
            case "command" -> ctx.plugin().getServer().dispatchCommand(
                    ctx.plugin().getServer().getConsoleSender(),
                    value.replace("<player>", player.getName()));
            default -> ctx.logger().warning("Bilinmeyen gorev odulu: " + type);
        }
    }

    @Override
    public boolean abandon(Player player, String questId) {
        return profile(player.getUniqueId()).map(profile -> {
            if (profile.attribute(ACTIVE_PREFIX + questId).isEmpty()) return false;
            profile.removeAttribute(ACTIVE_PREFIX + questId);
            return true;
        }).orElse(false);
    }

    /** Ilerleme "index:miktar" ciftleri olarak saklanir; tek nitelikte tum gorev. */
    private String encodeProgress(Map<Integer, Integer> progress) {
        StringBuilder builder = new StringBuilder();
        progress.forEach((index, amount) -> builder.append(index).append(':').append(amount).append(','));
        return builder.toString();
    }

    private Map<Integer, Integer> decodeProgress(String raw) {
        Map<Integer, Integer> progress = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return progress;
        for (String part : raw.split(",")) {
            if (part.isBlank()) continue;
            String[] pair = part.split(":");
            progress.put(Integer.parseInt(pair[0]), Integer.parseInt(pair[1]));
        }
        return progress;
    }

    private Optional<PlayerProfile> profile(UUID uuid) {
        return ctx.services().optional(ProfileService.class)
                .flatMap(profiles -> profiles.cached(uuid));
    }
}
