package net.aethel.core.modules.socket;

import net.aethel.core.api.CustomItem;
import net.aethel.core.api.ItemService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Soket komutlari. Tas takmak bir OYUNCU eylemidir (elindeki silaha takarsin),
 * bu yuzden komut oyuncuya baglidir ve hedef daima ELDEKI item'dir.
 */
@Command(value = "socket", aliases = {"soket"}, playerOnly = true,
        descriptionKey = "socket.command-description")
public final class SocketCommand {

    private final CoreContext ctx;
    private final SocketModule sockets;

    public SocketCommand(CoreContext ctx, SocketModule sockets) {
        this.ctx = ctx;
        this.sockets = sockets;
    }

    /** Elindeki silahin soket durumu. */
    @Command("")
    public void info(CommandSender sender) {
        Player player = (Player) sender;
        ItemStack held = player.getInventory().getItemInMainHand();

        var definition = sockets.definition(held).filter(CustomItem::socketable);
        if (definition.isEmpty()) {
            ctx.lang().send(player, "socket.not-socketable");
            return;
        }
        CustomItem item = definition.get();
        ctx.lang().send(player, "socket.info",
                LangService.of("item", item.fullId()),
                LangService.of("stone", sockets.socketedStone(held).orElse("-")),
                LangService.of("kills", sockets.kills(held)),
                LangService.of("stage", sockets.stage(held)),
                LangService.of("last", item.socketing().lastStage()));
    }

    /** Tanimli taslari listeler. */
    @Command("taslar")
    public void list(CommandSender sender) {
        var all = sockets.stones();
        ctx.lang().send(sender, "socket.stone-header", LangService.of("count", all.size()));
        all.forEach(stone -> ctx.lang().send(sender, "socket.stone-entry",
                LangService.of("id", stone.fullId()),
                LangService.of("tint", stone.tint())));
    }

    /** Elindeki silaha tas takar. */
    @Command("tak")
    public void insert(CommandSender sender, @Arg(value = "tas", suggests = "stone", identifier = true) String stoneId) {
        Player player = (Player) sender;
        ItemStack held = player.getInventory().getItemInMainHand();
        String fullId = stoneId.contains(":") ? stoneId : "aethel:" + stoneId;

        if (sockets.stone(fullId).isEmpty()) {
            ctx.lang().send(player, "socket.unknown-stone", LangService.of("id", fullId));
            return;
        }
        if (!sockets.socket(held, fullId)) {
            ctx.lang().send(player, sockets.socketedStone(held).isPresent()
                    ? "socket.already-filled" : "socket.not-socketable");
            return;
        }
        ctx.lang().send(player, "socket.inserted", LangService.of("id", fullId));
    }

    /** Tasi soker; ilerleme tasa ait oldugu icin sayac sifirlanir. */
    @Command("sok")
    public void remove(CommandSender sender) {
        Player player = (Player) sender;
        ItemStack held = player.getInventory().getItemInMainHand();

        ctx.lang().send(player, sockets.unsocket(held)
                ? "socket.removed" : "socket.nothing-to-remove");
    }

    /**
     * Oldurme sayacini elle ayarlar. Evrimi bastan sona test etmek icin:
     * asamayi gormek adina yuzlerce mob oldurmek gerekmemeli.
     */
    @Command(value = "sayac", permission = "aethel.admin.items")
    public void setKills(CommandSender sender, @Arg("adet") int kills) {
        Player player = (Player) sender;
        ItemStack held = player.getInventory().getItemInMainHand();

        if (sockets.definition(held).filter(CustomItem::socketable).isEmpty()) {
            ctx.lang().send(player, "socket.not-socketable");
            return;
        }
        new SocketData(ctx.plugin()).kills(held, kills);
        sockets.refresh(held);
        ctx.lang().send(player, "socket.counter-set",
                LangService.of("kills", kills), LangService.of("stage", sockets.stage(held)));
    }

    /** Tasin kendisini item olarak verir (vitrinde gorunmeyen tur). */
    @Command(value = "ver", permission = "aethel.admin.items")
    public void give(CommandSender sender, @Arg(value = "tas", suggests = "stone", identifier = true) String stoneId) {
        Player player = (Player) sender;
        String fullId = stoneId.contains(":") ? stoneId : "aethel:" + stoneId;

        ctx.services().optional(ItemService.class)
                .flatMap(items -> items.create(fullId))
                .ifPresentOrElse(stack -> {
                    player.getInventory().addItem(stack);
                    ctx.lang().send(player, "socket.stone-given", LangService.of("id", fullId));
                }, () -> ctx.lang().send(player, "socket.unknown-stone",
                        LangService.of("id", fullId)));
    }
}
