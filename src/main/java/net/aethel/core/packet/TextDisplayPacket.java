package net.aethel.core.packet;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tek bir text_display'in paket temsili. Sunucuda entity yoktur: spawn, metadata ve
 * yok etme paketleri dogrudan istemciye gonderilir, sunucu hicbir tick harcamaz.
 */
public final class TextDisplayPacket {

    /** Display entity metadata indeksleri (1.19.4+ text_display). */
    private static final int INDEX_BILLBOARD = 15;
    private static final int INDEX_TEXT = 23;
    private static final int INDEX_BACKGROUND = 25;
    private static final byte BILLBOARD_CENTER = 3;

    private final PacketBridge bridge;
    private final int entityId;
    private final UUID uuid = UUID.randomUUID();
    private Location location;

    public TextDisplayPacket(PacketBridge bridge, int entityId, Location location) {
        this.bridge = bridge;
        this.entityId = entityId;
        this.location = location;
    }

    public int entityId() {
        return entityId;
    }

    public void spawn(Player viewer, Component text) {
        bridge.send(viewer, new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(uuid), EntityTypes.TEXT_DISPLAY,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                0f, 0f, 0f, 0, Optional.empty()));
        update(viewer, text);
    }

    /** Metni degistirir; yeniden spawn gerekmez, yalnizca metadata gider. */
    public void update(Player viewer, Component text) {
        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(INDEX_BILLBOARD, EntityDataTypes.BYTE, BILLBOARD_CENTER));
        data.add(new EntityData<>(INDEX_TEXT, EntityDataTypes.ADV_COMPONENT, text));
        data.add(new EntityData<>(INDEX_BACKGROUND, EntityDataTypes.INT, 0x40000000));
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
