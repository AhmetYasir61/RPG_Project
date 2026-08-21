package net.aethel.core.nms.v1_21_11;

import com.mojang.authlib.properties.Property;
import net.aethel.core.nms.VersionAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1.21.11 VersionAdapter implementasyonu. Mojang-mapped kaynak yazilir, paperweight
 * derleme sirasinda remap eder; refleksiyon yoktur, hatalar derlemede yakalanir.
 */
public final class Adapter implements VersionAdapter {

    private static final AtomicInteger FAKE_ENTITY_IDS = new AtomicInteger(Integer.MAX_VALUE);

    @Override
    public String minecraftVersion() {
        return "1.21.11";
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        ((CraftPlayer) player).getHandle().connection.send((Packet<?>) packet);
    }

    /**
     * Sahte entity id'leri Integer.MAX_VALUE'den geriye dogru dagitilir. Vanilla sayaci
     * 1'den ileri sayar ve pratikte bu araliga ulasmaz; boylece sunucunun ic sayacina
     * dokunmadan carpisma riski olmayan id uretmis oluruz.
     */
    @Override
    public int nextEntityId() {
        return FAKE_ENTITY_IDS.decrementAndGet();
    }

    @Override
    public void sendBlockDamage(Player player, Location location, int stage) {
        BlockPos pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        sendPacket(player, new ClientboundBlockDestructionPacket(nextEntityId(), pos, stage));
    }

    @Override
    public void sendFakeBlock(Player player, Location location, String blockData) {
        BlockPos pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        var state = ((org.bukkit.craftbukkit.block.data.CraftBlockData)
                org.bukkit.Bukkit.createBlockData(blockData)).getState();
        sendPacket(player, new ClientboundBlockUpdatePacket(pos, state));
    }

    @Override
    public void clearGoals(LivingEntity entity) {
        if (((CraftLivingEntity) entity).getHandle() instanceof Mob mob) {
            mob.goalSelector.removeAllGoals(goal -> true);
            mob.targetSelector.removeAllGoals(goal -> true);
        }
    }

    @Override
    public void navigateTo(LivingEntity entity, Location target, double speed) {
        if (((CraftLivingEntity) entity).getHandle() instanceof Mob mob) {
            mob.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), speed);
        }
    }

    @Override
    public SkinData skinOf(Player player) {
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        var textures = handle.getGameProfile().properties().get("textures");
        for (Property property : textures) {
            return new SkinData(player.getUniqueId(), property.value(), property.signature());
        }
        return new SkinData(player.getUniqueId(), null, null);
    }

    /** CraftServer erisimi, ileride dunya/kayit islemleri icin gerekli olacak. */
    static CraftServer server() {
        return (CraftServer) org.bukkit.Bukkit.getServer();
    }
}
