package net.aethel.core.api;

import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Optional;

/**
 * Soket ve tas mekanigi. Item'a takilan tas hem stat ekler hem gorunumu degistirir;
 * takan oyuncu oldurdukce asama yukselir ve son asamada item baska bir item'a evrilir.
 */
public interface SocketService {

    Optional<SocketStone> stone(String fullId);

    Collection<SocketStone> stones();

    /** Item'a takili tasin kimligi; takili degilse bos. */
    Optional<String> socketedStone(ItemStack stack);

    /** Item'in su ana kadarki oldurme sayaci. */
    int kills(ItemStack stack);

    /** Item'in bulundugu asama (0 = taban). */
    int stage(ItemStack stack);

    /**
     * Tasi takar. Item soket almiyorsa, yuvalari doluysa ya da tas bilinmiyorsa
     * false doner ve item'a DOKUNULMAZ.
     */
    boolean socket(ItemStack stack, String stoneFullId);

    /** Tasi soker; sayac sifirlanir cunku ilerleme tasa aittir. */
    boolean unsocket(ItemStack stack);

    int reload();
}
