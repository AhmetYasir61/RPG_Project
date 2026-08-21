package net.aethel.core.modules.dungeon.instance;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.i18n.LangService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Set;

/**
 * Cekirdek kirildiktan sonraki cokme sekansi: geri sayim, uyarilar ve gorsel
 * efektler. Efektler particle ile verilir; skill kuralindaki gibi entity kullanilmaz.
 */
public final class CollapseSequence {

    /** Bu esiklerde ayrica baslik ve ses ile uyarilir. */
    private static final Set<Long> WARN_SECONDS = Set.of(300L, 180L, 120L, 60L, 30L, 10L, 5L, 3L, 2L, 1L);

    private final CoreContext ctx;
    private final MiniMessage mini = MiniMessage.miniMessage();

    public CollapseSequence(CoreContext ctx) {
        this.ctx = ctx;
    }

    /** Her saniye cagrilir: uyari, ekran sarsintisi ve moloz efekti. */
    public void tick(ActiveInstance instance) {
        long left = instance.collapseSecondsLeft();

        for (Player player : onlinePlayers(instance)) {
            player.sendActionBar(ctx.lang().render(player, "dungeon.collapse-actionbar",
                    LangService.of("seconds", left)));

            if (WARN_SECONDS.contains(left)) {
                player.showTitle(Title.title(
                        ctx.lang().render(player, "dungeon.collapse-title"),
                        ctx.lang().render(player, "dungeon.collapse-subtitle",
                                LangService.of("seconds", left)),
                        Title.Times.times(Duration.ofMillis(200),
                                Duration.ofMillis(1200), Duration.ofMillis(400))));
                player.playSound(player.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 1f, 0.5f);
            }
            debris(player);
        }
    }

    /**
     * Moloz efekti oyuncunun cevresine particle olarak dokulur. Gercek blok
     * dusurmek yerine particle kullaniyoruz: dunya zaten silinecek, blok fizigi
     * calistirmak yalnizca sunucuyu yorar.
     */
    private void debris(Player player) {
        Location above = player.getLocation().add(0, 6, 0);
        player.spawnParticle(Particle.FALLING_DUST, above, 12, 4, 1, 4, 0,
                org.bukkit.Material.DEEPSLATE.createBlockData());
        player.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0),
                6, 3, 1, 3, 0.02);
    }

    /** Sure dolunca icerde kalanlari oldurur; esyalar dunya ile birlikte gider. */
    public void finish(ActiveInstance instance) {
        for (Player player : onlinePlayers(instance)) {
            player.showTitle(Title.title(
                    ctx.lang().render(player, "dungeon.collapsed-title"),
                    ctx.lang().render(player, "dungeon.collapsed-subtitle"),
                    Title.Times.times(Duration.ofMillis(300),
                            Duration.ofMillis(2500), Duration.ofMillis(500))));
            player.setHealth(0.0D);
        }
    }

    private java.util.List<Player> onlinePlayers(ActiveInstance instance) {
        return instance.players().stream()
                .map(uuid -> ctx.plugin().getServer().getPlayer(uuid))
                .filter(java.util.Objects::nonNull)
                .filter(player -> player.getWorld().equals(instance.world()))
                .toList();
    }
}
