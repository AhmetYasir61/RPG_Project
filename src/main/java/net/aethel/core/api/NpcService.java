package net.aethel.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;

/**
 * NPC servisi. NPC'ler paket ile cizilir: sunucuda entity yoktur, tick harcamaz
 * ve mob sayacini etkilemez. Tiklama aksiyonlari panelden tanimlanir.
 */
public interface NpcService {

    /** Bir NPC tanimi. */
    record Npc(String id, String displayName, Location location, String skinOwner,
               String dialogId, java.util.List<String> clickActions, boolean lookAtPlayer) {}

    Optional<Npc> npc(String id);

    Collection<Npc> all();

    Npc create(String id, Location location, String displayName);

    void remove(String id);

    void show(Player player, String id);

    void hide(Player player, String id);

    /** Oyuncunun gorus alanindaki NPC'leri tazeler. */
    void refresh(Player player);
}
