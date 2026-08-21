package net.aethel.core.modules.web;

/**
 * Panelin HTML iskeletleri. Bicimin tamami JAR icindeki web/styles.css dosyasindan
 * gelir (Nocturne tasarim sistemi); burada tek bir renk ya da olcu yazili degildir.
 * Sayfalar veri tasimaz: veriyi tarayici /api uclarindan ceker.
 */
final class WebPages {

    private WebPages() {}

    static String landing() {
        return page("Aethel", """
                <div class="auth-shell">
                  <div class="card elev-md auth-card">
                    <span class="card-kicker">Erisim</span>
                    <h2 class="card-title">Panel baglantisi</h2>
                    <p class="card-body">Panele girmek icin oyun icinden
                       <code>/adminmenu</code> yazarak tek kullanimlik bir baglanti al.</p>
                    <p class="card-meta">Baglanti kisa omurludur ve yalnizca bir kez calisir.</p>
                  </div>
                </div>""");
    }

    static String error(String message) {
        return page("Aethel", """
                <div class="auth-shell">
                  <div class="card elev-md auth-card">
                    <span class="card-kicker">Hata</span>
                    <p class="error">%s</p>
                    <a class="btn btn-secondary" href="/">Basa don</a>
                  </div>
                </div>""".formatted(escape(message)));
    }

    static String credentials(WebSession session, boolean registration, String error) {
        return credentials(session, registration, error, "PIN");
    }

    /**
     * Giris / kayit formu. Kayitta sir iki kez istenir: yanlis yazilmis bir PIN
     * oyuncuyu hesabindan tamamen kilitler ve karma geri donusturulemez.
     * label, auth.mode degerine gore "PIN" ya da "Parola" olur.
     */
    static String credentials(WebSession session, boolean registration, String error, String label) {
        String title = registration ? "Kayit Ol" : "Giris Yap";
        String action = registration ? "/register" : "/login";
        boolean numeric = "PIN".equalsIgnoreCase(label);
        String mode = numeric ? "numeric" : "text";
        String errorBlock = error == null ? "" : "<p class=\"error\">" + escape(error) + "</p>";
        String confirmField = !registration ? "" : ("""
                <div class="field">
                  <label for="pin2">%s (tekrar)</label>
                  <input id="pin2" name="pin2" class="input" type="password"
                         inputmode="%s" autocomplete="new-password" required>
                </div>""").formatted(escape(label), mode);

        return page("Aethel · " + title, """
                <div class="auth-shell">
                  <form class="card elev-lg auth-card" method="post" action="%s">
                    <span class="card-kicker">AethelCore</span>
                    <h2 class="card-title">%s</h2>
                    <p class="card-meta">Hesap: %s</p>
                    %s
                    <div class="field">
                      <label for="pin">%s</label>
                      <input id="pin" name="pin" class="input" type="password"
                             inputmode="%s" autocomplete="%s" autofocus required>
                    </div>
                    %s
                    <button class="btn btn-primary btn-block" type="submit">%s</button>
                    <p class="card-meta">Bu baglanti %d saniye sonra gecersiz olur.</p>
                  </form>
                </div>""".formatted(action, escape(title), escape(session.playerName()),
                errorBlock, escape(label), mode,
                registration ? "new-password" : "current-password",
                confirmField, escape(title), session.remainingSeconds()));
    }

    /**
     * Panel iskeleti. Bolum listesi, kayit listesi ve duzenleyici bos gelir;
     * hepsini web/panel.js semadan uretir. Boylece yeni bir modul alani eklemek
     * icin HTML'e dokunmak gerekmez.
     */
    static String panel(WebSession session) {
        return page("Aethel · Panel", """
                <div class="app">
                  <aside class="side">
                    <div class="side-brand">AethelCore<small>%s · %s</small></div>
                    <nav id="sections"></nav>
                  </aside>
                  <div class="main">
                    <header class="topbar">
                      <h1 id="title">Panel</h1>
                      <span class="tag tag-accent">%s</span>
                    </header>
                    <div class="content split">
                      <section class="card elev-sm">
                        <span class="card-kicker">Kayitlar</span>
                        <div class="list" id="records"></div>
                      </section>
                      <section class="card elev-sm">
                        <span class="card-kicker">Duzenle</span>
                        <form class="editor" id="editor"></form>
                      </section>
                    </div>
                  </div>
                </div>
                <div class="toast" id="toast" hidden></div>
                <script src="/assets/panel.js"></script>""".formatted(
                escape(session.playerName()), session.admin() ? "yetkili" : "oyuncu",
                session.remainingSeconds() + " sn"));
    }

    private static String page(String title, String body) {
        return """
                <!doctype html>
                <html lang="tr"><head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="color-scheme" content="dark">
                  <title>%s</title>
                  <link rel="stylesheet" href="/assets/styles.css">
                </head><body>%s</body></html>""".formatted(escape(title), body);
    }

    /** Kullanici verisi HTML'e gomulurken kacilir; panel bir enjeksiyon yuzeyi olmamali. */
    private static String escape(String raw) {
        return raw == null ? "" : raw
                .replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
