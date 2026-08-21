package net.aethel.core.modules.content.pack;

import net.aethel.core.bootstrap.CoreContext;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paketi oyuncuya zorunlu olarak gonderir ve sonucu takip eder. Kabul etmeyen ya da
 * indiremeyen oyuncu atilir: pack olmadan HUD, item ve menuler bozuk gorunur.
 */
public final class PackDelivery implements Listener {

    private final CoreContext ctx;
    private final ConcurrentHashMap<UUID, Boolean> applied = new ConcurrentHashMap<>();
    private String url;
    private String hash;
    private boolean force = true;

    public PackDelivery(CoreContext ctx) {
        this.ctx = ctx;
    }

    public void configure(String url, String hash, boolean force) {
        this.url = url;
        this.hash = hash;
        this.force = force;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        send(event.getPlayer());
    }

    /**
     * Gonderim, oyuncuya "kabul ediyor musun" diye SORMAZ: required bayragi ile gider.
     * Istemci yine de reddedebilir; o durumu asagidaki durum olayinda ele aliriz.
     */
    public void send(Player player) {
        if (url == null || hash == null) return;
        try {
            player.setResourcePack(UUID.nameUUIDFromBytes(hash.getBytes()),
                    URI.create(url).toString(), hexToBytes(hash), (String) null, force);
        } catch (IllegalArgumentException e) {
            ctx.logger().warning("Kaynak paketi gonderilemedi: " + e.getMessage());
        }
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> applied.put(player.getUniqueId(), true);
            case DECLINED -> {
                if (force) player.kick(ctx.lang().render(player, "pack.declined"));
            }
            case FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL -> {
                if (force) player.kick(ctx.lang().render(player, "pack.failed"));
            }
            default -> { /* ACCEPTED / DOWNLOADED: indirme suruyor, bir sey yapilmaz */ }
        }
    }

    public boolean hasPack(Player player) {
        return applied.getOrDefault(player.getUniqueId(), false);
    }

    private byte[] hexToBytes(String hex) {
        return java.util.HexFormat.of().parseHex(hex);
    }
}
