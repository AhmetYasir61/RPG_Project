package net.aethel.core.nms;

import org.bukkit.entity.Player;

/**
 * Surumden bagimsiz NMS sozlesmesi. Cekirdek yalnizca bu arayuzu gorur; net.minecraft
 * siniflari sadece nms/v1_21_11 alt projesinde bulunur ve oraya paperweight remap eder.
 * FAZ 2'de mob AI, paket ve blok state metotlari ile genisletilecek.
 */
public interface VersionAdapter {

    /** Adapterin destekledigi Minecraft surumu, orn. "1.21.11". */
    String minecraftVersion();

    /** Oyuncunun bagli oldugu kanal uzerinden ham paket gonderir. */
    void sendPacket(Player player, Object packet);

    /**
     * Bir varliga ait yeni entity id ayirir. Sahte (client-side) hologram ve NPC'ler
     * sunucuda entity olusturmadan bu id'lerle adreslenir.
     */
    int nextEntityId();
}
