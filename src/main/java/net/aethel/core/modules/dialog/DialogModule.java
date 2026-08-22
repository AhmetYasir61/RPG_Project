package net.aethel.core.modules.dialog;

import net.aethel.core.api.DialogService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.aethel.core.util.Sounds;
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
    private final Map<String, Integer> portraits = new ConcurrentHashMap<>();
    private final MiniMessage mini = MiniMessage.miniMessage();
    private DialogRenderer renderer;
    private DialogStyle style = DialogStyle.BOX;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.services().register(DialogService.class, this, "dialog");
        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("dialog.typewriter", true,
                "Harf harf akan metin (kapaliysa metin tek seferde gosterilir)");
        features.declare("dialog.box", true,
                "Cerceveli diyalog kutusu (kapaliysa metin sohbete yazilir)");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        ctx.commands().register("dialog", new DialogCommand(ctx, this));
        this.renderer = new DialogRenderer(ctx);
        this.style = ctx.feature("dialog.box") ? DialogStyle.BOX : DialogStyle.CHAT;
        reload();
        ctx.listener("dialog.box", new DialogInput(this));
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
                    section.getString("speaker", ""),
                    section.getString("portrait", ""),
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

        // Satirlar kutu genisligine gore ONCEDEN sarmalanir: her tick yeniden
        // olcmek, akan metinde satir sonlarinin oynamasina yol acar.
        List<String> wrapped = new java.util.ArrayList<>();
        node.lines().forEach(line ->
                wrapped.addAll(TextMeasure.wrap(line, DialogGlyphs.TEXT_WIDTH)));

        String speaker = node.speaker() == null || node.speaker().isBlank()
                ? "?" : node.speaker();
        // Portre tanimda verilmisse o kullanilir; verilmemisse konusmaci adina
        // gore kalici bir indeks atanir, boylece ayni NPC hep ayni portreyi alir.
        int portrait = node.portrait() == null || node.portrait().isBlank()
                ? portraits.computeIfAbsent(speaker, key -> portraits.size())
                : parsePortrait(node.portrait(), speaker);

        sessions.put(player.getUniqueId(),
                new DialogSession(node, speaker, portrait, wrapped));
        return true;
    }

    @Override
    public void skip(Player player) {
        DialogSession session = sessions.get(player.getUniqueId());
        if (session != null) session.skip();
    }

    /** Hotbar kaydirmasi secim imlecini oynatir. */
    void moveSelection(Player player, int delta) {
        DialogSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        session.moveSelection(delta);
        Sounds.parse("ui.button.click").ifPresent(sound ->
                player.playSound(player.getLocation(), sound, 0.4f, 1.6f));
        renderer.draw(player, session.frame());
    }

    /**
     * Shift/sag tik: metin akiyorsa atlar, tamamlanmissa secimi uygular ya da
     * secenek yoksa diyalogu bitirir. Tek tusla hem "atla" hem "onayla" olmasi,
     * oyuncunun akisi kesmeden ilerlemesini saglar.
     */
    void confirm(Player player) {
        DialogSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        if (!session.textComplete()) {
            session.skip();
            renderer.draw(player, session.frame());
            return;
        }
        if (session.node().choices().isEmpty()) {
            finish(player, session);
            return;
        }
        choose(player, session.selectedChoice());
    }

    /** Secim bekleniyor mu; girdi dinleyicisi hotbar'i buna gore kilitler. */
    boolean awaitingChoice(Player player) {
        DialogSession session = sessions.get(player.getUniqueId());
        return session != null && session.textComplete()
                && !session.node().choices().isEmpty();
    }

    @Override
    public void choose(Player player, int choiceIndex) {
        DialogSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.textComplete()) return;
        List<Choice> choices = session.node().choices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) return;

        Choice choice = choices.get(choiceIndex);
        sessions.remove(player.getUniqueId());
        renderer.clear(player);
        Sounds.parse("entity.experience_orb.pickup").ifPresent(sound ->
                player.playSound(player.getLocation(), sound, 0.6f, 1.2f));

        choice.outcomes().forEach(outcome -> runOutcome(player, outcome));
        if (choice.targetNode() != null) start(player, choice.targetNode());
    }

    /** Portre alani sayi ya da ad olabilir; ad ise kalici bir indekse cevrilir. */
    private int parsePortrait(String raw, String speaker) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return portraits.computeIfAbsent(raw, key -> portraits.size());
        }
    }

    /** Secenegi olmayan bir dugumun sonu: sonuclari uygular ve kutuyu kapatir. */
    private void finish(Player player, DialogSession session) {
        sessions.remove(player.getUniqueId());
        renderer.clear(player);
        session.node().outcomes().forEach(outcome -> runOutcome(player, outcome));
    }

    @Override
    public void stop(Player player) {
        sessions.remove(player.getUniqueId());
        if (renderer != null) renderer.clear(player);
    }

    @Override
    public boolean inDialog(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /**
     * Akan metni ilerletir ve kutuyu yeniden cizer. Metin tamamlandiktan sonra da
     * cizim surer: title kisa omurlu oldugu icin yenilenmezse kutu kaybolur.
     */
    private void tick() {
        sessions.entrySet().removeIf(entry -> {
            Player player = ctx.plugin().getServer().getPlayer(entry.getKey());
            if (player == null) return true;
            DialogSession session = entry.getValue();

            if (!session.textComplete()) {
                if (ctx.feature("dialog.typewriter")) {
                    session.advance(session.node().charsPerTick());
                    typeSound(player);
                } else {
                    session.skip();
                }
            }
            render(player, session);
            return false;
        });
    }

    /** Aktif stile gore cizer; BOX disinda kutu yerine duz metin gonderilir. */
    private void render(Player player, DialogSession session) {
        if (style == DialogStyle.BOX && ctx.feature("dialog.box")) {
            renderer.draw(player, session.frame());
            return;
        }
        String text = String.join(" ", session.visibleLines());
        if (style == DialogStyle.ACTIONBAR) {
            player.sendActionBar(mini.deserialize(text));
            return;
        }
        if (session.textComplete()) {
            player.sendMessage(mini.deserialize("<yellow>" + session.speaker()
                    + ":</yellow> <gray>" + text + "</gray>"));
            renderChatChoices(player, session);
            stop(player);
        }
    }

    /** CHAT modunda secenekler tiklanabilir satir olarak gonderilir. */
    private void renderChatChoices(Player player, DialogSession session) {
        List<Choice> choices = session.node().choices();
        for (int i = 0; i < choices.size(); i++) {
            player.sendMessage(mini.deserialize("<yellow><click:run_command:'/dialog sec "
                    + i + "'>[" + (i + 1) + "] " + choices.get(i).text() + "</click></yellow>"));
        }
    }

    /**
     * Yazma sesi her karakterde degil, birkac adimda bir calinir: her karakterde
     * calmak kulakta tirmalayan bir gurultuye donusur.
     */
    private void typeSound(Player player) {
        if (ctx.plugin().getServer().getCurrentTick() % 6 != 0) return;
        Sounds.parse("block.stone.hit").ifPresent(sound ->
                player.playSound(player.getLocation(), sound, 0.25f, 1.8f));
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
