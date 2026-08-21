package net.aethel.core.api;

import org.bukkit.entity.Player;

/**
 * Yonetim paneli girisi. Tek komut (/adminmenu) bu servise duser; hangi arayuzun
 * acilacagini PanelMode belirler ve iki mod ayni anda etkin olamaz.
 */
public interface AdminPanelService {

    PanelMode mode();

    /** Aktif moda gore paneli acar: GUI menusu ya da tek kullanimlik web baglantisi. */
    void open(Player player);

    /** Belirli bir bolume dogrudan giris (orn. "npc", "items", "regions"). */
    void openSection(Player player, String section);

    /** Panelin duzenleyebilecegi bolumler; modullerin kaydettigi listeden gelir. */
    java.util.List<String> sections();

    /**
     * Bir modul kendi panel bolumunu kaydeder. GUI ve WEB ayni kaydi kullanir;
     * is mantigi tek yerdedir, panel yalnizca on yuzdur.
     */
    void registerSection(String id, String displayName, String icon, String permission);
}
