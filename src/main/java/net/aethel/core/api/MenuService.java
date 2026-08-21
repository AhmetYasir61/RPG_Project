package net.aethel.core.api;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/**
 * Menu motoru. Tum yonetim ve oyuncu arayuzleri buradan gecer; metin girisi chat
 * yerine anvil ile alinir, boylece oyuncu menuden hic cikmaz.
 */
public interface MenuService {

    /** rows 1-6 arasi olmalidir. */
    Menu create(Component title, int rows);

    /**
     * Anvil uzerinden metin girisi ister. Oyuncu yazar, sonuc slotuna tiklar ve
     * callback calisir. Iptal ederse callback hic cagrilmaz.
     */
    void anvilInput(Player player, Component title, String initial, Consumer<String> callback);

    /** Sayi girisi; gecersiz deger girilirse tekrar sorar. */
    void anvilNumber(Player player, Component title, double initial, Consumer<Double> callback);

    /** Giris/kayit ekrani (GUI modunda). registration true ise kayit akisi acilir. */
    void openAuth(Player player, boolean registration);

    /** YAML'den tanimli bir menuyu acar (content/menus/*.yml). */
    boolean openNamed(Player player, String menuId);
}
