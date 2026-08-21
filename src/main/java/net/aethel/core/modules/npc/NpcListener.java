package net.aethel.core.modules.npc;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.packet.PacketBridge;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * NPC tiklamalarini yakalar. NPC sunucuda entity olmadigi icin Bukkit'in etkilesim
 * olayi calismaz; tiklama yalnizca istemciden gelen paketten anlasilir.
 */
final class NpcListener implements Listener {

    private final NpcModule npcs;
    private final CoreContext ctx;

    NpcListener(NpcModule npcs, CoreContext ctx, PacketBridge bridge) {
        this.npcs = npcs;
        this.ctx = ctx;
        bridge.listener(new InteractListener());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Giriste bir tick beklenir: istemci dunyayi yuklemeden gonderilen paketler yutulur.
        ctx.scheduler().later("npc", 20L, () -> npcs.refresh(event.getPlayer()));
    }

    /** Paket dinleyicisi; etkilesim paketini oyun thread'ine tasir. */
    private final class InteractListener extends PacketListenerAbstract {

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
            if (!(event.getPlayer() instanceof Player player)) return;

            WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);
            npcs.byEntityId(player, wrapper.getEntityId()).ifPresent(npc -> {
                event.setCancelled(true);
                // Oyun durumu ana thread'de degistirilir; paket thread'inde API cagrilmaz.
                ctx.scheduler().sync("npc", () -> npcs.handleClick(player, npc));
            });
        }
    }
}
