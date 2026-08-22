package net.aethel.core.modules.quest;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Oyuncu gorev komutlari: aktif gorevler, ilerleme ve birakma. */
@Command(value = "gorev", aliases = {"quest"}, playerOnly = true,
        descriptionKey = "quest.command-description")
public final class QuestCommand {

    private final CoreContext ctx;
    private final QuestModule quests;

    public QuestCommand(CoreContext ctx, QuestModule quests) {
        this.ctx = ctx;
        this.quests = quests;
    }

    @Command("")
    public void active(CommandSender sender) {
        Player player = (Player) sender;
        var active = quests.activeQuests(player.getUniqueId());
        if (active.isEmpty()) {
            ctx.lang().send(player, "quest.none");
            return;
        }
        ctx.lang().send(player, "quest.header");
        active.forEach((questId, progress) -> quests.quest(questId).ifPresent(quest -> {
            ctx.lang().send(player, "quest.entry",
                    LangService.of("quest", quest.displayName()));
            for (int index = 0; index < quest.objectives().size(); index++) {
                var objective = quest.objectives().get(index);
                ctx.lang().send(player, "quest.objective",
                        LangService.of("description", objective.description()),
                        LangService.of("current", progress.getOrDefault(index, 0)),
                        LangService.of("total", objective.amount()));
            }
        }));
    }

    @Command("birak")
    public void abandon(CommandSender sender, @Arg(value = "gorev", suggests = "quest") String questId) {
        Player player = (Player) sender;
        boolean ok = quests.abandon(player, questId);
        ctx.lang().send(player, ok ? "quest.abandoned" : "quest.not-active",
                LangService.of("quest", questId));
    }
}
