package net.aethel.core.modules.npc;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.aethel.core.packet.PacketBridge;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Paket tabanli oyuncu NPC'si. Sunucuda entity yoktur: tick harcamaz, mob sayacini
 * etkilemez ve dunya kaydina yazilmaz. Her oyuncu icin ayri gonderilir.
 */
final class PacketNpc {

    /** Ikinci deri katmanini (sapka, ceket) acan bit maskesi. */
    private static final int INDEX_SKIN_LAYERS = 17;
    private static final byte ALL_SKIN_LAYERS = 0x7F;

    private final PacketBridge bridge;
    private final int entityId;
    private final UUID uuid = UUID.randomUUID();
    private final String name;
    private final Location location;
    private final TextureProperty skin;

    PacketNpc(PacketBridge bridge, int entityId, String name, Location location,
              String skinValue, String skinSignature) {
        this.bridge = bridge;
        this.entityId = entityId;
        // Sekmede gorunmemesi icin ad gorunmez renk kodlariyla doldurulur.
        this.name = name.length() > 16 ? name.substring(0, 16) : name;
        this.location = location;
        this.skin = skinValue == null ? null
                : new TextureProperty("textures", skinValue, skinSignature);
    }

    /**
     * NPC once oyuncu listesine eklenir (deri yuklensin diye), sonra spawn edilir.
     * Sira tersine olursa istemci deriyi cozemez ve NPC Steve olarak gorunur.
     */
    void show(Player viewer) {
        UserProfile profile = new UserProfile(uuid, name);
        if (skin != null) profile.getTextureProperties().add(skin);

        WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(profile, false, 0,
                        com.github.retrooper.packetevents.protocol.player.GameMode.SURVIVAL,
                        null, null);
        bridge.send(viewer, new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED),
                List.of(info)));

        bridge.send(viewer, new WrapperPlayServerSpawnEntity(entityId, Optional.of(uuid),
                EntityTypes.PLAYER,
                new Vector3d(location.getX(), location.getY(), location.getZ()),
                location.getPitch(), location.getYaw(), location.getYaw(), 0, Optional.empty()));

        List<EntityData<?>> data = new ArrayList<>();
        data.add(new EntityData<>(INDEX_SKIN_LAYERS, EntityDataTypes.BYTE, ALL_SKIN_LAYERS));
        bridge.send(viewer, new WrapperPlayServerEntityMetadata(entityId, data));

        // Listeden hemen cikarilir: NPC sekmede gorunmesin ama derisi yuklu kalsin.
        bridge.send(viewer, new WrapperPlayServerPlayerInfoRemove(List.of(uuid)));
    }

    void hide(Player viewer) {
        bridge.send(viewer, new WrapperPlayServerDestroyEntities(entityId));
    }

    int entityId() {
        return entityId;
    }

    Location location() {
        return location;
    }
}
