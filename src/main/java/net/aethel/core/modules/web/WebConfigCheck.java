package net.aethel.core.modules.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

/**
 * Web paneli yapilandirmasini acilista dogrular. Bu tur uyumsuzluklar sessizce
 * calisir gibi gorunup baglantiyi kirar; teshisi dakikalar degil saatler alir.
 */
final class WebConfigCheck {

    private WebConfigCheck() {}

    /**
     * public-url ile gercekten dinlenen portu karsilastirir.
     *
     * En sik yapilan hata: port degistirilip public-url guncellenmiyor (ya da tersi).
     * Sunucu sorunsuz acilir, panel calisir, ama oyuncuya gonderilen baglanti YANLIS
     * porta gider ve tarayicida "baglanti kurulamadi" cikar. Hicbir hata logu olmadigi
     * icin de sorunun panelde degil adreste oldugu anlasilmaz.
     */
    static void validate(String publicUrl, String bind, int port, Logger log) {
        if (publicUrl == null || publicUrl.isBlank()) {
            log.warning("admin.web.public-url bos. Panel baglantisi 127.0.0.1:" + port
                    + " olarak verilecek; bu yalnizca ayni makinede calisir.");
            return;
        }
        URI uri;
        try {
            uri = new URI(publicUrl.trim());
        } catch (URISyntaxException e) {
            log.severe("admin.web.public-url gecerli bir adres degil: " + publicUrl);
            return;
        }
        int urlPort = uri.getPort() > 0 ? uri.getPort()
                : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;

        // Ters vekil (nginx vb.) arkasindaysa 80/443 normaldir; ayni makinede
        // dogrudan servis ediliyorsa portlarin tutmasi gerekir.
        boolean behindProxy = urlPort == 80 || urlPort == 443;
        if (urlPort != port && !behindProxy) {
            log.severe("PORT UYUMSUZLUGU: panel " + port + " portunu dinliyor ama "
                    + "admin.web.public-url " + urlPort + " portunu gosteriyor ("
                    + publicUrl + "). Oyuncuya gonderilen baglanti calismayacak.");
        }
        String host = uri.getHost();
        if (host != null && (host.equals("127.0.0.1") || host.equalsIgnoreCase("localhost"))) {
            log.warning("admin.web.public-url yerel adres gosteriyor (" + host
                    + "); uzaktan baglanan yetkililer paneli acamaz.");
        }
        if ("0.0.0.0".equals(host)) {
            log.severe("admin.web.public-url adresi 0.0.0.0 olamaz: bu bir dinleme "
                    + "adresidir, tarayiciya yazilamaz. Sunucunun genel IP'sini yaz.");
        }
    }

    /**
     * Kaynak paketi adresi ile panel adresi ayni makinede olmali. Farkli IP'ler
     * genelde elle yazim hatasidir (or. .118 / .119) ve pack indirilemez.
     */
    static void compareHosts(String packHost, String panelUrl, Logger log) {
        if (packHost == null || packHost.isBlank() || panelUrl == null || panelUrl.isBlank()) return;
        try {
            String panelHost = new URI(panelUrl.trim()).getHost();
            if (panelHost == null || panelHost.equals(packHost)) return;
            if (panelHost.equals("127.0.0.1") || packHost.equals("127.0.0.1")) return;

            log.warning("resource-pack.public-host (" + packHost + ") ile "
                    + "admin.web.public-url (" + panelHost + ") farkli adresler. "
                    + "Ayni makinede calisiyorsan bu muhtemelen yazim hatasidir.");
        } catch (URISyntaxException ignored) {
            // Adres zaten validate() icinde raporlaniyor.
        }
    }
}
