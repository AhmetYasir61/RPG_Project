package net.aethel.core.modules.auth;

import net.aethel.core.api.AuthService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.i18n.LangService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Dogrulanmamis oyuncuyu oyundan yalitir: hareket, konusma, komut, blok ve envanter
 * eylemleri engellenir. Panel moduna gore karsilama akisini baslatir.
 */
final class AuthListener implements Listener {

    private final CoreContext ctx;
    private final AuthModule auth;
    private final AuthSettings settings;

    AuthListener(CoreContext ctx, AuthModule auth, AuthSettings settings) {
        this.ctx = ctx;
        this.auth = auth;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        auth.beginSession(player);

        // Durum asenkron belirlendigi icin karsilama bir tick sonra gonderilir.
        ctx.scheduler().later("auth", 20L, () -> {
            if (!player.isOnline()) return;
            switch (auth.state(player.getUniqueId())) {
                case AUTHENTICATED -> ctx.lang().send(player, "auth.session-restored");
                case UNREGISTERED -> prompt(player, true);
                case AWAITING_LOGIN -> prompt(player, false);
            }
        });

        // Sure asimi: giris yapmayan oyuncu sunucuda sonsuza kadar bekleyemez.
        ctx.scheduler().later("auth", 20L * settings.timeoutSeconds, () -> {
            if (player.isOnline() && !auth.isAuthenticated(player)) {
                player.kick(ctx.lang().render(player, "auth.timeout"));
            }
        });
    }

    /**
     * WEB modunda oyuncuya tiklanabilir bir baglanti gonderilir ve tarayicida islem
     * bitince oyun ici durum otomatik dogrulanir. GUI modunda ise oyun ici akis baslar.
     */
    private void prompt(org.bukkit.entity.Player player, boolean registration) {
        boolean web = "WEB".equalsIgnoreCase(ctx.config().get("config.yml").yaml()
                .getString("admin.mode", "GUI"));
        if (web) {
            ctx.lang().send(player, registration ? "auth.web-register" : "auth.web-login",
                    LangService.link("url", auth.webLoginUrl(player)));
        } else {
            ctx.lang().send(player, registration ? "auth.gui-register" : "auth.gui-login");
            ctx.services().optional(net.aethel.core.api.MenuService.class)
                    .ifPresent(menus -> menus.openAuth(player, registration));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        auth.endSession(event.getPlayer().getUniqueId());
    }

    /**
     * Hareket engeli yalnizca yatay eksende uygulanir: oyuncunun bakinmasi serbesttir,
     * yerinden ayrilmasi degil. Tam dondurma, istemcide sinirli hissi verir.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!settings.freezeUntilLogin || allowed(event.getPlayer())) return;
        var from = event.getFrom();
        var to = event.getTo();
        if (from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!allowed(event.getPlayer())) event.setCancelled(true);
    }

    /** Giris yapmamis oyuncu yalnizca kimlik komutlarini kullanabilir. */
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (allowed(event.getPlayer())) return;
        String command = event.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (command.startsWith("/giris") || command.startsWith("/kayit")
                || command.startsWith("/login") || command.startsWith("/register")) return;
        event.setCancelled(true);
        ctx.lang().send(event.getPlayer(), "auth.required");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!allowed(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!allowed(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player && !allowed(player)) {
            event.setCancelled(true);
        }
    }

    /** Giris ekranindaki oyuncu olmemeli: aksi halde esyasini kaybeder. */
    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player && !allowed(player)) {
            event.setCancelled(true);
        }
    }

    private boolean allowed(org.bukkit.entity.Player player) {
        return auth.state(player.getUniqueId()) == AuthService.State.AUTHENTICATED;
    }
}
