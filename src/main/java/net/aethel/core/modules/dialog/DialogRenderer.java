package net.aethel.core.modules.dialog;

import net.aethel.core.api.PlaceholderService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.util.SpaceEncoder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

/**
 * Diyalog kutusunu ekrana cizer. Kutu, portre ve secenekler negatif bosluk fontu
 * ile ust uste bindirilip TEK bir baslik/alt baslik metni olarak gonderilir.
 */
final class DialogRenderer {

    /**
     * Kutu, title/subtitle uzerinden ciziliyor. Alternatifler: action bar (tek satir,
     * cok satirli kutu sigmaz), boss bar (ust kenara sabit, portre alani yok) ve chat
     * (kaydirilir, kalicilik yok). Title ekranin ortasinda, cok satirli ve her tick
     * yenilenebilir oldugu icin diyalog kutusu icin tek uygun tasiyici odur.
     *
     * Kalis suresi kisa tutulup her adimda yeniden gonderiliyor: boylece metin akarken
     * kutu titremeden yenilenir, diyalog bitince de kendiliginden kaybolur.
     */
    private static final Title.Times TIMES = Title.Times.times(
            Duration.ZERO, Duration.ofMillis(600), Duration.ZERO);

    private final CoreContext ctx;
    private final MiniMessage mini = MiniMessage.miniMessage();

    DialogRenderer(CoreContext ctx) {
        this.ctx = ctx;
    }

    /** Tek bir diyalog karesi cizer. */
    void draw(Player player, Frame frame) {
        Component titleLine = mini.deserialize(buildTitle(frame));
        Component subtitleLine = mini.deserialize(buildSubtitle(player, frame));
        player.showTitle(Title.title(titleLine, subtitleLine, TIMES));
    }

    /** Cizilecek karenin verisi. */
    record Frame(String speaker, int portraitIndex, List<String> bodyLines,
                 List<String> choices, int selectedChoice, boolean complete) {}

    /**
     * Ust satir: kutu cercevesi + portre + konusmacinin adi. Once cerceve cizilip
     * genisligi kadar geri donuluyor; sonraki parcalar ayni piksel uzerine biner.
     */
    private String buildTitle(Frame frame) {
        StringBuilder line = new StringBuilder();

        // Kutuyu ekranin ortasina hizala: genisliginin yarisi kadar sola kay.
        line.append("<white><font:aethel:dialog>");
        line.append(SpaceEncoder.shift(-DialogGlyphs.BOX_WIDTH / 2));
        line.append(DialogGlyphs.FRAME);
        line.append(SpaceEncoder.shift(-DialogGlyphs.BOX_WIDTH));

        // Portre, kutunun sol icine oturur.
        line.append(SpaceEncoder.shift(DialogGlyphs.TEXT_LEFT_PADDING));
        line.append(DialogGlyphs.portrait(frame.portraitIndex()));
        line.append(SpaceEncoder.shift(-DialogGlyphs.PORTRAIT_WIDTH));
        line.append("</font>");

        // Konusmacinin adi portrenin sagindan baslar.
        line.append(SpaceEncoder.shift(DialogGlyphs.PORTRAIT_WIDTH
                + DialogGlyphs.TEXT_LEFT_PADDING));
        line.append("<gradient:#f0c040:#e08020><bold>")
                .append(frame.speaker())
                .append("</bold></gradient>");
        return line.toString();
    }

    /** Alt blok: govde satirlari ve secenekler. */
    private String buildSubtitle(Player player, Frame frame) {
        StringBuilder block = new StringBuilder();
        int textStart = DialogGlyphs.PORTRAIT_WIDTH + DialogGlyphs.TEXT_LEFT_PADDING;

        for (String bodyLine : frame.bodyLines()) {
            block.append(SpaceEncoder.shift(-DialogGlyphs.BOX_WIDTH / 2 + textStart));
            block.append("<gray>").append(resolve(player, bodyLine)).append("</gray>");
            block.append("\n");
        }
        if (!frame.complete()) {
            return block.toString();
        }
        if (frame.choices().isEmpty()) {
            block.append(SpaceEncoder.shift(-DialogGlyphs.BOX_WIDTH / 2 + textStart));
            block.append("<dark_gray>[ ")
                    .append(ctx.lang().render(player, "dialog.continue-hint"))
                    .append(" ]</dark_gray>");
            return block.toString();
        }
        appendChoices(block, player, frame, textStart);
        return block.toString();
    }

    /**
     * Secenekler. Secili olan imlecle ve vurgulu renkle gosterilir; secim chat ile
     * degil hotbar kaydirmasiyla yapildigi icin ekranda hangi satirin secili oldugu
     * her zaman gorunur olmali.
     */
    private void appendChoices(StringBuilder block, Player player, Frame frame, int textStart) {
        for (int i = 0; i < frame.choices().size(); i++) {
            boolean selected = i == frame.selectedChoice();
            block.append(SpaceEncoder.shift(-DialogGlyphs.BOX_WIDTH / 2 + textStart));

            if (selected) {
                block.append("<font:aethel:dialog><yellow>")
                        .append(DialogGlyphs.CHOICE_CURSOR)
                        .append("</yellow></font>")
                        .append(SpaceEncoder.shift(2))
                        .append("<yellow>");
            } else {
                block.append(SpaceEncoder.shift(10)).append("<dark_gray>");
            }
            block.append(resolve(player, frame.choices().get(i)));
            block.append(selected ? "</yellow>" : "</dark_gray>");
            if (i < frame.choices().size() - 1) block.append("\n");
        }
    }

    private String resolve(Player player, String text) {
        return ctx.services().optional(PlaceholderService.class)
                .map(service -> service.apply(player, text))
                .orElse(text);
    }

    /** Diyalog bitince kutuyu ekrandan kaldirir. */
    void clear(Player player) {
        player.clearTitle();
    }
}
