package net.aethel.core.modules.profile;

import net.aethel.core.api.AdminPanelService;
import net.aethel.core.api.EconomyService;
import net.aethel.core.api.JobService;
import net.aethel.core.api.PanelMode;
import net.aethel.core.api.RpgService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /profil ve /profiles komutlari. Kendi profilini herkes gorebilir; baskasininkini
 * yalnizca yetkili gorur ve GUI modunda bu gorunum SALT OKUNURDUR.
 */
@Command(value = "profil", aliases = {"profile", "profiles"}, playerOnly = true,
        descriptionKey = "profile.command-description")
public final class ProfileCommand {

    private static final String INSPECT_PERMISSION = "aethel.profile.inspect";

    private final CoreContext ctx;

    public ProfileCommand(CoreContext ctx) {
        this.ctx = ctx;
    }

    /** Kendi profilin: WEB modunda tarayicida, GUI modunda oyun ici gosterilir. */
    @Command("")
    public void self(CommandSender sender) {
        Player player = (Player) sender;
        if (panelMode().isWeb()) {
            openWeb(player);
            return;
        }
        printSummary(player, player.getUniqueId(), player.getName());
    }

    /**
     * Baska bir oyuncunun profili. GUI modunda envantere mudahale SECENEGI YOKTUR:
     * oyun ici bir menude yanlis tiklama geri alinamaz ve denetim izi birakmaz.
     * Mudahale yalnizca WEB modunda, denetim kaydiyla birlikte mumkundur.
     */
    @Command("bak")
    public void inspect(CommandSender sender, @Arg("oyuncu") String targetName) {
        Player player = (Player) sender;
        if (!player.hasPermission(INSPECT_PERMISSION)) {
            ctx.lang().send(player, "command.no-permission");
            return;
        }
        ctx.services().get(net.aethel.core.api.ProfileService.class)
                .loadByName(targetName)
                .thenAccept(found -> ctx.scheduler().sync("profile", () -> {
                    if (found.isEmpty()) {
                        ctx.lang().send(player, "profile.not-found",
                                LangService.of("player", targetName));
                        return;
                    }
                    var profile = found.get();
                    if (panelMode().isWeb()) {
                        openWeb(player);
                        ctx.lang().send(player, "profile.web-inspect",
                                LangService.of("player", profile.name()));
                        return;
                    }
                    printSummary(player, profile.uuid(), profile.name());
                    ctx.lang().send(player, "profile.gui-readonly");
                }));
    }

    /** Ozet cikti: seviye, bakiye, meslek ve oynanis suresi. */
    private void printSummary(Player viewer, UUID target, String name) {
        ctx.lang().send(viewer, "profile.header", LangService.of("player", name));

        ctx.services().optional(RpgService.class).ifPresent(rpg ->
                ctx.lang().send(viewer, "profile.level",
                        LangService.of("level", rpg.level(target)),
                        LangService.of("points", rpg.availablePoints(target))));

        ctx.services().optional(EconomyService.class).ifPresent(economy ->
                ctx.lang().send(viewer, "profile.balance",
                        LangService.of("balance", economy.format(economy.balance(target)))));

        ctx.services().optional(JobService.class).ifPresent(jobs ->
                jobs.jobsOf(target).forEach((jobId, level) ->
                        ctx.lang().send(viewer, "profile.job",
                                LangService.of("job", jobId),
                                LangService.of("level", level))));

        ctx.services().optional(net.aethel.core.api.ProfileService.class)
                .flatMap(profiles -> profiles.cached(target))
                .ifPresent(profile -> ctx.lang().send(viewer, "profile.playtime",
                        LangService.of("hours", profile.playtimeSeconds() / 3600)));
    }

    private void openWeb(Player player) {
        ctx.services().optional(net.aethel.core.api.AuthService.class).ifPresentOrElse(
                auth -> ctx.lang().send(player, "profile.web-link",
                        LangService.link("url", auth.webLoginUrl(player))),
                () -> ctx.lang().send(player, "panel.web-unavailable"));
    }

    private PanelMode panelMode() {
        return ctx.services().optional(AdminPanelService.class)
                .map(AdminPanelService::mode)
                .orElseGet(() -> PanelMode.parse(ctx.config().get("config.yml").yaml()
                        .getString("admin.mode", "GUI")));
    }
}
