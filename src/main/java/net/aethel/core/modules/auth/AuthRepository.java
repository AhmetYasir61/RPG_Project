package net.aethel.core.modules.auth;

import net.aethel.core.storage.Database;
import net.aethel.core.storage.SqlDialect;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kimlik kayitlarinin veri erisimi. Karma disinda hicbir sey saklanmaz; denetim
 * kaydi (kim ne zaman sifirladi) ayri tabloda tutulur.
 */
final class AuthRepository {

    /** Kayit satiri; karma ve son giris bilgisi. */
    record Record(UUID uuid, String secretHash, long registeredAt, Long lastLogin, String lastIp) {}

    private final Database database;

    AuthRepository(Database database) {
        this.database = database;
    }

    CompletableFuture<Optional<Record>> find(UUID uuid) {
        return database.queryOne(
                "SELECT * FROM core_auth WHERE uuid = ?",
                stmt -> stmt.setString(1, uuid.toString()),
                rs -> new Record(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("secret_hash"),
                        rs.getLong("registered_at"),
                        rs.getLong("last_login"),
                        rs.getString("last_ip")))
                .thenApply(Optional::ofNullable);
    }

    CompletableFuture<Integer> create(UUID uuid, String hash) {
        return database.update(
                "INSERT INTO core_auth (uuid, secret_hash, registered_at) VALUES (?, ?, ?)",
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, hash);
                    stmt.setLong(3, System.currentTimeMillis());
                });
    }

    CompletableFuture<Integer> updateSecret(UUID uuid, String hash) {
        return database.update(
                "UPDATE core_auth SET secret_hash = ? WHERE uuid = ?",
                stmt -> {
                    stmt.setString(1, hash);
                    stmt.setString(2, uuid.toString());
                });
    }

    CompletableFuture<Integer> touchLogin(UUID uuid, String ip) {
        return database.update(
                "UPDATE core_auth SET last_login = ?, last_ip = ? WHERE uuid = ?",
                stmt -> {
                    stmt.setLong(1, System.currentTimeMillis());
                    stmt.setString(2, ip);
                    stmt.setString(3, uuid.toString());
                });
    }

    CompletableFuture<Integer> delete(UUID uuid) {
        return database.update("DELETE FROM core_auth WHERE uuid = ?",
                stmt -> stmt.setString(1, uuid.toString()));
    }

    /** Yetkili islemleri ve sifirlamalar buraya yazilir; kayit hicbir zaman silinmez. */
    CompletableFuture<Integer> audit(UUID uuid, String action, String actor) {
        String sql = database.dialect() == SqlDialect.MYSQL
                ? "INSERT INTO core_auth_audit (uuid, action, actor, created_at) VALUES (?, ?, ?, ?)"
                : "INSERT INTO core_auth_audit (uuid, action, actor, created_at) VALUES (?, ?, ?, ?)";
        return database.update(sql, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, action);
            stmt.setString(3, actor);
            stmt.setLong(4, System.currentTimeMillis());
        });
    }
}
