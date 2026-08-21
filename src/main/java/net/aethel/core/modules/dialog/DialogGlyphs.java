package net.aethel.core.modules.dialog;

/**
 * Diyalog kutusunun font karakterleri ve piksel olculeri. Karakterler resource
 * pack uretiminde tahsis edilir; buradaki degerler pack ile birebir ayni olmalidir.
 */
final class DialogGlyphs {

    /**
     * Kutu tek bir font karakteri olarak cizilir. Alternatif, kutuyu satir satir
     * karakterlerden ormek olurdu; tek buyuk bir texture hem daha az karakter
     * harcar hem de olcek degistiginde tek dosya guncellenir.
     */
    static final String FRAME = "";
    static final String FRAME_TOP = "";
    static final String PORTRAIT_SLOT = "";
    static final String CHOICE_CURSOR = "";
    static final String CONTINUE_ARROW = "";

    /** Portre karakterleri U+E200'den baslar; her NPC bir karakter alir. */
    static final int PORTRAIT_BASE = 0xE200;

    /** Kutu genisligi (piksel). Metin sarmalamasi bu olcuye gore hesaplanir. */
    static final int BOX_WIDTH = 320;

    /** Portre alani ve metin baslangici arasindaki bosluk. */
    static final int PORTRAIT_WIDTH = 48;
    static final int TEXT_LEFT_PADDING = 8;

    /** Kutuya sigan metin genisligi. */
    static final int TEXT_WIDTH = BOX_WIDTH - PORTRAIT_WIDTH - TEXT_LEFT_PADDING * 2;

    private DialogGlyphs() {}

    /** Bir NPC portresinin karakterini verir; indeks pack tahsisinden gelir. */
    static String portrait(int index) {
        return new String(Character.toChars(PORTRAIT_BASE + Math.max(0, index)));
    }
}
