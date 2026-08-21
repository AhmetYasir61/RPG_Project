package net.aethel.core.module;

/**
 * Bir modulun bu sunucuda calisamayacagini bildirir (eksik NMS adapteri, kapali paket
 * katmani gibi). Cekirdek bunu HATA degil, "atlandi" olarak isler ve stack trace basmaz.
 */
public class ModuleUnavailableException extends RuntimeException {

    public ModuleUnavailableException(String message) {
        super(message);
    }
}
