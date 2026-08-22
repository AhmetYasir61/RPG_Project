package net.aethel.core.modules.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import net.aethel.core.api.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panelin JSON API'si. Tum uclar oturum jetonu ister (WebModule.requireSession).
 * Yazma islemleri ana thread'de calisir ve AuditLog'a yazilir.
 *
 * GET  /api/schema                -> bolum ve alan semasi
 * GET  /api/records?section=items -> kayit listesi
 * POST /api/records?section=items -> kayit kaydet (govde: duz JSON nesnesi)
 * POST /api/delete?section=items  -> kayit sil (govde: {"id":"..."})
 * GET  /api/status                -> TPS, oyuncu, bellek, modul sagligi
 * GET  /api/logs                  -> son gunluk satirlari
 * POST /api/command               -> konsol komutu (govde: {"line":"..."})
 * POST /api/feature               -> ozellik anahtari (govde: {"key":"...","enabled":true})
 * POST /api/module                -> modul ac/kapa (govde: {"id":"...","enabled":true})
 * GET  /api/audit  /api/evidence  -> salt okunur kayitlar
 * GET/POST /api/notes             -> yonetici notlari (paneldeki not defteri)
 */
public final class PanelApi {

    private final WebModule web;
    private final YamlStore store;
    private final AuditLog audit;
    private final Gson gson = new Gson();

    public PanelApi(WebModule web, YamlStore store, AuditLog audit) {
        this.web = web;
        this.store = store;
        this.audit = audit;
    }

    /** WebRoutes.register() icinden cagrilir. */
    public void register(com.sun.net.httpserver.HttpServer server) {
        server.createContext("/api/schema", ex -> guarded(ex, this::schema));
        server.createContext("/api/records", ex -> guarded(ex, this::records));
        server.createContext("/api/delete", ex -> guarded(ex, this::delete));
        server.createContext("/api/status", ex -> guarded(ex, this::status));
        server.createContext("/api/logs", ex -> guarded(ex, this::logs));
        server.createContext("/api/command", ex -> guarded(ex, this::command));
        server.createContext("/api/feature", ex -> guarded(ex, this::feature));
        server.createContext("/api/module", ex -> guarded(ex, this::module));
        server.createContext("/api/audit", ex -> guarded(ex, e -> json(e, audit.recent(200))));
        server.createContext("/api/evidence", ex -> guarded(ex, e -> json(e, web.evidence().all())));
        server.createContext("/api/notes", ex -> guarded(ex, this::notes));
    }

    // ---- uclar ----

    private void schema(HttpExchange ex) throws IOException {
        json(ex, PanelSchema.sections().values());
    }

    private void records(HttpExchange ex) throws IOException {
        String id = param(ex, "section");
        PanelSchema.Section section = PanelSchema.section(id);
        if (section == null) { error(ex, 404, "bilinmeyen bolum: " + id); return; }

        if ("POST".equals(ex.getRequestMethod())) {
            Map<String, Object> body = readJson(ex);
            sync(() -> write(section, body));
            audit.write("SAVE_" + id.toUpperCase(), actor(ex), String.valueOf(body.get("id")), summarize(body));
            json(ex, Map.of("ok", true));
            return;
        }
        json(ex, read(section));
    }

    private void delete(HttpExchange ex) throws IOException {
        PanelSchema.Section section = PanelSchema.section(param(ex, "section"));
        if (section == null) { error(ex, 404, "bilinmeyen bolum"); return; }
        Map<String, Object> body = readJson(ex);
        String id = String.valueOf(body.get("id"));
        sync(() -> store.deleteRecord(resolve(section.file()), (String) body.get("__file"), id));
        audit.write("DELETE_" + section.id().toUpperCase(), actor(ex), id, "panelden silindi");
        json(ex, Map.of("ok", true));
    }

    private void status(HttpExchange ex) throws IOException {
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tps", Math.round(Bukkit.getTPS()[0] * 10) / 10.0);
        out.put("players", Bukkit.getOnlinePlayers().size());
        out.put("maxPlayers", Bukkit.getMaxPlayers());
        out.put("uptimeSeconds", (System.currentTimeMillis() - web.startedAt()) / 1000);
        out.put("memoryUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1048576);
        out.put("memoryMaxMb", rt.maxMemory() / 1048576);
        out.put("storage", web.core().storageType());
        out.put("modules", web.core().modules().all().stream().map(m -> Map.of(
                "id", m.id(), "enabled", m.enabled(), "state", m.state().name(),
                "depends", m.dependencies())).toList());
        json(ex, out);
    }

    private void logs(HttpExchange ex) throws IOException {
        json(ex, web.logBuffer().tail(200));
    }

    private void command(HttpExchange ex) throws IOException {
        String line = String.valueOf(readJson(ex).get("line")).replaceFirst("^/", "");
        sync(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line));
        audit.write("COMMAND", actor(ex), "console", line);
        json(ex, Map.of("ok", true));
    }

    private void feature(HttpExchange ex) throws IOException {
        Map<String, Object> body = readJson(ex);
        String key = String.valueOf(body.get("key"));
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        sync(() -> web.core().features().set(key, enabled));
        audit.write("FEATURE_SET", actor(ex), key, String.valueOf(enabled));
        json(ex, Map.of("ok", true));
    }

    private void module(HttpExchange ex) throws IOException {
        Map<String, Object> body = readJson(ex);
        String id = String.valueOf(body.get("id"));
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        sync(() -> {
            if (enabled) web.core().modules().enable(id); else web.core().modules().disable(id);
        });
        audit.write(enabled ? "MODULE_ENABLE" : "MODULE_DISABLE", actor(ex), id, "panelden");
        json(ex, Map.of("ok", true));
    }

    private void notes(HttpExchange ex) throws IOException {
        if ("POST".equals(ex.getRequestMethod())) {
            String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            web.notes().replaceAll(raw);
            json(ex, Map.of("ok", true));
            return;
        }
        raw(ex, web.notes().raw());
    }

    // ---- okuma / yazma ----

    private List<Map<String, Object>> read(PanelSchema.Section section) {
        return switch (section.kind()) {
            case FILE -> store.loadAll(resolve(section.file()));
            case CONFIG -> List.of(store.loadSection("config.yml", section.file()));
            case FEATURES -> web.core().features().all().entrySet().stream()
                    .map(e -> Map.<String, Object>of("id", e.getKey(), "enabled", e.getValue(),
                            "description", web.core().features().describe(e.getKey())))
                    .toList();
            case MODULES -> web.core().modules().all().stream()
                    .map(m -> Map.<String, Object>of("id", m.id(), "enabled", m.enabled(),
                            "state", m.state().name(), "depends", m.dependencies()))
                    .toList();
            case AUDIT -> audit.recent(200);
            case EVIDENCE -> web.evidence().all();
            case PLAYERS -> players();
        };
    }

    private void write(PanelSchema.Section section, Map<String, Object> body) throws Exception {
        switch (section.kind()) {
            case FILE -> store.saveRecord(resolve(section.file()), (String) body.get("__file"), body);
            case CONFIG -> {
                store.saveSection("config.yml", section.file(), body);
                web.core().reloadSettings(section.id());
            }
            case PLAYERS -> savePlayer(body);
            case FEATURES -> web.core().features()
                    .set(String.valueOf(body.get("id")), Boolean.TRUE.equals(body.get("enabled")));
            case MODULES -> {
                if (Boolean.TRUE.equals(body.get("enabled"))) web.core().modules().enable(String.valueOf(body.get("id")));
                else web.core().modules().disable(String.valueOf(body.get("id")));
            }
            case AUDIT, EVIDENCE -> throw new IllegalStateException("salt okunur bolum");
        }
    }

    private List<Map<String, Object>> players() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = web.core().profiles().get(p.getUniqueId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", p.getName());
            row.put("uuid", p.getUniqueId().toString());
            row.put("world", p.getWorld().getName());
            row.put("online", true);
            row.put("balance", web.core().economy().balance(p.getUniqueId()));
            row.put("groups", web.core().permissions().groupsOf(p.getUniqueId()));
            profile.data().forEach((k, v) -> row.put(k, v));
            List<String> inv = new ArrayList<>();
            var contents = p.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null) inv.add(i + " · " + contents[i].getType() + " ×" + contents[i].getAmount());
            }
            row.put("inventory", inv);
            out.add(row);
        }
        return out;
    }

    private void savePlayer(Map<String, Object> body) {
        var uuid = java.util.UUID.fromString(String.valueOf(body.get("uuid")));
        PlayerProfile profile = web.core().profiles().get(uuid);
        body.forEach((key, value) -> {
            if (key.startsWith("rpg:")) profile.set(key, value);
        });
        if (body.get("balance") instanceof Number n) web.core().economy().set(uuid, n.doubleValue());
        web.core().profiles().flush(uuid);
    }

    /** contents/%s/items -> contents/<namespace>/items */
    private String resolve(String pattern) {
        return pattern.contains("%s") ? String.format(pattern, web.core().namespace()) : pattern;
    }

    // ---- altyapi ----

    private interface Task { void run() throws Exception; }

    private void sync(Task task) {
        try {
            Bukkit.getScheduler().callSyncMethod(web.plugin(), () -> { task.run(); return null; }).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private interface Handler { void handle(HttpExchange ex) throws IOException; }

    private void guarded(HttpExchange ex, Handler handler) throws IOException {
        try {
            if (!web.requireSession(ex)) { error(ex, 401, "oturum gerekli"); return; }
            handler.handle(ex);
        } catch (Exception e) {
            error(ex, 500, String.valueOf(e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private String actor(HttpExchange ex) {
        return web.sessionName(ex);
    }

    private String summarize(Map<String, Object> body) {
        StringBuilder sb = new StringBuilder();
        body.forEach((k, v) -> {
            if (k.startsWith("__") || sb.length() > 180) return;
            sb.append(k).append('=').append(v).append(' ');
        });
        return sb.toString().trim();
    }

    private String param(HttpExchange ex, String name) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) return "";
        for (String part : q.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv[0].equals(name) && kv.length > 1) return kv[1];
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (raw.isBlank()) return Map.of();
            return gson.fromJson(raw, Map.class);
        }
    }

    private void json(HttpExchange ex, Object value) throws IOException {
        raw(ex, gson.toJson(value));
    }

    private void raw(HttpExchange ex, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
    }

    private void error(HttpExchange ex, int code, String message) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        byte[] bytes = gson.toJson(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
    }
}
