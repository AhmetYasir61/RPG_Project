package net.aethel.core.modules.scoreboard;

import net.aethel.core.api.PermissionService;
import net.aethel.core.api.PlaceholderService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tab listesi ve yan tablo modulu. Her oyuncunun KENDI tablosu vardir; boylece
 * satirlar kisiye ozel placeholder tasiyabilir ve dili oyuncuya gore degisir.
 */
@ModuleInfo(id = "scoreboard", name = "Tab ve Scoreboard",
        softDepends = {"placeholder", "permissions"})
public final class ScoreboardModule implements Module, Listener {

    /** Satir basina benzersiz takim adi; ayni metin iki satirda cikabilsin diye. */
    private static final String LINE_TEAM_PREFIX = "aethel_line_";

    private final ScoreboardSettings settings = new ScoreboardSettings();
    private final List<BoardLayout> layouts = new ArrayList<>();
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final MiniMessage mini = MiniMessage.miniMessage();
    private ConfigFile config;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        ctx.config().open("modules/scoreboard.yml", 1, settings, ConfigMigration.NONE);
        this.config = ctx.config().open("scoreboard.yml", 1, null, ConfigMigration.NONE);

        var features = ctx.services().get(net.aethel.core.api.FeatureService.class);
        features.declare("scoreboard.sidebar", true, "Yan tablo (scoreboard)");
        features.declare("scoreboard.tab", true, "Tab listesi basligi ve alt bilgisi");
        features.declare("scoreboard.tab-prefix", true, "Tab listesinde rutbe on eki");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        loadLayouts();
        ctx.listener("scoreboard.sidebar", this);
        ctx.scheduler().repeating("scoreboard", 20L,
                Math.max(5, settings.updateTicks), this::tick);
        ctx.plugin().getServer().getOnlinePlayers().forEach(this::attach);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        // Oyuncular ana tabloya dondurulur; aksi halde plugin kapaninca ekranda
        // guncellenmeyen olu bir tablo asili kalir.
        boards.keySet().forEach(uuid -> {
            Player player = ctx.plugin().getServer().getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(ctx.plugin().getServer().getScoreboardManager().getMainScoreboard());
            }
        });
        boards.clear();
        layouts.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        loadLayouts();
    }

    private void loadLayouts() {
        layouts.clear();
        ConfigurationSection root = config.yaml().getConfigurationSection("boards");
        if (root == null) {
            writeExample();
            root = config.yaml().getConfigurationSection("boards");
            if (root == null) return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            layouts.add(new BoardLayout(id,
                    section.getString("condition", ""),
                    section.getInt("priority", 0),
                    section.getString("title", "<gold>Sunucu</gold>"),
                    section.getStringList("lines"),
                    String.join("\n", section.getStringList("tab-header")),
                    String.join("\n", section.getStringList("tab-footer"))));
        }
        layouts.sort(Comparator.comparingInt(BoardLayout::priority).reversed());
        ctx.logger().info("Scoreboard duzeni: " + layouts.size());
    }

    private void writeExample() {
        var yaml = config.yaml();
        String base = "boards.varsayilan.";
        yaml.set(base + "priority", 0);
        yaml.set(base + "title", "<gradient:#f0c040:#e08020><bold>AETHEL</bold></gradient>");
        yaml.set(base + "lines", List.of(
                "<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "<gray>Oyuncu</gray> <white>%aethel_player_name%</white>",
                "<gray>Seviye</gray> <white>%aethel_rpg_level%</white>",
                "<gray>Altin</gray> <gold>%aethel_economy_balance%</gold>",
                "",
                "<gray>Dunya</gray> <white>%aethel_player_world%</white>",
                "<gray>Cevrimici</gray> <white>%aethel_server_online%</white>",
                "<dark_gray>▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        yaml.set(base + "tab-header", List.of(
                "", "<gradient:#f0c040:#e08020><bold>AETHEL</bold></gradient>", ""));
        yaml.set(base + "tab-footer", List.of(
                "", "<gray>Cevrimici:</gray> <white>%aethel_server_online%</white>"
                        + " <dark_gray>|</dark_gray> <gray>TPS:</gray> <white>%aethel_server_tps%</white>", ""));
        config.save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        attach(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer().getUniqueId());
    }

    /** Oyuncuya kendi tablosunu verir. */
    private void attach(Player player) {
        Scoreboard board = ctx.plugin().getServer().getScoreboardManager().getNewScoreboard();
        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
    }

    private void tick() {
        for (Player player : ctx.plugin().getServer().getOnlinePlayers()) {
            BoardLayout layout = select(player);
            if (layout == null) continue;
            if (ctx.feature("scoreboard.sidebar") && settings.sidebarEnabled) {
                renderSidebar(player, layout);
            }
            if (ctx.feature("scoreboard.tab") && settings.tabEnabled) {
                renderTab(player, layout);
            }
            if (ctx.feature("scoreboard.tab-prefix") && settings.tabPrefix) {
                renderTabName(player);
            }
        }
    }

    private BoardLayout select(Player player) {
        for (BoardLayout layout : layouts) {
            if (layout.condition().isBlank() || player.hasPermission(layout.condition())) {
                return layout;
            }
        }
        return null;
    }

    /**
     * Yan tablo. Satirlar takim on eki olarak yazilir: skor girdisinin kendisi
     * benzersiz olmak zorundadir, ayni metin iki satirda gorunemez. Takim
     * kullanmak bu sinirlamayi kaldirir ve satir uzunlugunu da serbestlestirir.
     */
    private void renderSidebar(Player player, BoardLayout layout) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;

        Objective objective = board.getObjective("aethel");
        if (objective == null) {
            objective = board.registerNewObjective("aethel", Criteria.DUMMY,
                    render(player, layout.title()));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            objective.displayName(render(player, layout.title()));
        }

        List<String> lines = layout.lines();
        for (int i = 0; i < lines.size(); i++) {
            int score = lines.size() - i;
            String entry = invisibleEntry(i);
            String teamName = LINE_TEAM_PREFIX + i;

            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
                team.addEntry(entry);
            }
            team.prefix(render(player, lines.get(i)));
            objective.getScore(entry).setScore(score);
        }
    }

    /** Gorunmez benzersiz girdi: renk kodlari ekranda hicbir sey cizmez. */
    private String invisibleEntry(int index) {
        char[] codes = "0123456789abcdef".toCharArray();
        return "§" + codes[index % codes.length] + "§r";
    }

    private void renderTab(Player player, BoardLayout layout) {
        player.sendPlayerListHeaderAndFooter(
                render(player, layout.tabHeader()),
                render(player, layout.tabFooter()));
    }

    /** Tab listesindeki ad: rutbe on eki + oyuncu adi. */
    private void renderTabName(Player player) {
        String prefix = ctx.services().optional(PermissionService.class)
                .map(permissions -> permissions.prefix(player.getUniqueId()))
                .orElse("");
        String suffix = ctx.services().optional(PermissionService.class)
                .map(permissions -> permissions.suffix(player.getUniqueId()))
                .orElse("");
        player.playerListName(render(player, prefix + player.getName() + suffix));
    }

    /** Placeholder cozumu + MiniMessage; dil oyuncuya gore degisir. */
    private Component render(Player player, String text) {
        String resolved = ctx.services().optional(PlaceholderService.class)
                .map(service -> service.apply(player, text))
                .orElse(text);
        return mini.deserialize(resolved);
    }
}
