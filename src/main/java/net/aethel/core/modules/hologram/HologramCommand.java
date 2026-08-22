package net.aethel.core.modules.hologram;

import net.aethel.core.api.Hologram;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hologram yerlestirme komutu. Hologramlar paket tabanlidir: sunucuda entity
 * olusmaz, her satir yalnizca ilgili oyuncunun istemcisinde cizilir.
 *
 * Satirlar burada TUTULUR cunku Hologram arayuzu yalnizca "tum satirlari degistir"
 * diyebilir; tek satir eklemek icin onceki satirlarin bilinmesi gerekir.
 */
@Command(value = "hologram", aliases = {"holo"}, permission = "aethel.admin.hologram",
        playerOnly = true, descriptionKey = "hologram.command-description")
public final class HologramCommand {

    private final CoreContext ctx;
    private final HologramModule holograms;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<String, List<String>> text = new HashMap<>();

    public HologramCommand(CoreContext ctx, HologramModule holograms) {
        this.ctx = ctx;
        this.holograms = holograms;
    }

    @Command("")
    public void list(CommandSender sender) {
        Player player = (Player) sender;
        var all = holograms.all();
        ctx.lang().send(player, "hologram.header", LangService.of("count", all.size()));
        all.forEach(hologram -> ctx.lang().send(player, "hologram.entry",
                LangService.of("id", hologram.id()),
                LangService.of("lines", text.getOrDefault(hologram.id(), List.of()).size()),
                LangService.of("x", (int) hologram.location().getX()),
                LangService.of("y", (int) hologram.location().getY()),
                LangService.of("z", (int) hologram.location().getZ())));
    }

    /** Durdugun yerin GOZ hizasinda bir hologram olusturur. */
    @Command("olustur")
    public void create(CommandSender sender, @Arg("id") String id,
                       @Arg(value = "metin", greedy = true) String line) {
        Player player = (Player) sender;
        if (holograms.get(id).isPresent()) {
            ctx.lang().send(player, "hologram.exists", LangService.of("id", id));
            return;
        }
        Hologram hologram = holograms.create(id, player.getEyeLocation());
        text.put(id, new ArrayList<>(List.of(line)));
        apply(hologram, id);
        ctx.lang().send(player, "hologram.created", LangService.of("id", id));
    }

    /** Var olan hologramin altina bir satir daha ekler. */
    @Command("satir")
    public void addLine(CommandSender sender, @Arg("id") String id,
                        @Arg(value = "metin", greedy = true) String line) {
        Player player = (Player) sender;
        var hologram = holograms.get(id);
        if (hologram.isEmpty()) {
            ctx.lang().send(player, "hologram.missing", LangService.of("id", id));
            return;
        }
        List<String> lines = text.computeIfAbsent(id, key -> new ArrayList<>());
        lines.add(line);
        apply(hologram.get(), id);
        ctx.lang().send(player, "hologram.line-added",
                LangService.of("id", id), LangService.of("count", lines.size()));
    }

    /** Son satiri geri alir; yanlis yazilan bir satiri silip bastan kurmaya gerek yok. */
    @Command("geri")
    public void removeLine(CommandSender sender, @Arg("id") String id) {
        Player player = (Player) sender;
        var hologram = holograms.get(id);
        List<String> lines = text.get(id);
        if (hologram.isEmpty() || lines == null || lines.isEmpty()) {
            ctx.lang().send(player, "hologram.missing", LangService.of("id", id));
            return;
        }
        lines.remove(lines.size() - 1);
        apply(hologram.get(), id);
        ctx.lang().send(player, "hologram.line-removed",
                LangService.of("id", id), LangService.of("count", lines.size()));
    }

    @Command("tasi")
    public void move(CommandSender sender, @Arg("id") String id) {
        Player player = (Player) sender;
        var hologram = holograms.get(id);
        if (hologram.isEmpty()) {
            ctx.lang().send(player, "hologram.missing", LangService.of("id", id));
            return;
        }
        hologram.get().teleport(player.getEyeLocation());
        ctx.lang().send(player, "hologram.moved", LangService.of("id", id));
    }

    @Command("sil")
    public void remove(CommandSender sender, @Arg("id") String id) {
        Player player = (Player) sender;
        if (holograms.get(id).isEmpty()) {
            ctx.lang().send(player, "hologram.missing", LangService.of("id", id));
            return;
        }
        holograms.remove(id);
        text.remove(id);
        ctx.lang().send(player, "hologram.removed", LangService.of("id", id));
    }

    /** On saniye yasayan bir hologram: bir metni yerinde gormek icin. */
    @Command("test")
    public void temporary(CommandSender sender, @Arg(value = "metin", greedy = true) String line) {
        Player player = (Player) sender;
        Hologram hologram = holograms.temporary(player.getEyeLocation(), 200L);
        hologram.lines(List.of(mini.deserialize(line)));
        hologram.show(player);
        ctx.lang().send(player, "hologram.temporary");
    }

    private void apply(Hologram hologram, String id) {
        List<Component> rendered = new ArrayList<>();
        text.getOrDefault(id, List.of()).forEach(line -> rendered.add(mini.deserialize(line)));
        hologram.lines(rendered);
    }
}
