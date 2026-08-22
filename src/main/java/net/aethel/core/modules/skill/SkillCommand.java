package net.aethel.core.modules.skill;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Yetenek test komutu. Yetenekler tamamen parcacik tabanlidir; bir efektin
 * gercekte nasil gorundugunu ancak oyunda calistirinca anlarsin.
 */
@Command(value = "skill", aliases = {"yetenek"}, permission = "aethel.admin.skills",
        playerOnly = true, descriptionKey = "skill.command-description")
public final class SkillCommand {

    private final CoreContext ctx;
    private final SkillModule skills;

    public SkillCommand(CoreContext ctx, SkillModule skills) {
        this.ctx = ctx;
        this.skills = skills;
    }

    @Command("")
    public void list(CommandSender sender) {
        Player player = (Player) sender;
        var all = skills.all();
        ctx.lang().send(player, "skill.header", LangService.of("count", all.size()));
        all.forEach(definition -> ctx.lang().send(player, "skill.entry",
                LangService.of("id", definition.id()),
                LangService.of("trigger", definition.trigger().name()),
                LangService.of("cooldown", definition.cooldownSeconds())));
    }

    /**
     * Yetenegi SENIN uzerinden calistirir. Baktigin canli varsa hedef odur;
     * yoksa hedefsiz calisir. Soguma test sirasinda da gecerlidir: sogumayi
     * atlayan bir test, sogumasi bozuk bir yetenegi saglam gosterirdi.
     */
    @Command("test")
    public void cast(CommandSender sender, @Arg(value = "id", suggests = "skill", identifier = true) String id) {
        Player player = (Player) sender;
        if (skills.definition(id).isEmpty()) {
            ctx.lang().send(player, "skill.missing", LangService.of("id", id));
            return;
        }
        if (skills.isOnCooldown(player, id)) {
            ctx.lang().send(player, "skill.cooldown",
                    LangService.of("seconds", skills.cooldownRemaining(player, id) / 1000));
            return;
        }
        LivingEntity target = player.getTargetEntity(30) instanceof LivingEntity living
                ? living : null;
        boolean cast = target == null
                ? skills.cast(player, id) : skills.cast(player, id, target);

        ctx.lang().send(player, cast ? "skill.cast" : "skill.cast-failed",
                LangService.of("id", id),
                LangService.of("target", target == null ? "-" : target.getName()));
    }

    @Command("yenile")
    public void reload(CommandSender sender) {
        ctx.lang().send(sender, "skill.reloaded", LangService.of("count", skills.reload()));
    }
}
