package net.aethel.core.modules.rpg;

import net.aethel.core.api.StatType;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Oyuncu RPG komutlari: seviye ozeti, stat dagitimi ve yetenek agaci.
 * Yonetim tarafi panelden yapilir, bunlar oyuncunun kendi komutlaridir.
 */
@Command(value = "rpg", aliases = {"karakter"}, playerOnly = true,
        descriptionKey = "rpg.command-description")
public final class RpgCommand {

    private final CoreContext ctx;
    private final RpgModule rpg;

    public RpgCommand(CoreContext ctx, RpgModule rpg) {
        this.ctx = ctx;
        this.rpg = rpg;
    }

    @Command("")
    public void summary(CommandSender sender) {
        Player player = (Player) sender;
        var uuid = player.getUniqueId();
        ctx.lang().send(player, "rpg.header",
                LangService.of("level", rpg.level(uuid)),
                LangService.of("xp", String.format("%.0f", rpg.experience(uuid))),
                LangService.of("points", rpg.availablePoints(uuid)));

        rpg.effectiveStats(uuid).forEach((type, value) -> ctx.lang().send(player, "rpg.stat-line",
                LangService.of("stat", type.turkishName()),
                LangService.of("value", value)));
    }

    /** Bir stata puan harcar: /rpg puan guc */
    @Command("puan")
    public void spend(CommandSender sender, @Arg("stat") String statName) {
        Player player = (Player) sender;
        StatType type = parse(statName);
        if (type == null) {
            ctx.lang().send(player, "rpg.unknown-stat", LangService.of("stat", statName));
            return;
        }
        boolean ok = rpg.spendPoint(player, type);
        ctx.lang().send(player, ok ? "rpg.point-spent" : "rpg.no-points",
                LangService.of("stat", type.turkishName()));
    }

    /** Yetenek agacindaki bir dugumu acar. */
    @Command("ac")
    public void unlock(CommandSender sender, @Arg("dugum") String nodeId) {
        rpg.unlockNode((Player) sender, nodeId);
    }

    /** Acilabilir dugumleri listeler. */
    @Command("agac")
    public void tree(CommandSender sender) {
        Player player = (Player) sender;
        var unlocked = rpg.unlockedNodes(player.getUniqueId());
        ctx.lang().send(player, "rpg.tree-header");
        rpg.tree().nodes().forEach(node -> ctx.lang().send(player,
                unlocked.contains(node.id()) ? "rpg.tree-unlocked" : "rpg.tree-locked",
                LangService.of("node", node.displayName()),
                LangService.of("id", node.id()),
                LangService.of("cost", node.cost())));
    }

    /** Turkce ve Ingilizce stat adlarini kabul eder. */
    private StatType parse(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        for (StatType type : StatType.values()) {
            if (type.turkishName().equals(normalized)
                    || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
