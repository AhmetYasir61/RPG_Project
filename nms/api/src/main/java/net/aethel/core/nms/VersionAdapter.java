package net.aethel.core.nms;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Surumden bagimsiz NMS sozlesmesi. Cekirdek yalnizca bu arayuzu gorur; net.minecraft
 * siniflari sadece nms/v1_21_11 alt projesinde bulunur ve oraya paperweight remap eder.
 */
public interface VersionAdapter {

    String minecraftVersion();

    /** Oyuncunun baglantisi uzerinden ham paket gonderir. */
    void sendPacket(Player player, Object packet);

    /**
     * Sahte entity id ayirir. Hologram, NPC ve waypoint isaretleri sunucuda entity
     * olusturmadan yalnizca bu id'lerle adreslenir.
     */
    int nextEntityId();

    /** Bir bloga uygulanan kirilma animasyonunu (0-9 arasi asama) tek oyuncuya gonderir. */
    void sendBlockDamage(Player player, Location location, int stage);

    /** Sunucu tarafinda blogu degistirmeden istemciye sahte blok gosterir. */
    void sendFakeBlock(Player player, Location location, String blockData);

    /**
     * Mob'un varsayilan hedefleme/gezinme hedeflerini temizler. MobEngine kendi
     * davranisini kurabilsin diye vanilla AI'nin ustune yazmak yerine once bosaltiriz.
     */
    void clearGoals(LivingEntity entity);

    /** Bir mob'a belirli hiz ve mesafe ile hedefe gitme emri verir (pathfinder). */
    void navigateTo(LivingEntity entity, Location target, double speed);

    /** Oyuncu skin verisi (deger + imza); NPC'lere takilir. Skin yoksa null alanlar doner. */
    SkinData skinOf(Player player);

    /** NPC ve sahte oyuncu isaretleri icin skin bilgisi. */
    record SkinData(UUID owner, String value, String signature) {}
}
