package net.aethel.core.modules.travel;

import net.aethel.core.api.TravelService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Oyuncu seyahat komutlari. Serbest teleport yoktur: her komut ya parsomen harcar
 * ya soguma bekletir, ve kesfedilmemis noktaya isinlanilamaz.
 */
@Command(value = "waypoint", aliases = {"wp", "seyahat"}, playerOnly = true,
        descriptionKey = "travel.command-description")
public final class TravelCommand {

    private final CoreContext ctx;
    private final TravelModule travel;

    public TravelCommand(CoreContext ctx, TravelModule travel) {
        this.ctx = ctx;
        this.travel = travel;
    }

    /** Kesfedilmis noktalari listeler. */
    @Command("")
    public void list(CommandSender sender) {
        Player player = (Player) sender;
        travel.discovered(player.getUniqueId()).thenAccept(known ->
                ctx.scheduler().sync("travel", () -> {
                    ctx.lang().send(player, "travel.header");
                    travel.waypoints().forEach(waypoint -> ctx.lang().send(player,
                            known.contains(waypoint.id())
                                    ? "travel.entry-known" : "travel.entry-unknown",
                            LangService.of("id", waypoint.id()),
                            LangService.of("name", waypoint.displayName())));
                }));
    }

    /** Parsomen ile isinlanma baslatir. */
    @Command(value = "git", feature = "travel.scroll")
    public void travelTo(CommandSender sender, @Arg(value = "nokta", suggests = "waypoint", identifier = true) String waypointId) {
        Player player = (Player) sender;
        TravelService.CastResult result = travel.beginScrollTeleport(player, waypointId);
        ctx.lang().send(player, switch (result) {
            case STARTED -> "travel.cast-started";
            case NO_SCROLL -> "travel.no-scroll";
            case NOT_DISCOVERED -> "travel.not-discovered";
            case IN_COMBAT -> "travel.in-combat";
            case ON_COOLDOWN -> "travel.on-cooldown";
            case BLOCKED_REGION -> "travel.blocked";
        }, LangService.of("waypoint", waypointId));
    }

    /** Hearthstone kullanimi; parsomen gerektirmez ama uzun soguma vardir. */
    @Command(value = "ocak", feature = "travel.hearthstone")
    public void hearthstone(CommandSender sender) {
        Player player = (Player) sender;
        TravelService.CastResult result = travel.useHearthstone(player);
        if (result == TravelService.CastResult.ON_COOLDOWN) {
            ctx.lang().send(player, "travel.hearth-cooldown", LangService.of("seconds",
                    travel.hearthstoneCooldown(player.getUniqueId()) / 1000));
            return;
        }
        ctx.lang().send(player, switch (result) {
            case STARTED -> "travel.cast-started";
            case NOT_DISCOVERED -> "travel.no-bind";
            case IN_COMBAT -> "travel.in-combat";
            default -> "travel.blocked";
        }, LangService.of("waypoint", "ocak"));
    }

    /** Hearthstone bag noktasini bulunulan yere tasir. */
    @Command(value = "bagla", feature = "travel.hearthstone")
    public void bind(CommandSender sender) {
        Player player = (Player) sender;
        ctx.lang().send(player, travel.bindHearthstone(player)
                ? "travel.bound" : "travel.bind-failed");
    }
}
