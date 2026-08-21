package net.aethel.core.packet;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Parti uyesi isareti icin kullanilan saydam kafa: item_display entity'si paket ile
 * cizilir. Sunucuda entity yoktur, yalnizca isareti goren oyuncunun istemcisinde durur.
 */
public final class HeadDisplayPacket {

    /** item_display metadata indeksleri (1.21). */
    private static final int INDEX_FLAGS = 0;
    private static final int INDEX_BILLBOARD = 15;
    private static final int INDEX_ITEM = 23;
    private static final int INDEX_DISPLAY_TYPE = 24;
    private static final byte FLAG_GLOWING = 0x40;
    private static final byte BILLBOARD_CENTER = 3;
    private static final byte DISPLAY_HEAD = 5;

    private final PacketBridge bridge;
    private final int entityId;
    private final UUID uuid = UUID.randomUUID();
    private Location location;

    public HeadDisplayPacket(PacketBridge bridge, int entityId, Location location) {
        this.bridge = bridge;
        this.entityId = entityId;
        this.location = location;
    }

    public int entityId() {
        return entityId;
    }

    /**
     * Kafayi cizer. glowing true ise istemci varligi duvarlarin arkasindan da parlayan
     * bir konturla gosterir; MMORPG'lerdeki "arkadasini gorme" hissi buradan gelir.
     */
    public void spawn(Player viewer, ItemStack head, boolean glowing) {
        bridge.send(viewer, new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(uuid), EntityTypes.ITEM_DISPLAY,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                0f, 0f, 0f, 0, Optional.empty()));
        update(viewer, head, glowing);
    }

    public void update(Player viewer, ItemStack head, boolean glowing) {
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(INDEX_FLAGS, EntityDataTypes.BYTE,
                glowing ? FLAG_GLOWING : (byte) 0));
        data.add(new EntityData<>(INDEX_BILLBOARD, EntityDataTypes.BYTE, BILLBOARD_CENTER));
        data.add(new EntityData<>(INDEX_ITEM, EntityDataTypes.ITEMSTACK,
                SpigotConversionUtil.fromBukkitItemStack(head)));
        data.add(new EntityData<>(INDEX_DISPLAY_TYPE, EntityDataTypes.BYTE, DISPLAY_HEAD));
        bridge.send(viewer, new WrapperPlayServerEntityMetadata(entityId, data));
    }

    public void move(Player viewer, Location target) {
        this.location = target;
        bridge.send(viewer, new WrapperPlayServerEntityTeleport(entityId,
                new Vector3d(target.getX(), target.getY(), target.getZ()),
                target.getYaw(), target.getPitch(), false));
    }

    public void destroy(Player viewer) {
        bridge.send(viewer, new WrapperPlayServerDestroyEntities(entityId));
    }

    public Location location() {
        return location;
    }
}
