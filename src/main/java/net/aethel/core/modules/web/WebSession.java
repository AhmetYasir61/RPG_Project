package net.aethel.core.modules.web;

import java.util.UUID;

/**
 * Tarayici oturumu. Jeton tuketilince olusur ama BASTA DOGRULANMAMIS'tir:
 * panele erisim ancak PIN girildikten sonra acilir.
 */
final class WebSession {

    private final UUID player;
    private final String playerName;
    private final long expiresAt;
    private boolean authenticated;
    private boolean admin;

    WebSession(UUID player, String playerName, long expiresAt) {
        this.player = player;
        this.playerName = playerName;
        this.expiresAt = expiresAt;
    }

    UUID player() { return player; }
    String playerName() { return playerName; }
    boolean admin() { return admin; }

    /**
     * Yalnizca kimlik dogrulandiktan sonra true olur. Jetonun kendisi yeterli
     * sayilsaydi, baglantiyi ele geciren biri PIN bilmeden hesaba girerdi.
     */
    boolean authenticated() {
        return authenticated && valid();
    }

    void authenticate(boolean admin) {
        this.authenticated = true;
        this.admin = admin;
    }

    boolean valid() {
        return expiresAt > System.currentTimeMillis();
    }

    /** Kalan sure (saniye); arayuzde oturum sayaci icin. */
    long remainingSeconds() {
        return Math.max(0, (expiresAt - System.currentTimeMillis()) / 1000);
    }
}
