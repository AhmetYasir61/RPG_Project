package net.aethel.core.nms.v1_21_11;

import net.aethel.core.nms.VersionAdapter;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * 1.21.11 VersionAdapter implementasyonu. Mojang-mapped kaynak yazilir, paperweight
 * derleme sirasinda remap eder; refleksiyon yoktur, hatalar derlemede yakalanir.
 */
public final class Adapter implements VersionAdapter {

    private static final java.util.concurrent.atomic.AtomicInteger FAKE_ENTITY_IDS =
            new java.util.concurrent.atomic.AtomicInteger(Integer.MAX_VALUE);

    @Override
    public String minecraftVersion() {
        return "1.21.11";
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        handle.connection.send((Packet<?>) packet);
    }

    /**
     * Sahte entity id'leri Integer.MAX_VALUE'den geriye dogru dagitilir. Vanilla sayaci
     * 1'den ileri sayar ve pratikte bu araliga hicbir zaman ulasmaz; boylece sunucunun
     * ic sayacina dokunmadan carpisma riski olmayan id uretmis oluruz.
     */
    @Override
    public int nextEntityId() {
        return FAKE_ENTITY_IDS.decrementAndGet();
    }
}
