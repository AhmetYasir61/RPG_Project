package net.aethel.core.bootstrap;

import net.aethel.core.module.Module;

/**
 * Derleme zamaninda bilinen modul listesi. Classpath taramasi yerine acik liste
 * kullaniyoruz: baslangic suresi sabit kalir ve eksik modul derlemede yakalanir.
 */
final class ModuleCatalog {

    private ModuleCatalog() {}

    /** FAZ 1'de cekirdek tek basina calisir; sonraki fazlarda moduller buraya eklenir. */
    static Module[] all() {
        return new Module[0];
    }
}
