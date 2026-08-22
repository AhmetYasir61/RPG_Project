package net.aethel.core.modules.loot;

import net.aethel.core.api.LootService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Loot sandigi yerlestirme ve tehdit denetimi.
 *
 * Sandigin degeri cevredeki TEHDIT YOGUNLUGUNA gore yukselir; "buraya koydugum
 * sandik ne cikarir" sorusunun cevabi ancak o noktada olculebilir, bu yuzden
 * olcum komutu oyun icidir.
 */
@Command(value = "loot", permission = "aethel.admin.loot", playerOnly = true,
        descriptionKey = "loot.command-description")
public final class LootCommand {

    private final CoreContext ctx;
    private final LootModule loot;

    public LootCommand(CoreContext ctx, LootModule loot) {
        this.ctx = ctx;
        this.loot = loot;
    }

    @Command("")
    public void list(CommandSender sender) {
        Player player = (Player) sender;
        var chests = loot.chests();
        ctx.lang().send(player, "loot.header", LangService.of("count", chests.size()));
        chests.forEach(chest -> ctx.lang().send(player, "loot.entry",
                LangService.of("id", chest.id()),
                LangService.of("table", chest.tableId()),
                LangService.of("x", chest.location().getBlockX()),
                LangService.of("y", chest.location().getBlockY()),
                LangService.of("z", chest.location().getBlockZ())));
    }

    /**
     * Baktigin bloga bir loot sandigi baglar. Blok secimi bakisla yapilir: sandigin
     * hangi blok oldugunu koordinatla dogru vermek hataya cok acik.
     */
    @Command("sandik")
    public void place(CommandSender sender, @Arg(value = "id", suggests = "loot-chest") String id, @Arg(value = "tablo", suggests = "loot-table") String tableId,
                      @Arg(value = "yenilenme-sn", optional = true) int respawnSeconds) {
        Player player = (Player) sender;
        Block target = player.getTargetBlockExact(10);
        if (target == null) {
            ctx.lang().send(player, "loot.no-block");
            return;
        }
        long respawn = respawnSeconds <= 0 ? 900L : respawnSeconds;
        loot.registerChest(new LootService.LootChest(id, target.getLocation(), tableId, respawn));

        ctx.lang().send(player, "loot.chest-placed", LangService.of("id", id),
                LangService.of("table", tableId),
                LangService.of("x", target.getX()), LangService.of("y", target.getY()),
                LangService.of("z", target.getZ()));
    }

    @Command("sil")
    public void remove(CommandSender sender, @Arg(value = "id", suggests = "loot-chest") String id) {
        loot.removeChest(id);
        ctx.lang().send(sender, "loot.chest-removed", LangService.of("id", id));
    }

    /**
     * Durdugun noktadaki tehdit yogunlugunu ve bunun nadirlige carpanini gosterir.
     * Cok sayida zayif mob ile birkac guclu mob benzer bir skora ulasir.
     */
    @Command("tehdit")
    public void threat(CommandSender sender) {
        Player player = (Player) sender;
        ctx.lang().send(player, "loot.threat",
                LangService.of("threat", String.format("%.1f", loot.threatAt(player.getLocation()))),
                LangService.of("multiplier",
                        String.format("%.2f", loot.rarityMultiplier(player.getLocation()))));
    }

    /**
     * Tabloyu bulundugun noktada bir kez cevirir ve ciktiyi yazar. Sandigi acmadan,
     * dunyaya hicbir sey birakmadan tablonun gercekte ne verdigini gosterir.
     */
    @Command("test")
    public void roll(CommandSender sender, @Arg(value = "tablo", suggests = "loot-table") String tableId) {
        Player player = (Player) sender;
        var items = loot.roll(tableId, player.getLocation(), player);
        if (items.isEmpty()) {
            ctx.lang().send(player, "loot.empty-roll", LangService.of("table", tableId));
            return;
        }
        ctx.lang().send(player, "loot.roll-header",
                LangService.of("table", tableId), LangService.of("count", items.size()));
        items.forEach(item -> ctx.lang().send(player, "loot.roll-entry",
                LangService.of("item", item.getType().name()),
                LangService.of("amount", item.getAmount())));
    }
}
