package net.aethel.core.modules.hud;

import java.util.List;

/**
 * Bir HUD duzeni: katmanlar, kosul ve guncelleme sikligi. Katmanlar negatif bosluk
 * karakterleriyle ust uste bindirilir, hepsi tek bir action bar satirinda cizilir.
 */
record HudLayout(String id,
                 String condition,
                 int priority,
                 int updateTicks,
                 List<Layer> layers) {

    /**
     * Tek bir katman. offsetX piksel cinsindendir ve negatif bosluk karakterleriyle
     * uygulanir; boylece parcalar birbirinden bagimsiz konumlandirilabilir.
     */
    record Layer(String text, int offsetX, String showWhen) {}
}
