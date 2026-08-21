package net.aethel.core.modules.dialog;

import net.aethel.core.api.DialogService.DialogNode;

import java.util.List;

/**
 * Devam eden bir diyalog. Metin harf harf ilerler, satirlar kutuya sigacak sekilde
 * onceden sarmalanir ve secim imleci burada tutulur.
 */
final class DialogSession {

    private final DialogNode node;
    private final String speaker;
    private final int portraitIndex;
    private final List<String> wrapped;

    private int lineIndex;
    private int visibleChars;
    private int selectedChoice;
    private boolean awaitingChoice;

    DialogSession(DialogNode node, String speaker, int portraitIndex, List<String> wrapped) {
        this.node = node;
        this.speaker = speaker;
        this.portraitIndex = portraitIndex;
        this.wrapped = wrapped;
    }

    DialogNode node() { return node; }
    String speaker() { return speaker; }
    int portraitIndex() { return portraitIndex; }
    int selectedChoice() { return selectedChoice; }
    boolean awaitingChoice() { return awaitingChoice; }

    /**
     * Kutuda gorunecek satirlar. Tamamlanan satirlar tam, o an akan satir kismi
     * gosterilir; sonraki satirlar hic cizilmez ki metin asagi dogru "acilsin".
     */
    List<String> visibleLines() {
        List<String> lines = new java.util.ArrayList<>(lineIndex + 1);
        for (int i = 0; i < lineIndex && i < wrapped.size(); i++) {
            lines.add(wrapped.get(i));
        }
        if (lineIndex < wrapped.size()) {
            lines.add(TextMeasure.truncateVisible(wrapped.get(lineIndex), visibleChars));
        }
        return lines;
    }

    /** Bir adim ilerletir; satir bitince siradakine gecer. */
    void advance(int charsPerStep) {
        if (lineIndex >= wrapped.size()) {
            awaitingChoice = true;
            return;
        }
        String line = wrapped.get(lineIndex);
        if (visibleChars < TextMeasure.visibleLength(line)) {
            visibleChars += Math.max(1, charsPerStep);
            return;
        }
        lineIndex++;
        visibleChars = 0;
        if (lineIndex >= wrapped.size()) awaitingChoice = true;
    }

    /** Akan metni atlar: once satiri tamamlar, zaten tamsa tum metni acar. */
    void skip() {
        if (lineIndex < wrapped.size()
                && visibleChars < TextMeasure.visibleLength(wrapped.get(lineIndex))) {
            visibleChars = TextMeasure.visibleLength(wrapped.get(lineIndex));
            return;
        }
        lineIndex = wrapped.size();
        visibleChars = 0;
        awaitingChoice = true;
    }

    /** Metnin tamami gorunur oldu mu; "devam" ipucu ve secenekler buna bagli. */
    boolean textComplete() {
        return lineIndex >= wrapped.size();
    }

    /** Secim imlecini dairesel olarak kaydirir. */
    void moveSelection(int delta) {
        int count = node.choices().size();
        if (count == 0) return;
        selectedChoice = ((selectedChoice + delta) % count + count) % count;
    }

    /** Kutuya cizilecek kare. */
    DialogRenderer.Frame frame() {
        return new DialogRenderer.Frame(speaker, portraitIndex, visibleLines(),
                node.choices().stream()
                        .map(net.aethel.core.api.DialogService.Choice::text).toList(),
                selectedChoice, textComplete());
    }
}
