package net.aethel.core.modules.pcoins;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PCoins backend HTTP istemcisi. Her istek HMAC-SHA256 ile imzalanir ve zaman damgasi
 * tasir; boylece anahtar sizmadan istek taklidi ve tekrar saldirisi engellenir.
 */
final class PCoinClient {

    /** Saat farki bu esigi asarsa backend istegi reddeder (tekrar saldirisi korumasi). */
    private static final long MAX_CLOCK_SKEW_MILLIS = 5 * 60_000L;

    private final HttpClient http;
    private final Gson gson = new Gson();
    private final PCoinSettings settings;
    private final String apiKey;
    private final Logger log;

    PCoinClient(PCoinSettings settings, String apiKey, Logger log, java.util.concurrent.Executor executor) {
        this.settings = settings;
        this.apiKey = apiKey;
        this.log = log;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(settings.requestTimeoutSeconds))
                .executor(executor)
                .build();
    }

    CompletableFuture<Long> balance(UUID uuid) {
        return send("GET", "/coins/" + uuid, null)
                .thenApply(json -> json == null ? -1L : json.get("balance").getAsLong());
    }

    /** Harcama once rezervasyon olusturur; backend onaylamazsa hicbir sey verilmez. */
    CompletableFuture<JsonObject> reserve(UUID uuid, long amount, String sku, String reason) {
        JsonObject body = new JsonObject();
        body.addProperty("player", uuid.toString());
        body.addProperty("amount", amount);
        body.addProperty("sku", sku);
        body.addProperty("reason", reason);
        body.addProperty("idempotency_key", UUID.randomUUID().toString());
        return send("POST", "/coins/reserve", body);
    }

    CompletableFuture<JsonObject> commit(String reservationId) {
        JsonObject body = new JsonObject();
        body.addProperty("reservation", reservationId);
        return send("POST", "/coins/commit", body);
    }

    CompletableFuture<JsonObject> cancel(String reservationId) {
        JsonObject body = new JsonObject();
        body.addProperty("reservation", reservationId);
        return send("POST", "/coins/cancel", body);
    }

    /** Sitede yapilan satin almalarin bekleyen listesi; her biri islem id'si tasir. */
    CompletableFuture<JsonObject> pending(UUID uuid) {
        return send("GET", "/coins/" + uuid + "/pending", null);
    }

    CompletableFuture<JsonObject> acknowledge(UUID uuid, String transactionId) {
        JsonObject body = new JsonObject();
        body.addProperty("player", uuid.toString());
        body.addProperty("transaction", transactionId);
        return send("POST", "/coins/ack", body);
    }

    private CompletableFuture<JsonObject> send(String method, String path, JsonObject body) {
        String payload = body == null ? "" : gson.toJson(body);
        long timestamp = System.currentTimeMillis();
        String signature = sign(method + "\n" + path + "\n" + timestamp + "\n" + payload);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(settings.endpoint + path))
                .timeout(Duration.ofSeconds(settings.requestTimeoutSeconds))
                .header("Content-Type", "application/json")
                .header("X-Aethel-Timestamp", String.valueOf(timestamp))
                .header("X-Aethel-Signature", signature);

        HttpRequest request = "GET".equals(method)
                ? builder.GET().build()
                : builder.POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::parse)
                .exceptionally(error -> {
                    // Anahtar ve imza asla loglanmaz; yalnizca hata turu yazilir.
                    log.log(Level.WARNING, "PCoins istegi basarisiz: " + path
                            + " (" + error.getClass().getSimpleName() + ")");
                    return null;
                });
    }

    private JsonObject parse(HttpResponse<String> response) {
        if (response.statusCode() / 100 != 2) {
            log.warning("PCoins backend HTTP " + response.statusCode());
            return null;
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private String sign(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("PCoins imzasi olusturulamadi", e);
        }
    }

    static long maxClockSkewMillis() {
        return MAX_CLOCK_SKEW_MILLIS;
    }
}
