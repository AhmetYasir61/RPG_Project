package net.aethel.core.modules.content.pack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Custom bloklara note_block state'i tahsis eder. Tahsisler PackIndex uzerinde
 * kalicidir; ayni blok her uretimde ayni state'i alir.
 */
public final class NoteBlockAllocator {

    /**
     * note_block'un 16 enstrumani × 25 notasi × 2 powered durumu = 800 kombinasyon.
     * Vanilla davranisinin bozulmamasi icin "harp/0/false" (dogal hali) ayrilmaz.
     *
     * Neden note_block: state havuzu genis, blok modeli tamamen degistirilebilir ve
     * blockstate dosyasi tek dosyada toplanir. Alternatifler: mushroom_block (64 state,
     * daha az) ve tripwire (128 state ama redstone etkilesimi karisik). Bedeli:
     * note_block'un vanilla ses mekaniginin devre disi birakilmasi gerekir — bunu
     * BlockPhysicsEvent + Interact iptali ile yapiyoruz.
     */
    private static final List<String> INSTRUMENTS = List.of(
            "harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar",
            "chime", "xylophone", "iron_xylophone", "cow_bell", "didgeridoo",
            "bit", "banjo", "pling");
    private static final int NOTES = 25;

    private final Set<String> used = new HashSet<>();

    public NoteBlockAllocator(Iterable<String> alreadyUsed) {
        alreadyUsed.forEach(used::add);
    }

    /** Bos bir state uretir; havuz tukendiyse hata firlatir (uretimde acikca gorunsun). */
    public String allocate() {
        for (String instrument : INSTRUMENTS) {
            for (int note = 0; note < NOTES; note++) {
                for (boolean powered : new boolean[] {false, true}) {
                    if (isVanillaDefault(instrument, note, powered)) continue;
                    String state = instrument + "/" + note + "/" + powered;
                    if (used.add(state)) return state;
                }
            }
        }
        throw new IllegalStateException(
                "note_block state havuzu doldu (800 kombinasyon). Ikinci bir tasiyici blok gerekiyor.");
    }

    /** Vanilla note block'un dogal hali; custom bloga verilirse normal blok bozulur. */
    private boolean isVanillaDefault(String instrument, int note, boolean powered) {
        return "harp".equals(instrument) && note == 0 && !powered;
    }

    public int remaining() {
        return INSTRUMENTS.size() * NOTES * 2 - used.size() - 1;
    }

    /** "harp/7/false" -> Bukkit blok verisi metni. */
    public static String toBlockData(String state) {
        String[] parts = state.split("/");
        return "minecraft:note_block[instrument=" + parts[0]
                + ",note=" + parts[1] + ",powered=" + parts[2] + "]";
    }
}
