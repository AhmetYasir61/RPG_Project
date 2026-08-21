package net.aethel.core.modules.web;

/**
 * Panelin HTML sayfalari. Tek dosyada gomulu tutuluyor: harici bir varlik sunucusuna
 * bagimli olmadan, JAR icinden calisan kendi kendine yeten bir panel.
 */
final class WebPages {

    private WebPages() {}

    private static final String STYLE = """
            <style>
              :root { color-scheme: dark; }
              * { box-sizing: border-box; }
              body { margin:0; font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
                     background:#12131a; color:#e8e8ef; }
              header { padding:18px 24px; background:#1b1d29; border-bottom:1px solid #2a2d3d;
                       display:flex; justify-content:space-between; align-items:center; }
              h1 { font-size:18px; margin:0; letter-spacing:.02em; }
              .wrap { max-width:1100px; margin:0 auto; padding:24px; }
              .grid { display:grid; gap:16px; grid-template-columns:repeat(auto-fit,minmax(260px,1fr)); }
              .card { background:#1b1d29; border:1px solid #2a2d3d; border-radius:10px; padding:16px; }
              .card h2 { font-size:13px; text-transform:uppercase; letter-spacing:.08em;
                         color:#8a8fa8; margin:0 0 10px; }
              table { width:100%; border-collapse:collapse; font-size:14px; }
              th,td { text-align:left; padding:7px 8px; border-bottom:1px solid #262838; }
              th { color:#8a8fa8; font-weight:600; font-size:12px; text-transform:uppercase; }
              .tag { display:inline-block; padding:2px 8px; border-radius:99px;
                     background:#2a2d3d; font-size:12px; }
              .muted { color:#8a8fa8; }
              button { background:#3b4fd8; color:#fff; border:0; border-radius:6px;
                       padding:7px 12px; cursor:pointer; font-size:13px; }
              button:hover { background:#4a5ee8; }
              .warn { color:#ffb168; }
              .narrow { max-width:420px; }
              .small { font-size:12px; }
              .form { display:flex; flex-direction:column; gap:6px; margin-top:14px; }
              .form label { font-size:12px; color:#8a8fa8; text-transform:uppercase;
                            letter-spacing:.06em; }
              .form input { background:#12131a; border:1px solid #2a2d3d; border-radius:6px;
                            color:#e8e8ef; padding:10px 12px; font-size:16px; }
              .form input:focus { outline:none; border-color:#3b4fd8; }
              .form button { margin-top:10px; padding:10px 12px; font-size:14px; }
            </style>""";

    static String landing() {
        return page("Aethel", """
                <div class="wrap">
                  <div class="card">
                    <h2>Giris</h2>
                    <p>Panele erisim icin oyun icinden <code>/adminmenu</code> yazarak
                       tek kullanimlik bir baglanti al.</p>
                    <p class="muted">Baglanti kisa omurludur ve yalnizca bir kez kullanilabilir.</p>
                  </div>
                </div>""");
    }

    static String error(String message) {
        return page("Aethel", """
                <div class="wrap">
                  <div class="card">
                    <h2>Hata</h2>
                    <p class="warn">%s</p>
                  </div>
                </div>""".formatted(escape(message)));
    }

    /**
     * Giris / kayit formu. Kayitta PIN iki kez istenir: yanlis yazilmis bir PIN
     * oyuncuyu hesabindan tamamen kilitler ve karma geri donusturulemez.
     */
    static String credentials(WebSession session, boolean registration, String error) {
        return credentials(session, registration, error, "PIN");
    }

    /** label, auth.mode degerine gore "PIN" ya da "Parola" olur. */
    static String credentials(WebSession session, boolean registration, String error, String label) {
        String title = registration ? "Kayit Ol" : "Giris Yap";
        String action = registration ? "/register" : "/login";
        String errorBlock = error == null ? ""
                : "<p class=\"warn\">" + escape(error) + "</p>";
        boolean numeric = "PIN".equalsIgnoreCase(label);
        String mode = numeric ? "numeric" : "text";
        String confirmField = !registration ? "" : ("""
                <label for="pin2">%s (tekrar)</label>
                <input id="pin2" name="pin2" type="password" inputmode="%s"
                       autocomplete="new-password" required>""").formatted(escape(label), mode);

        return page("Aethel · " + title, """
                <div class="wrap narrow">
                  <div class="card">
                    <h2>%s</h2>
                    <p class="muted">Hesap: <strong>%s</strong></p>
                    %s
                    <form method="post" action="%s" class="form">
                      <label for="pin">%s</label>
                      <input id="pin" name="pin" type="password" inputmode="%s"
                             autocomplete="%s" autofocus required>
                      %s
                      <button type="submit">%s</button>
                    </form>
                    <p class="muted small">Bu baglanti %d saniye sonra gecersiz olur.</p>
                  </div>
                </div>""".formatted(escape(title), escape(session.playerName()), errorBlock,
                action, escape(label), mode,
                registration ? "new-password" : "current-password",
                confirmField, escape(title), session.remainingSeconds()));
    }

    /** Panel iskeleti; veriler /api uclarindan tarayicida cekilir. */
    static String panel(WebSession session) {
        return page("Aethel · Panel", """
                <div class="wrap">
                  <div class="grid">
                    <div class="card">
                      <h2>Oturum</h2>
                      <p><strong>%s</strong> <span class="tag">%s</span></p>
                      <p class="muted">Kalan sure: <span id="expiry">%d</span> sn</p>
                    </div>
                    <div class="card">
                      <h2>Cevrimici Oyuncular</h2>
                      <table id="players"><thead><tr>
                        <th>Oyuncu</th><th>Dunya</th><th>Can</th>
                      </tr></thead><tbody></tbody></table>
                    </div>
                    <div class="card">
                      <h2>Item Tanimlari</h2>
                      <table id="items"><thead><tr>
                        <th>Kimlik</th><th>Materyal</th><th>Nadirlik</th>
                      </tr></thead><tbody></tbody></table>
                    </div>
                    <div class="card">
                      <h2>Bolgeler</h2>
                      <table id="regions"><thead><tr>
                        <th>Bolge</th><th>Sekil</th><th>Zorluk</th>
                      </tr></thead><tbody></tbody></table>
                    </div>
                  </div>
                </div>
                <script>
                  const rows = (id, data, fields) => {
                    const body = document.querySelector('#' + id + ' tbody');
                    body.innerHTML = data.map(entry =>
                      '<tr>' + fields.map(f => '<td>' + (entry[f] ?? '') + '</td>').join('') + '</tr>'
                    ).join('');
                  };
                  const load = async () => {
                    const get = async path => (await fetch(path)).json();
                    rows('players', await get('/api/players'), ['name','world','health']);
                    rows('items', await get('/api/items'), ['id','material','rarity']);
                    rows('regions', await get('/api/regions'), ['id','shape','difficulty']);
                  };
                  load();
                  setInterval(load, 5000);
                  const expiry = document.getElementById('expiry');
                  setInterval(() => {
                    const left = parseInt(expiry.textContent, 10) - 1;
                    expiry.textContent = Math.max(0, left);
                  }, 1000);
                </script>""".formatted(escape(session.playerName()),
                session.admin() ? "yetkili" : "oyuncu", session.remainingSeconds()));
    }

    private static String page(String title, String body) {
        return """
                <!doctype html>
                <html lang="tr"><head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  %s
                </head><body>
                  <header><h1>%s</h1><span class="muted">AethelCore</span></header>
                  %s
                </body></html>""".formatted(escape(title), STYLE, escape(title), body);
    }

    /** Kullanici verisi HTML'e gomulurken kacilir; panel bir enjeksiyon yuzeyi olmamali. */
    private static String escape(String raw) {
        return raw == null ? "" : raw
                .replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
}
