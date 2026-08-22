package net.aethel.core.modules.content;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import net.aethel.core.i18n.LangService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Kaynak paketi komutu. Sunucu ACIKKEN paketi bastan uretip cevrimici herkese
 * yeniden gonderir; yeniden baslatma gerekmez.
 *
 * Yonetim yuzeyinin geri kalani menu tabanlidir; bu komut istek uzerine, ayni isi
 * yapan panel dugmesinin yaninda ek bir yol olarak duruyor. Yetki: aethel.admin.pack.
 */
@Command(value = "pack", aliases = {"paket"}, permission = "aethel.admin.pack",
        descriptionKey = "pack.command-description")
public final class PackCommand {

    private final CoreContext ctx;
    private final ContentModule content;

    public PackCommand(CoreContext ctx, ContentModule content) {
        this.ctx = ctx;
        this.content = content;
    }

    /** Argumansiz kullanim durumu gosterir; kazara yeniden uretim baslatmaz. */
    @Command("")
    public void status(CommandSender sender) {
        String hash = content.currentHash();
        ctx.lang().send(sender, "pack.status",
                LangService.of("hash", hash.isBlank() ? "-" : hash.substring(0, Math.min(8, hash.length()))),
                LangService.of("size", String.format("%.1f", content.packSize() / 1024.0 / 1024.0)),
                LangService.of("items", content.all().size()));
    }

    /**
     * Tanimlari diskten yeniden okur, zip'i bastan uretir ve cevrimici herkese
     * gonderir. Tum agir is sanal thread'de; sunucu donmaz.
     */
    @Command(value = "yenile", aliases = {"reload"}, feature = "content.pack-generation")
    public void regenerate(CommandSender sender) {
        ctx.lang().send(sender, "pack.regenerating");

        content.regenerateAndPublish(result -> {
            if (!result.ok()) {
                ctx.lang().send(sender, "busy".equals(result.error())
                        ? "pack.busy" : "pack.failed-generate",
                        LangService.of("error", String.valueOf(result.error())));
                return;
            }
            result.warnings().forEach(warning ->
                    ctx.lang().send(sender, "pack.warning", LangService.of("text", warning)));

            // Hash degismediyse istemci paketi yeniden INDIRMEZ; bu bir hata degil,
            // ama "yeniledim, bir sey olmadi" sanilmasin diye ayri soyleniyor.
            ctx.lang().send(sender, result.changed() ? "pack.done" : "pack.unchanged",
                    LangService.of("items", result.items()),
                    LangService.of("files", result.files()),
                    LangService.of("ms", result.millis()),
                    LangService.of("players", ctx.plugin().getServer().getOnlinePlayers().size()));
        });
    }

    /**
     * Paketi kontrol eder ve NEDEN gorunmedigini soyler.
     *
     * Item'in mor-siyah kare cikmasinin uc yaygin nedeni var ve ucu de sunucu
     * gunluguene hicbir sey yazmaz: dokunun diskte olmamasi, dokunun uretilen
     * zip'e girmemis olmasi, ve pack_format'in sunucu surumune uymamasi. Bu komut
     * ucunu de tek tek yazar.
     */
    @Command("dogrula")
    public void verify(CommandSender sender) {
        ctx.lang().send(sender, "pack.verify-header",
                LangService.of("format", content.packFormat()),
                LangService.of("expected", net.aethel.core.modules.content.pack
                        .PackGenerator.DEFAULT_PACK_FORMAT));

        if (sender instanceof Player player) reportStaleItems(player);
        var problems = content.verify();
        if (problems.isEmpty()) {
            ctx.lang().send(sender, "pack.verify-clean",
                    LangService.of("items", content.all().size()));
            return;
        }
        problems.forEach(problem ->
                ctx.lang().send(sender, "pack.verify-problem", LangService.of("text", problem)));
        ctx.lang().send(sender, "pack.verify-count", LangService.of("count", problems.size()));
    }

    /**
     * Envanterdeki ESKI itemleri sayar.
     *
     * item_model degeri itemin NBT'sine yazilir; tanim ya da uretim duzeltilse
     * bile ONCEDEN verilmis itemler eski degeri tasimaya devam eder ve bozuk
     * gorunur. Paket tertemiz oldugu halde "hala kare" denmesinin nedeni budur,
     * bu yuzden denetimin sonunda ayrica soyleniyor.
     */
    private void reportStaleItems(Player player) {
        var service = ctx.services().optional(net.aethel.core.api.ItemService.class);
        if (service.isEmpty()) return;

        int stale = 0;
        for (var stack : player.getInventory().getContents()) {
            if (stack == null || !stack.hasItemMeta()) continue;
            var definition = service.get().resolve(stack);
            if (definition.isEmpty()) continue;

            var current = stack.getItemMeta().getItemModel();
            String expected = definition.get().modelKey();
            if (current == null || !current.toString().equals(expected)) stale++;
        }
        if (stale > 0) {
            ctx.lang().send(player, "pack.verify-stale", LangService.of("count", stale));
        }
    }

    /** Paketi yalnizca bir oyuncuya yeniden gonderir (indirmesi takilan oyuncu icin). */
    @Command("gonder")
    public void resend(CommandSender sender, @Arg(value = "oyuncu", suggests = "player") String target) {
        Player player = ctx.plugin().getServer().getPlayerExact(target);
        if (player == null) {
            ctx.lang().send(sender, "pack.player-offline", LangService.of("player", target));
            return;
        }
        content.resend(player);
        ctx.lang().send(sender, "pack.resent", LangService.of("player", player.getName()));
    }
}
