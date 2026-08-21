package net.aethel.core.modules.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.aethel.core.bootstrap.CoreContext;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI koprusu. Iki yonludur: bizim placeholder'lar PAPI'ye kaydedilir ve
 * PAPI placeholder'lari bizim metinlerimizde cozulur. PAPI yoksa bu sinif yuklenmez.
 */
final class PapiBridge extends PlaceholderExpansion {

    private final CoreContext ctx;
    private final PlaceholderModule module;

    PapiBridge(CoreContext ctx, PlaceholderModule module) {
        this.ctx = ctx;
        this.module = module;
    }

    /** PlaceholderExpansion#register final degildir ama adi cakisiyor; sarmaliyoruz. */
    void hook() {
        if (!register()) ctx.logger().warning("PlaceholderAPI genislemesi kaydedilemedi.");
    }

    void unhook() {
        unregister();
    }

    /** PAPI tarafindan %aethel_<prefix>_<key>% seklinde cagrilir. */
    @Override
    public String onRequest(OfflinePlayer player, String params) {
        int underscore = params.indexOf('_');
        if (underscore < 0) return null;
        String prefix = params.substring(0, underscore);
        String key = params.substring(underscore + 1);
        Player online = player == null ? null : player.getPlayer();
        return module.value(online, prefix, key, null);
    }

    /** Bizim metinlerimizde PAPI placeholder'larini cozer. */
    String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    @Override public String getIdentifier() { return "aethel"; }
    @Override public String getAuthor() { return "AethelCore"; }
    @Override public String getVersion() { return ctx.plugin().getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
}
