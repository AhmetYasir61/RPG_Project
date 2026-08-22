package net.aethel.core.api;

import java.util.List;
import java.util.Map;

/**
 * Bir soket tasi. Silaha takildiginda stat ekler ve GORUNUMU degistirir:
 * dokunun gri/siyah isaretli bolgesi tasin rengine boyanir.
 *
 * Renk dokuya UYGULANMAZ, uretim sirasinda ONCEDEN boyanmis varyantlar cikarilir.
 * Calisma aninda doku uretmek mumkun degildir -- istemci paketi yalnizca girerken
 * indirir; her varyantin pakette hazir durmasi gerekir.
 */
public record SocketStone(String id,
                          String namespace,
                          String displayName,
                          List<String> lore,
                          String baseMaterial,
                          String texture,
                          String tint,
                          Map<String, Double> attributes,
                          String particle,
                          List<String> tags) {

    public String fullId() {
        return namespace + ":" + id;
    }

    /** "#ff6a00" -> 0xff6a00. Gecersiz deger beyaza (etkisiz) duser. */
    public int tintRgb() {
        try {
            String hex = tint == null ? "" : tint.replace("#", "").trim();
            return hex.isEmpty() ? 0xFFFFFF : Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFFFFFF;
        }
    }
}
