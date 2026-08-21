package net.aethel.core.modules.web;

import net.aethel.core.storage.Database;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Yetkili islemlerinin denetim kaydi. Envantere mudahale geri alinamaz bir islemdir;
 * kim ne yapti sorusunun cevabi her zaman kayitli olmalidir.
 */
final class AuditLog {

    /** Tek bir denetim satiri. */
    record Entry(long id, UUID actor, String actorName, UUID target,
                 String action, String details, long createdAt) {}

    private final Database database;
    private final boolean enabled;

    AuditLog(Database database, boolean enabled) {
        this.database = database;
        this.enabled = enabled;
    }

    CompletableFuture<Integer> record(UUID actor, String actorName, UUID target,
                                      String action, String details) {
        if (!enabled) return CompletableFuture.completedFuture(0);
        return database.update(
                "INSERT INTO core_admin_audit (actor, actor_name, target, action, details, created_at)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                stmt -> {
                    stmt.setString(1, actor.toString());
                    stmt.setString(2, actorName);
                    stmt.setString(3, target == null ? null : target.toString());
                    stmt.setString(4, action);
                    stmt.setString(5, details);
                    stmt.setLong(6, System.currentTimeMillis());
                });
    }

    CompletableFuture<List<Entry>> recent(int limit) {
        return database.query(
                "SELECT * FROM core_admin_audit ORDER BY created_at DESC LIMIT "
                        + Math.max(1, Math.min(500, limit)),
                Database.StatementBinder.NONE,
                rs -> new Entry(rs.getLong("id"),
                        UUID.fromString(rs.getString("actor")),
                        rs.getString("actor_name"),
                        rs.getString("target") == null ? null : UUID.fromString(rs.getString("target")),
                        rs.getString("action"),
                        rs.getString("details"),
                        rs.getLong("created_at")));
    }

    CompletableFuture<List<Entry>> forTarget(UUID target, int limit) {
        return database.query(
                "SELECT * FROM core_admin_audit WHERE target = ? ORDER BY created_at DESC LIMIT "
                        + Math.max(1, Math.min(500, limit)),
                stmt -> stmt.setString(1, target.toString()),
                rs -> new Entry(rs.getLong("id"),
                        UUID.fromString(rs.getString("actor")),
                        rs.getString("actor_name"),
                        target,
                        rs.getString("action"),
                        rs.getString("details"),
                        rs.getLong("created_at")));
    }
}
