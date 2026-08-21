package net.aethel.core.modules.web;

import java.util.UUID;

/**
 * Tarayici oturumu. Jeton tek kullanimlikti ve tuketildikten sonra yerine sureli
 * bir oturum cerezi gecer; boylece baglanti paylasilsa bile tekrar kullanilamaz.
 */
record WebSession(UUID player, String playerName, long expiresAt, boolean admin) {

    boolean valid() {
        return expiresAt > System.currentTimeMillis();
    }

    /** Kalan sure (saniye); arayuzde oturum sayaci icin. */
    long remainingSeconds() {
        return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000);
    }
}
