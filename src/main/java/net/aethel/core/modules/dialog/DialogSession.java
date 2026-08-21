package net.aethel.core.modules.dialog;

import net.aethel.core.api.DialogService.DialogNode;

/**
 * Devam eden bir diyalog. Metin harf harf ilerler; oyuncunun okuma temposunu
 * bozmamak icin her tick'te sabit sayida karakter eklenir.
 */
final class DialogSession {

    private final DialogNode node;
    private int lineIndex;
    private int charIndex;
    private boolean awaitingChoice;

    DialogSession(DialogNode node) {
        this.node = node;
    }

    DialogNode node() { return node; }
    boolean awaitingChoice() { return awaitingChoice; }

    /** Su an gosterilecek kismi metin. */
    String currentText() {
        if (lineIndex >= node.lines().size()) return "";
        String line = node.lines().get(lineIndex);
        return line.substring(0, Math.min(charIndex, line.length()));
    }

    /** Bir adim ilerletir; satir bittiyse siradaki satira gecer. */
    void advance() {
        if (lineIndex >= node.lines().size()) return;
        String line = node.lines().get(lineIndex);
        if (charIndex < line.length()) {
            charIndex += Math.max(1, node.charsPerTick());
            return;
        }
        lineIndex++;
        charIndex = 0;
        if (lineIndex >= node.lines().size()) awaitingChoice = true;
    }

    /** Akan metni atlar: satiri tamamlar, zaten tamamsa siradakine gecer. */
    void skip() {
        if (lineIndex >= node.lines().size()) return;
        String line = node.lines().get(lineIndex);
        if (charIndex < line.length()) {
            charIndex = line.length();
            return;
        }
        lineIndex++;
        charIndex = 0;
        if (lineIndex >= node.lines().size()) awaitingChoice = true;
    }

    boolean finished() {
        return lineIndex >= node.lines().size();
    }

    /** Satir tamamlandi mi; "devam et" ipucunu gostermek icin. */
    boolean lineComplete() {
        if (lineIndex >= node.lines().size()) return true;
        return charIndex >= node.lines().get(lineIndex).length();
    }
}
