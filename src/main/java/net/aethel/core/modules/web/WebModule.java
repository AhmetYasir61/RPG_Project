package net.aethel.core.modules.web;

import io.javalin.Javalin;
import net.aethel.core.api.PanelMode;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.config.ConfigMigration;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Web yonetim paneli. Yalnizca admin.mode=WEB iken calisir; GUI modunda port hic
 * dinlenmez, boylece ayni kaydin iki yerden duzenlenmesi mumkun olmaz.
 */
@ModuleInfo(id = "web", name = "Web Paneli", depends = {"profile"}, softDepends = {"panel"})
public final class WebModule implements Module {

    private final WebSettings settings = new WebSettings();
    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();
    private Javalin server;
    private AuditLog audit;
    private LogBuffer logs;
    private EvidenceStore evidence;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        // Ayarlar cekirdek config.yml icindeki admin.web.* bolumunden gelir.
        ctx.config().bind("config.yml", settings);
    }

    @Override
    public void onEnable(CoreContext ctx) {
        PanelMode mode = PanelMode.parse(ctx.config().get("config.yml").yaml()
                .getString("admin.mode", "GUI"));
        if (!mode.isWeb()) {
            ctx.logger().info("admin.mode=GUI oldugu icin web paneli baslatilmadi.");
            return;
        }
        ctx.schema().migrate("web", WebSchema.MIGRATIONS);
        this.audit = new AuditLog(ctx.database(), settings.auditLog);
        this.evidence = new EvidenceStore(ctx.database());
        // Panelin gunluk penceresi cekirdek logger'ini dinler; disk okunmaz.
        this.logs = new LogBuffer(ctx.logger());

        startServer();
        // Suresi dolan oturumlar temizlenir; sizan bir cerez sonsuza kadar gecerli olmaz.
        ctx.scheduler().repeating("web", 20L * 60, 20L * 60,
                () -> sessions.values().removeIf(session -> !session.valid()));
    }

    @Override
    public void onDisable(CoreContext ctx) {
        if (server != null) {
            server.stop();
            server = null;
        }
        sessions.clear();
        if (logs != null) {
            logs.close();
            logs = null;
        }
    }

    /**
     * Javalin gomulu sunucu olarak calisir. Tum istekler kendi thread havuzunda
     * islenir; hicbir HTTP isi ana thread'e dokunmaz. Oyun durumu gerektiginde
     * scheduler uzerinden ana thread'e gecilir.
     */
    private void startServer() {
        server = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.http.maxRequestSize = settings.maxUploadMegabytes * 1024L * 1024L;
            // enableWebjars() KULLANILMIYOR: jar icinde META-INF/resources/webjars
            // klasoru olmadigi icin Javalin baslarken patliyor. Panelin CSS'i zaten
            // WebPages icinde gomulu, harici varlik gerekmiyor.
        });

        WebRoutes routes = new WebRoutes(ctx, settings, sessions, audit, evidence, logs);
        routes.register(server);

        server.start(settings.bind, settings.port);

        // bind adresi (0.0.0.0) "tum arayuzlerde dinle" demektir, tarayiciya
        // yazilabilecek bir adres degildir; erisim adresini ayri gosteriyoruz.
        var yaml = ctx.config().get("config.yml").yaml();
        String publicUrl = settings.publicUrl;

        ctx.logger().info("Web paneli dinlemede: " + settings.bind + ":" + settings.port);
        ctx.logger().info("Panel adresi: " + (publicUrl.isBlank()
                ? "http://127.0.0.1:" + settings.port + " (admin.web.public-url bos)"
                : publicUrl));

        WebConfigCheck.validate(publicUrl, settings.bind, settings.port, ctx.logger());
        WebConfigCheck.compareHosts(yaml.getString("resource-pack.public-host", ""),
                publicUrl, ctx.logger());
    }

    /**
     * Oturum tablosu WebRoutes tarafindan doldurulur (jeton tuketimi orada yapilir).
     * Burada bir "consumeToken" sarmalayicisi vardi ama hicbir yerden cagrilmiyordu;
     * calisiyormus gibi duran olu kod, oturumun hic olusmamasina yol acmisti.
     */
}
