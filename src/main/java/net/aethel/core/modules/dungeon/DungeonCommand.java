package net.aethel.core.modules.dungeon;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Dungeon komutlari. Oyuncu tarafi giris/cikis; tasarim tarafi (oda export) yetkiye
 * baglidir ve tasarim dunyasinda calisir.
 */
@Command(value = "dungeon", aliases = {"zindan"}, playerOnly = true,
        feature = "dungeon.enabled", descriptionKey = "dungeon.command-description")
public final class DungeonCommand {

    private final CoreContext ctx;
    private final DungeonModule dungeons;

    public DungeonCommand(CoreContext ctx, DungeonModule dungeons) {
        this.ctx = ctx;
        this.dungeons = dungeons;
    }

    @Command("")
    public void list(CommandSender sender) {
        Player player = (Player) sender;
        ctx.lang().send(player, "dungeon.header");
        dungeons.definitions().forEach(definition -> ctx.lang().send(player, "dungeon.entry",
                LangService.of("dungeon", definition.displayName()),
                LangService.of("id", definition.id()),
                LangService.of("difficulty", definition.difficultyId())));
    }

    /** Yeni bir ornek acar ve partiyi iceri alir. */
    @Command("gir")
    public void enter(CommandSender sender, @Arg("dungeon") String dungeonId) {
        Player player = (Player) sender;
        dungeons.enter(player, dungeonId).ifPresentOrElse(
                instance -> { },
                () -> ctx.lang().send(player, "dungeon.enter-failed",
                        LangService.of("dungeon", dungeonId)));
    }

    /** Cikis: tek yonludur, ayni ornege geri donulemez. */
    @Command("cik")
    public void exit(CommandSender sender) {
        dungeons.exit((Player) sender);
    }

    /** Tasarim dunyasinda bulunulan chunk'i oda sablonu olarak kaydeder. */
    @Command(value = "kaydet", permission = "aethel.admin.dungeon")
    public void export(CommandSender sender, @Arg("oda") String roomId) {
        dungeons.exportRoom((Player) sender, roomId);
    }

    /** Bulunulan konuma dungeon girisi yerlestirir. */
    @Command(value = "giris", permission = "aethel.admin.dungeon")
    public void entrance(CommandSender sender, @Arg("dungeon") String dungeonId) {
        Player player = (Player) sender;
        if (dungeons.definition(dungeonId).isEmpty()) {
            ctx.lang().send(player, "dungeon.enter-failed", LangService.of("dungeon", dungeonId));
            return;
        }
        dungeons.entrances().place(dungeonId, player.getLocation().getBlock().getLocation());
        ctx.lang().send(player, "dungeon.entrance-placed", LangService.of("dungeon", dungeonId));
    }

    /** Yuklu oda sablonlarini ve kapi maskelerini listeler. */
    @Command(value = "odalar", permission = "aethel.admin.dungeon")
    public void rooms(CommandSender sender) {
        ctx.lang().send(sender, "dungeon.rooms-header");
        dungeons.rooms().values().forEach(room -> ctx.lang().send(sender, "dungeon.room-entry",
                LangService.of("room", room.id()),
                LangService.of("doors", Integer.toBinaryString(room.doorMask())),
                LangService.of("markers", room.markers().size())));
    }

    /** Acik ornekleri ve durumlarini gosterir. */
    @Command(value = "ornekler", permission = "aethel.admin.dungeon")
    public void instances(CommandSender sender) {
        ctx.lang().send(sender, "dungeon.instances-header");
        dungeons.instances().forEach(instance -> ctx.lang().send(sender, "dungeon.instance-entry",
                LangService.of("id", instance.id().toString().substring(0, 8)),
                LangService.of("dungeon", instance.definitionId()),
                LangService.of("state", instance.state().name()),
                LangService.of("players", instance.players().size())));
    }
}
