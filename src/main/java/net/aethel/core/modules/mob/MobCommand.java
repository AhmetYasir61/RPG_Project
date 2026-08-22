package net.aethel.core.modules.mob;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Mob spawn ve denetim komutu. Tanimlar panelden yazilir; bir mobun gercekten
 * dogru gorunup dogru vurdugunu ancak onu yanina cikarip gorerek anlarsin.
 */
@Command(value = "mob", permission = "aethel.admin.mobs", playerOnly = true,
        descriptionKey = "mob.command-description")
public final class MobCommand {

    /** Tek komutla cikarilabilecek azami mob. Kazara yazilan bir sifir sunucuyu bogar. */
    private static final int MAX_SPAWN = 50;

    private final CoreContext ctx;
    private final MobModule mobs;

    public MobCommand(CoreContext ctx, MobModule mobs) {
        this.ctx = ctx;
        this.mobs = mobs;
    }

    @Command("")
    public void list(CommandSender sender) {
        Player player = (Player) sender;
        var all = mobs.all();
        ctx.lang().send(player, "mob.header", LangService.of("count", all.size()));
        all.forEach(definition -> ctx.lang().send(player, "mob.entry",
                LangService.of("id", definition.id()),
                LangService.of("type", definition.baseType()),
                LangService.of("health", (int) definition.health()),
                LangService.of("tier", definition.tier())));
    }

    /** Baktigin yere, yoksa durdugun yere mob cikarir. */
    @Command("spawn")
    public void spawn(CommandSender sender, @Arg(value = "id", suggests = "mob", identifier = true) String id,
                      @Arg(value = "adet", optional = true) int amount) {
        Player player = (Player) sender;
        if (mobs.definition(id).isEmpty()) {
            ctx.lang().send(player, "mob.missing", LangService.of("id", id));
            return;
        }
        int count = Math.max(1, Math.min(MAX_SPAWN, amount == 0 ? 1 : amount));
        var target = player.getTargetBlockExact(60);
        var location = target == null
                ? player.getLocation() : target.getLocation().add(0.5, 1, 0.5);

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (mobs.spawn(id, location).isPresent()) spawned++;
        }
        ctx.lang().send(player, "mob.spawned", LangService.of("id", id),
                LangService.of("count", spawned));
    }

    /**
     * Baktigin mobun hangi tanimdan geldigini soyler. "Bu mob neden bu kadar
     * vuruyor" sorusunun cevabi genelde beklenenden baska bir tanim olmasidir.
     */
    @Command("bilgi")
    public void info(CommandSender sender) {
        Player player = (Player) sender;
        Entity target = player.getTargetEntity(30);
        if (target == null) {
            ctx.lang().send(player, "mob.no-target");
            return;
        }
        mobs.resolve(target).ifPresentOrElse(definition ->
                ctx.lang().send(player, "mob.info",
                        LangService.of("id", definition.id()),
                        LangService.of("type", definition.baseType()),
                        LangService.of("health", (int) definition.health()),
                        LangService.of("damage", definition.damage()),
                        LangService.of("tier", mobs.tierOf(target))),
                () -> ctx.lang().send(player, "mob.vanilla"));
    }

    /**
     * Yakindaki CUSTOM moblari siler. Vanilla canlilara ve oyunculara dokunmaz:
     * test icin cikarilan moblari toplarken kimsenin atini oldurmemeli.
     */
    @Command("temizle")
    public void clear(CommandSender sender, @Arg("yaricap") int radius) {
        Player player = (Player) sender;
        int range = Math.max(1, Math.min(200, radius));
        int removed = 0;
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (mobs.isCustom(entity)) {
                entity.remove();
                removed++;
            }
        }
        ctx.lang().send(player, "mob.cleared", LangService.of("count", removed),
                LangService.of("radius", range));
    }

    /** Tanimlari diskten yeniden okur; panelden yapilan degisiklik icin. */
    @Command("yenile")
    public void reload(CommandSender sender) {
        ctx.lang().send(sender, "mob.reloaded", LangService.of("count", mobs.reload()));
    }
}
