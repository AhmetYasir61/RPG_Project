package net.aethel.core.modules.npc;

import net.aethel.core.api.NpcService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * NPC yerlestirme komutu. Panelden bir NPC TANIMLANIR; dunyadaki yerini ise
 * ancak orada durarak vermek anlamlidir, bu yuzden konum islemleri oyun icidir.
 *
 * Konum daima komutu yazanin BULUNDUGU yerdir: koordinat yazdirmak, yanlis
 * sayiyla NPC'yi kayaya gommenin en kolay yoludur.
 */
@Command(value = "npc", permission = "aethel.admin.npc", playerOnly = true,
        descriptionKey = "npc.command-description")
public final class NpcCommand {

    private final CoreContext ctx;
    private final NpcModule npcs;

    public NpcCommand(CoreContext ctx, NpcModule npcs) {
        this.ctx = ctx;
        this.npcs = npcs;
    }

    @Command("")
    public void list(CommandSender sender) {
        Player player = (Player) sender;
        var all = npcs.all();
        ctx.lang().send(player, "npc.header", LangService.of("count", all.size()));
        all.forEach(npc -> ctx.lang().send(player, "npc.entry",
                LangService.of("id", npc.id()),
                LangService.of("world", npc.location().getWorld().getName()),
                LangService.of("x", (int) npc.location().getX()),
                LangService.of("y", (int) npc.location().getY()),
                LangService.of("z", (int) npc.location().getZ())));
    }

    /** Durdugun yere, baktigin yone bakan bir NPC koyar. */
    @Command("koy")
    public void place(CommandSender sender, @Arg(value = "id", suggests = "npc") String id,
                      @Arg(value = "ad", optional = true, greedy = true) String displayName) {
        Player player = (Player) sender;
        if (npcs.npc(id).isPresent()) {
            ctx.lang().send(player, "npc.exists", LangService.of("id", id));
            return;
        }
        String name = displayName == null || displayName.isBlank() ? id : displayName;
        NpcService.Npc npc = npcs.create(id, player.getLocation(), name);
        npcs.refresh(player);
        ctx.lang().send(player, "npc.placed", LangService.of("id", npc.id()),
                LangService.of("x", (int) npc.location().getX()),
                LangService.of("y", (int) npc.location().getY()),
                LangService.of("z", (int) npc.location().getZ()));
    }

    /** NPC'yi kaldirir. Tanim panelde kalir; yalnizca dunyadaki ornek silinir. */
    @Command("sil")
    public void remove(CommandSender sender, @Arg(value = "id", suggests = "npc") String id) {
        Player player = (Player) sender;
        if (npcs.npc(id).isEmpty()) {
            ctx.lang().send(player, "npc.missing", LangService.of("id", id));
            return;
        }
        npcs.remove(id);
        ctx.lang().send(player, "npc.removed", LangService.of("id", id));
    }

    /** Var olan NPC'yi durdugun yere tasir; silip yeniden kurmak gerekmez. */
    @Command("tasi")
    public void move(CommandSender sender, @Arg(value = "id", suggests = "npc") String id) {
        Player player = (Player) sender;
        var existing = npcs.npc(id);
        if (existing.isEmpty()) {
            ctx.lang().send(player, "npc.missing", LangService.of("id", id));
            return;
        }
        String name = existing.get().displayName();
        npcs.remove(id);
        npcs.create(id, player.getLocation(), name);
        npcs.refresh(player);
        ctx.lang().send(player, "npc.moved", LangService.of("id", id));
    }

    /**
     * NPC'yi yeniden gonderir. Istemci tarafinda kaybolan (chunk yeniden
     * yuklenmesi, boyut degisimi) bir NPC'yi geri getirmenin hizli yolu.
     */
    @Command("yenile")
    public void refresh(CommandSender sender) {
        Player player = (Player) sender;
        npcs.refresh(player);
        ctx.lang().send(player, "npc.refreshed", LangService.of("count", npcs.all().size()));
    }
}
