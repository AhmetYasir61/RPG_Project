package net.aethel.core.modules.region;

import net.aethel.core.api.Difficulty;
import net.aethel.core.api.RegionService;
import net.aethel.core.api.RegionShape;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bolge secim ve olusturma komutu. Bolgenin sekli dunyada durarak secilir; bir
 * bolgenin sinirlarini koordinat yazarak dogru vermek pratikte mumkun degildir.
 *
 * Secim OYUNCU BASINA tutulur ve sunucu kapaninca kaybolur: yarim kalmis bir
 * secimin diske yazilip aylar sonra karsina cikmasi istenmez.
 */
@Command(value = "region", aliases = {"bolge"}, permission = "aethel.admin.regions",
        playerOnly = true, descriptionKey = "region.command-description")
public final class RegionCommand {

    /** Bir oyuncunun yarim secimi. */
    private record Selection(Location first, Location second) {}

    private final CoreContext ctx;
    private final RegionModule regions;
    private final Map<UUID, Selection> selections = new HashMap<>();

    public RegionCommand(CoreContext ctx, RegionModule regions) {
        this.ctx = ctx;
        this.regions = regions;
    }

    @Command("")
    public void list(CommandSender sender) {
        Player player = (Player) sender;
        var all = regions.regions();
        ctx.lang().send(player, "region.header", LangService.of("count", all.size()));
        all.forEach(region -> ctx.lang().send(player, "region.entry",
                LangService.of("id", region.id()),
                LangService.of("world", region.world()),
                LangService.of("shape", region.shape().getClass().getSimpleName()),
                LangService.of("difficulty", region.difficultyId())));
    }

    /** Ilk koseyi durdugun bloga koyar. */
    @Command("pos1")
    public void first(CommandSender sender) {
        Player player = (Player) sender;
        Selection current = selections.get(player.getUniqueId());
        selections.put(player.getUniqueId(), new Selection(player.getLocation(),
                current == null ? null : current.second()));
        ctx.lang().send(player, "region.pos-set", LangService.of("corner", 1),
                LangService.of("x", player.getLocation().getBlockX()),
                LangService.of("y", player.getLocation().getBlockY()),
                LangService.of("z", player.getLocation().getBlockZ()));
    }

    @Command("pos2")
    public void second(CommandSender sender) {
        Player player = (Player) sender;
        Selection current = selections.get(player.getUniqueId());
        selections.put(player.getUniqueId(), new Selection(
                current == null ? null : current.first(), player.getLocation()));
        ctx.lang().send(player, "region.pos-set", LangService.of("corner", 2),
                LangService.of("x", player.getLocation().getBlockX()),
                LangService.of("y", player.getLocation().getBlockY()),
                LangService.of("z", player.getLocation().getBlockZ()));
    }

    /**
     * Secimden kutu bir bolge kurar. Iki kose farkli dunyalardaysa reddedilir:
     * boyle bir bolge her testte "icinde degilsin" der ve nedeni gorunmez.
     */
    @Command("olustur")
    public void create(CommandSender sender, @Arg(value = "id", suggests = "region") String id,
                       @Arg(value = "zorluk", optional = true, suggests = "difficulty") String difficultyId) {
        Player player = (Player) sender;
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.first() == null || selection.second() == null) {
            ctx.lang().send(player, "region.no-selection");
            return;
        }
        if (!selection.first().getWorld().equals(selection.second().getWorld())) {
            ctx.lang().send(player, "region.world-mismatch");
            return;
        }
        if (regions.region(id).isPresent()) {
            ctx.lang().send(player, "region.exists", LangService.of("id", id));
            return;
        }
        String difficulty = difficultyId == null || difficultyId.isBlank()
                ? "normal" : difficultyId;
        if (regions.difficulty(difficulty).isEmpty()) {
            ctx.lang().send(player, "region.unknown-difficulty",
                    LangService.of("id", difficulty));
            return;
        }
        Vector min = Vector.getMinimum(selection.first().toVector(), selection.second().toVector());
        Vector max = Vector.getMaximum(selection.first().toVector(), selection.second().toVector());

        regions.save(new RegionService.Region(id, selection.first().getWorld().getName(),
                new RegionShape.Cuboid(min, max), difficulty, 0,
                java.util.List.of(), java.util.List.of()));
        selections.remove(player.getUniqueId());

        ctx.lang().send(player, "region.created", LangService.of("id", id),
                LangService.of("difficulty", difficulty),
                LangService.of("volume", (long) ((max.getX() - min.getX() + 1)
                        * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1))));
    }

    @Command("sil")
    public void delete(CommandSender sender, @Arg(value = "id", suggests = "region") String id) {
        Player player = (Player) sender;
        if (regions.region(id).isEmpty()) {
            ctx.lang().send(player, "region.missing", LangService.of("id", id));
            return;
        }
        regions.delete(id);
        ctx.lang().send(player, "region.deleted", LangService.of("id", id));
    }

    /** Durdugun noktada hangi bolgeler ve hangi zorluk gecerli. */
    @Command("nerede")
    public void where(CommandSender sender) {
        Player player = (Player) sender;
        var here = regions.at(player.getLocation());
        Difficulty difficulty = regions.difficultyAt(player.getLocation());

        ctx.lang().send(player, "region.here-difficulty",
                LangService.of("name", difficulty.displayName()),
                LangService.of("percent", difficulty.percent()));
        if (here.isEmpty()) {
            ctx.lang().send(player, "region.here-none");
            return;
        }
        here.forEach(region -> ctx.lang().send(player, "region.here-entry",
                LangService.of("id", region.id()),
                LangService.of("priority", region.priority())));
    }

    /** Zorluk tanimlar ya da yuzdesini gunceller. */
    @Command("zorluk")
    public void difficulty(CommandSender sender, @Arg(value = "id", suggests = "difficulty") String id,
                           @Arg("yuzde") int percent,
                           @Arg(value = "ad", optional = true, greedy = true) String displayName) {
        Player player = (Player) sender;
        int clamped = Math.max(1, Math.min(1000, percent));
        String name = displayName == null || displayName.isBlank()
                ? regions.difficulty(id).map(Difficulty::displayName).orElse("<white>" + id)
                : displayName;

        regions.saveDifficulty(new Difficulty(id, name, clamped,
                regions.difficulty(id).map(Difficulty::icon).orElse(""),
                regions.difficulty(id).map(Difficulty::hardcore).orElse(false)));
        ctx.lang().send(player, "region.difficulty-saved",
                LangService.of("id", id), LangService.of("percent", clamped));
    }
}
