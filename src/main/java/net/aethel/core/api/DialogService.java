package net.aethel.core.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/**
 * Typewriter tarzi diyalog sistemi. Metin harf harf akar; secenekler, kosullar ve
 * sonuclar panelden duzenlenir. Gorsel katman action bar/chat, entity gerektirmez.
 */
public interface DialogService {

    /** Bir diyalog dugumu. */
    record DialogNode(String id, List<String> lines, List<Choice> choices,
                      List<String> conditions, List<String> outcomes, int charsPerTick) {}

    /** Oyuncunun secebilecegi bir secenek. */
    record Choice(String text, String targetNode, List<String> conditions,
                  List<String> outcomes) {}

    Optional<DialogNode> node(String id);

    /** Diyalogu baslatir; oyuncu zaten bir diyalogdaysa yenisi baslamaz. */
    boolean start(Player player, String dialogId);

    /** Akan metni atlar ve tum satiri gosterir. */
    void skip(Player player);

    void choose(Player player, int choiceIndex);

    void stop(Player player);

    boolean inDialog(Player player);

    int reload();
}
