package net.aethel.core.modules.dialog;

import net.aethel.core.api.DialogService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.aethel.core.util.Yamls;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typewriter tarzi diyalog motoru. Metin action bar yerine chat'te harf harf
 * yenilenir; secenekler tiklanabilir metin olarak sunulur, entity kullanilmaz.
 */
@ModuleInfo(id = "dialog", name = "Diyalog", softDepends = {"quest", "npc"})
public final class DialogModule implements Module, DialogService {

    /** Metin akis hizi: her 2 tick'te bir ilerleme, okunakli bir tempo verir. */
    private static final long TICK_PERIOD = 2L;

    private final Map<String, DialogNode> nodes = new ConcurrentHashMap<>();
    private final Map<UUID, DialogSession> sessions = new ConcurrentHashMap<>();
    private final MiniMessage mini = MiniMessage.miniMessage();
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.services().register(DialogService.class, this, "dialog");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        reload();
        ctx.scheduler().repeating("dialog", TICK_PERIOD, TICK_PERIOD, this::tick);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        nodes.clear();
        sessions.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        reload();
    }

    @Override
    public int reload() {
        nodes.clear();
        File contents = new File(ctx.config().dataFolder(), "contents");
        File[] namespaces = contents.listFiles(File::isDirectory);
        if (namespaces == null) return 0;

        for (File namespace : namespaces) {
            File folder = new File(namespace, "dialogs");
            File[] files = folder.listFiles(file -> file.getName().endsWith(".yml"));
            if (files == null) continue;
            for (File file : files) {
                loadFile(namespace.getName(), file);
            }
        }
        ctx.logger().info("Diyalog dugumu: " + nodes.size());
        return nodes.size();
    }

    private void loadFile(String namespace, File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(id);
            if (section == null) continue;

            List<Choice> choices = new ArrayList<>();
            for (Map<?, ?> raw : section.getMapList("choices")) {
                choices.add(new Choice(
                        Yamls.string(raw, "text", "..."),
                        raw.get("target") == null ? null : String.valueOf(raw.get("target")),
                        Yamls.stringList(raw, "conditions"),
                        Yamls.stringList(raw, "outcomes")));
            }
            nodes.put(namespace + ":" + id, new DialogNode(namespace + ":" + id,
                    section.getStringList("lines"), choices,
                    section.getStringList("conditions"),
                    section.getStringList("outcomes"),
                    section.getInt("chars-per-tick", 2)));
        }
    }

    @Override
    public Optional<DialogNode> node(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    /** Zaten diyalogdaki oyuncuya yeni diyalog acilmaz; ekranlar ust uste binmesin. */
    @Override
    public boolean start(Player player, String dialogId) {
        if (sessions.containsKey(player.getUniqueId())) return false;
        DialogNode node = nodes.get(dialogId);
        if (node == null) return false;
        sessions.put(player.getUniqueId(), new DialogSession(node));
        return true;
    }

    @Override
    public void skip(Player player) {
        DialogSession session = sessions.get(player.getUniqueId());
        if (session != null) session.skip();
    }

    @Override
    public void choose(Player player, int choiceIndex) {
        DialogSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.awaitingChoice()) return;
        List<Choice> choices = session.node().choices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) return;

        Choice choice = choices.get(choiceIndex);
        choice.outcomes().forEach(outcome -> runOutcome(player, outcome));
        sessions.remove(player.getUniqueId());
        if (choice.targetNode() != null) start(player, choice.targetNode());
    }

    @Override
    public void stop(Player player) {
        sessions.remove(player.getUniqueId());
    }

    @Override
    public boolean inDialog(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Akan metni her turda tazeler ve secenekleri gosterir. */
    private void tick() {
        sessions.entrySet().removeIf(entry -> {
            Player player = ctx.plugin().getServer().getPlayer(entry.getKey());
            if (player == null) return true;
            DialogSession session = entry.getValue();

            if (!session.finished()) {
                session.advance();
                player.sendActionBar(mini.deserialize(session.currentText()));
                return false;
            }
            if (session.awaitingChoice() && !session.node().choices().isEmpty()) {
                showChoices(player, session);
                return false;
            }
            session.node().outcomes().forEach(outcome -> runOutcome(player, outcome));
            return true;
        });
    }

    private void showChoices(Player player, DialogSession session) {
        List<Choice> choices = session.node().choices();
        for (int i = 0; i < choices.size(); i++) {
            String command = "/dialog sec " + i;
            player.sendMessage(mini.deserialize("<yellow><click:run_command:'" + command + "'>"
                    + "[" + (i + 1) + "] " + choices.get(i).text() + "</click></yellow>"));
        }
        session.skip();
    }

    /** Sonuc DSL'i: [quest], [item], [command], [message]. */
    private void runOutcome(Player player, String outcome) {
        int end = outcome.indexOf(']');
        if (!outcome.startsWith("[") || end < 0) return;
        String type = outcome.substring(1, end).toLowerCase(java.util.Locale.ROOT);
        String value = outcome.substring(end + 1).trim();

        switch (type) {
            case "command" -> player.performCommand(value);
            case "message" -> player.sendMessage(mini.deserialize(value));
            case "item" -> ctx.services().optional(net.aethel.core.api.ItemService.class)
                    .flatMap(items -> items.create(value))
                    .ifPresent(stack -> player.getInventory().addItem(stack));
            case "quest" -> ctx.services().optional(net.aethel.core.api.QuestService.class)
                    .ifPresent(quests -> quests.start(player, value));
            default -> ctx.logger().warning("Bilinmeyen diyalog sonucu: " + type);
        }
    }
}
