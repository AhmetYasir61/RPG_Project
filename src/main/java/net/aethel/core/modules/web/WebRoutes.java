package net.aethel.core.modules.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.javalin.Javalin;
import io.javalin.http.Context;
import net.aethel.core.api.AuthService;
import net.aethel.core.api.ItemService;
import net.aethel.core.api.ProfileService;
import net.aethel.core.api.RegionService;
import net.aethel.core.bootstrap.CoreContext;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Web panelinin HTTP uclari. Her ucta once oturum dogrulanir; kimliksiz istek
 * hicbir veriye ulasamaz ve yetkili islemleri denetim kaydina yazilir.
 */
final class WebRoutes {

    private static final String SESSION_COOKIE = "aethel_session";

    private final CoreContext ctx;
    private final WebSettings settings;
    private final Map<String, WebSession> sessions;
    private final AuditLog audit;
    private final EvidenceStore evidence;

    WebRoutes(CoreContext ctx, WebSettings settings, Map<String, WebSession> sessions,
              AuditLog audit, EvidenceStore evidence) {
        this.ctx = ctx;
        this.settings = settings;
        this.sessions = sessions;
        this.audit = audit;
        this.evidence = evidence;
    }

    void register(Javalin server) {
        server.get("/", context -> context.html(WebPages.landing()));
        server.get("/auth/{token}", this::authenticate);
        server.get("/login", context -> credentialPage(context, false));
        server.get("/register", context -> credentialPage(context, true));
        server.post("/login", context -> submitCredentials(context, false));
        server.post("/register", context -> submitCredentials(context, true));
        server.get("/panel", this::panel);

        server.get("/api/me", context -> withSession(context, session -> {
            JsonObject json = new JsonObject();
            json.addProperty("player", session.playerName());
            json.addProperty("admin", session.admin());
            json.addProperty("expires_in", session.remainingSeconds());
            context.json(json.toString());
        }));

        server.get("/api/players", context -> withAdmin(context, session ->
                context.json(playersJson().toString())));

        server.get("/api/player/{uuid}", context -> withAdmin(context, session ->
                context.json(playerJson(UUID.fromString(context.pathParam("uuid"))).toString())));

        server.get("/api/items", context -> withSession(context, session ->
                context.json(itemsJson().toString())));

        server.get("/api/regions", context -> withSession(context, session ->
                context.json(regionsJson().toString())));

        server.post("/api/player/{uuid}/seize/{slot}", this::seizeItem);
        server.get("/api/audit", context -> withAdmin(context, session ->
                context.json(auditJson().toString())));
    }

    /**
     * Tek kullanimlik jetonu tuketir, OTURUM OLUSTURUR ve cerez birakir.
     *
     * Oturumu burada olusturmak sart: jetonu dogrulayip cerezi birakmak tek basina
     * yetmez, cunku /panel cerezi oturum tablosunda arar. Tablo doldurulmazsa
     * kullanici dogru baglantiyi kullansa bile "once oyun icinden giris yap" gorur.
     */
    private void authenticate(Context context) {
        String token = context.pathParam("token");

        Optional<UUID> player = authModule().flatMap(module -> module.consumeWebToken(token));
        if (player.isEmpty()) {
            context.status(403).html(WebPages.error(
                    "Baglanti gecersiz ya da suresi dolmus. "
                            + "Oyun icinden /adminmenu yazarak yeni bir baglanti al."));
            return;
        }
        sessions.put(token, buildSession(player.get()));
        context.cookie(SESSION_COOKIE, token, settings.sessionMinutes * 60);

        // Kaydi olmayan oyuncu kayit ekranina, kayitli olan giris ekranina gider.
        // Jeton "kim" sorusunu cevaplar; "sifreyi biliyor mu" sorusunu bu ekran cevaplar.
        AuthService.State state = authService()
                .map(auth -> auth.state(player.get()))
                .orElse(AuthService.State.AWAITING_LOGIN);
        context.redirect(state == AuthService.State.UNREGISTERED ? "/register" : "/login");
    }

    /** Giris / kayit formu. Oturum yoksa form gosterilmez. */
    private void credentialPage(Context context, boolean registration) {
        Optional<WebSession> session = session(context);
        if (session.isEmpty()) {
            context.status(401).html(WebPages.error(
                    "Once oyun icinden /adminmenu yazarak baglanti al."));
            return;
        }
        if (session.get().authenticated()) {
            context.redirect("/panel");
            return;
        }
        context.html(WebPages.credentials(session.get(), registration, null));
    }

    /**
     * Formu isler. Kayit akisinda PIN iki kez istenir: yanlis yazilmis bir PIN
     * oyuncuyu hesabindan tamamen kilitler ve geri donusu yoktur.
     */
    private void submitCredentials(Context context, boolean registration) {
        Optional<WebSession> found = session(context);
        if (found.isEmpty()) {
            context.status(401).html(WebPages.error("Oturum suresi dolmus."));
            return;
        }
        WebSession session = found.get();
        String secret = context.formParam("pin");
        String confirm = context.formParam("pin2");

        if (secret == null || secret.isBlank()) {
            context.html(WebPages.credentials(session, registration, "PIN bos olamaz."));
            return;
        }
        if (registration && !secret.equals(confirm)) {
            context.html(WebPages.credentials(session, registration, "PIN'ler eslesmedi."));
            return;
        }
        Optional<AuthService> auth = authService();
        if (auth.isEmpty()) {
            context.status(503).html(WebPages.error("Kimlik servisi kullanilamiyor."));
            return;
        }
        boolean ok = (registration
                ? auth.get().register(session.player(), secret)
                : auth.get().login(session.player(), secret)).join();

        if (!ok) {
            context.html(WebPages.credentials(session, registration,
                    registration ? "Kayit basarisiz. PIN kurallara uymuyor olabilir."
                            : "PIN hatali."));
            return;
        }
        // Oyun ici durum da acilir: oyuncu tarayicidan cikmadan oynamaya baslayabilir.
        authModule().ifPresent(module -> module.markAuthenticated(session.player()));
        session.authenticate(isAdmin(session.player()));
        context.redirect("/panel");
    }

    private Optional<AuthService> authService() {
        return ctx.services().optional(AuthService.class);
    }

    private Optional<net.aethel.core.modules.auth.AuthModule> authModule() {
        return authService()
                .filter(net.aethel.core.modules.auth.AuthModule.class::isInstance)
                .map(net.aethel.core.modules.auth.AuthModule.class::cast);
    }

    private boolean isAdmin(UUID playerId) {
        var online = ctx.plugin().getServer().getPlayer(playerId);
        return online != null && online.hasPermission("aethel.admin.panel");
    }

    /** Oturum kaydini kurar; DOGRULANMAMIS baslar, PIN girilince acilir. */
    private WebSession buildSession(UUID playerId) {
        var online = ctx.plugin().getServer().getPlayer(playerId);
        String name = online != null ? online.getName()
                : ctx.services().optional(ProfileService.class)
                        .flatMap(profiles -> profiles.cached(playerId))
                        .map(net.aethel.core.api.PlayerProfile::name)
                        .orElse(playerId.toString().substring(0, 8));

        return new WebSession(playerId, name,
                System.currentTimeMillis() + settings.sessionMinutes * 60_000L);
    }

    private void panel(Context context) {
        Optional<WebSession> session = session(context);
        if (session.isEmpty()) {
            context.status(401).html(WebPages.error(
                    "Once oyun icinden /adminmenu yazarak baglanti al."));
            return;
        }
        if (!session.get().authenticated()) {
            context.redirect("/login");
            return;
        }
        context.html(WebPages.panel(session.get()));
    }

    private Optional<WebSession> session(Context context) {
        String token = context.cookie(SESSION_COOKIE);
        if (token == null) return Optional.empty();
        WebSession session = sessions.get(token);
        return session != null && session.valid() ? Optional.of(session) : Optional.empty();
    }

    /** Dogrulanmis oturum; PIN girmeden hicbir veri ucuna erisilemez. */
    private Optional<WebSession> authenticatedSession(Context context) {
        return session(context).filter(WebSession::authenticated);
    }

    private void withSession(Context context, java.util.function.Consumer<WebSession> action) {
        authenticatedSession(context).ifPresentOrElse(action,
                () -> context.status(401).result("unauthorized"));
    }

    private void withAdmin(Context context, java.util.function.Consumer<WebSession> action) {
        authenticatedSession(context).filter(WebSession::admin).ifPresentOrElse(action,
                () -> context.status(403).result("forbidden"));
    }

    /**
     * Envanter mudahalesi. Esya SILINMEZ, kanit deposuna tasinir; hatali bir islem
     * geri alinabilsin diye. Her islem denetim kaydina yazilir.
     */
    private void seizeItem(Context context) {
        withAdmin(context, session -> {
            if (!settings.allowInventoryEdit) {
                context.status(403).result("inventory-edit-disabled");
                return;
            }
            UUID target = UUID.fromString(context.pathParam("uuid"));
            int slot = Integer.parseInt(context.pathParam("slot"));
            String reason = context.queryParam("reason");

            // Envanter erisimi ana thread'de yapilmali; sonuc HTTP thread'ine donmez.
            ctx.scheduler().sync("web", () -> {
                var player = ctx.plugin().getServer().getPlayer(target);
                if (player == null) return;
                var item = player.getInventory().getItem(slot);
                if (item == null) return;

                player.getInventory().setItem(slot, null);
                evidence.seize(target, session.player(), item, reason);
                audit.record(session.player(), session.playerName(), target, "SEIZE_ITEM",
                        "slot=" + slot + " item=" + item.getType() + " reason=" + reason);
            });
            context.result("queued");
        });
    }

    private JsonArray playersJson() {
        JsonArray array = new JsonArray();
        ctx.plugin().getServer().getOnlinePlayers().forEach(player -> {
            JsonObject json = new JsonObject();
            json.addProperty("uuid", player.getUniqueId().toString());
            json.addProperty("name", player.getName());
            json.addProperty("world", player.getWorld().getName());
            json.addProperty("health", player.getHealth());
            array.add(json);
        });
        return array;
    }

    private JsonObject playerJson(UUID uuid) {
        JsonObject json = new JsonObject();
        var player = ctx.plugin().getServer().getPlayer(uuid);
        json.addProperty("uuid", uuid.toString());
        json.addProperty("online", player != null);
        if (player != null) {
            json.addProperty("name", player.getName());
            json.addProperty("world", player.getWorld().getName());
            JsonArray inventory = new JsonArray();
            var contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (contents[slot] == null) continue;
                JsonObject item = new JsonObject();
                item.addProperty("slot", slot);
                item.addProperty("type", contents[slot].getType().name());
                item.addProperty("amount", contents[slot].getAmount());
                inventory.add(item);
            }
            json.add("inventory", inventory);
        }
        ctx.services().optional(ProfileService.class)
                .flatMap(profiles -> profiles.cached(uuid))
                .ifPresent(profile -> {
                    json.addProperty("playtime", profile.playtimeSeconds());
                    json.addProperty("first_join", profile.firstJoin());
                });
        return json;
    }

    private JsonArray itemsJson() {
        JsonArray array = new JsonArray();
        ctx.services().optional(ItemService.class).ifPresent(items ->
                items.all().forEach(item -> {
                    JsonObject json = new JsonObject();
                    json.addProperty("id", item.fullId());
                    json.addProperty("display", item.displayName());
                    json.addProperty("material", item.baseMaterial());
                    json.addProperty("rarity", item.rarity());
                    array.add(json);
                }));
        return array;
    }

    private JsonArray regionsJson() {
        JsonArray array = new JsonArray();
        ctx.services().optional(RegionService.class).ifPresent(regions ->
                regions.regions().forEach(region -> {
                    JsonObject json = new JsonObject();
                    json.addProperty("id", region.id());
                    json.addProperty("world", region.world());
                    json.addProperty("shape", region.shape().getClass().getSimpleName());
                    json.addProperty("difficulty", region.difficultyId());
                    json.addProperty("priority", region.priority());
                    array.add(json);
                }));
        return array;
    }

    private JsonArray auditJson() {
        JsonArray array = new JsonArray();
        audit.recent(100).join().forEach(entry -> {
            JsonObject json = new JsonObject();
            json.addProperty("actor", entry.actorName());
            json.addProperty("action", entry.action());
            json.addProperty("details", entry.details());
            json.addProperty("at", entry.createdAt());
            array.add(json);
        });
        return array;
    }
}
