package net.aethel.core.modules.permissions;

import net.aethel.core.api.PermissionService.Group;
import net.aethel.core.api.PermissionService.TimedNode;
import net.aethel.core.storage.Database;
import net.aethel.core.storage.SqlDialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Yetki verisinin SQL erisimi. Gruplar acilista topluca yuklenir (sayilari azdir),
 * oyuncu verisi ise girise gore tek tek cekilir.
 */
final class PermissionRepository {

    private final Database database;

    PermissionRepository(Database database) {
        this.database = database;
    }

    CompletableFuture<List<Group>> loadGroups() {
        return database.query("SELECT * FROM core_group", Database.StatementBinder.NONE,
                        rs -> new Group(
                                rs.getString("name"),
                                rs.getString("display_name"),
                                rs.getString("prefix"),
                                rs.getString("suffix"),
                                rs.getInt("weight"),
                                split(rs.getString("parents")),
                                new ArrayList<>()))
                .thenCompose(this::attachPermissions);
    }

    private CompletableFuture<List<Group>> attachPermissions(List<Group> groups) {
        return database.query("SELECT group_name, permission FROM core_group_permission",
                Database.StatementBinder.NONE,
                rs -> Map.entry(rs.getString("group_name"), rs.getString("permission")))
                .thenApply(rows -> {
                    List<Group> result = new ArrayList<>(groups.size());
                    for (Group group : groups) {
                        List<String> permissions = rows.stream()
                                .filter(entry -> entry.getKey().equalsIgnoreCase(group.name()))
                                .map(Map.Entry::getValue).toList();
                        result.add(new Group(group.name(), group.displayName(), group.prefix(),
                                group.suffix(), group.weight(), group.parents(), permissions));
                    }
                    return result;
                });
    }

    CompletableFuture<Void> saveGroup(Group group) {
        String upsert = database.dialect() == SqlDialect.MYSQL
                ? "INSERT INTO core_group (name, display_name, prefix, suffix, weight, parents)"
                        + " VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE"
                        + " display_name = VALUES(display_name), prefix = VALUES(prefix),"
                        + " suffix = VALUES(suffix), weight = VALUES(weight), parents = VALUES(parents)"
                : "INSERT OR REPLACE INTO core_group"
                        + " (name, display_name, prefix, suffix, weight, parents) VALUES (?, ?, ?, ?, ?, ?)";
        return database.transaction(conn -> {
            try (var stmt = conn.prepareStatement(upsert)) {
                stmt.setString(1, group.name());
                stmt.setString(2, group.displayName());
                stmt.setString(3, group.prefix());
                stmt.setString(4, group.suffix());
                stmt.setInt(5, group.weight());
                stmt.setString(6, String.join(",", group.parents()));
                stmt.executeUpdate();
            }
            try (var delete = conn.prepareStatement(
                    "DELETE FROM core_group_permission WHERE group_name = ?")) {
                delete.setString(1, group.name());
                delete.executeUpdate();
            }
            try (var insert = conn.prepareStatement(
                    "INSERT INTO core_group_permission (group_name, permission) VALUES (?, ?)")) {
                for (String permission : group.permissions()) {
                    insert.setString(1, group.name());
                    insert.setString(2, permission);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        });
    }

    CompletableFuture<Integer> deleteGroup(String name) {
        return database.update("DELETE FROM core_group WHERE name = ?",
                stmt -> stmt.setString(1, name));
    }

    CompletableFuture<List<Map.Entry<String, Long>>> userGroups(UUID uuid) {
        return database.query(
                "SELECT group_name, expires_at FROM core_user_group WHERE uuid = ?",
                stmt -> stmt.setString(1, uuid.toString()),
                rs -> {
                    long expires = rs.getLong("expires_at");
                    return Map.entry(rs.getString("group_name"),
                            rs.wasNull() ? Long.MAX_VALUE : expires);
                });
    }

    CompletableFuture<List<TimedNode>> userNodes(UUID uuid) {
        return database.query(
                "SELECT permission, world, value, expires_at FROM core_user_permission WHERE uuid = ?",
                stmt -> stmt.setString(1, uuid.toString()),
                rs -> {
                    long expires = rs.getLong("expires_at");
                    boolean hasExpiry = !rs.wasNull();
                    String permission = rs.getInt("value") == 1
                            ? rs.getString("permission")
                            : "-" + rs.getString("permission");
                    return new TimedNode(permission, rs.getString("world"),
                            hasExpiry ? expires : null);
                });
    }

    CompletableFuture<Integer> addUserGroup(UUID uuid, String group, Long expiresAt) {
        String sql = database.dialect() == SqlDialect.MYSQL
                ? "INSERT INTO core_user_group (uuid, group_name, expires_at) VALUES (?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)"
                : "INSERT OR REPLACE INTO core_user_group (uuid, group_name, expires_at) VALUES (?, ?, ?)";
        return database.update(sql, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, group);
            if (expiresAt == null) stmt.setNull(3, java.sql.Types.BIGINT);
            else stmt.setLong(3, expiresAt);
        });
    }

    CompletableFuture<Integer> removeUserGroup(UUID uuid, String group) {
        return database.update("DELETE FROM core_user_group WHERE uuid = ? AND group_name = ?",
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, group);
                });
    }

    CompletableFuture<Integer> setUserPermission(UUID uuid, String permission, boolean value,
                                                 String world, Long expiresAt) {
        String sql = database.dialect() == SqlDialect.MYSQL
                ? "INSERT INTO core_user_permission (uuid, permission, world, value, expires_at)"
                        + " VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE"
                        + " value = VALUES(value), expires_at = VALUES(expires_at)"
                : "INSERT OR REPLACE INTO core_user_permission"
                        + " (uuid, permission, world, value, expires_at) VALUES (?, ?, ?, ?, ?)";
        return database.update(sql, stmt -> {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, permission);
            stmt.setString(3, world == null ? "" : world);
            stmt.setInt(4, value ? 1 : 0);
            if (expiresAt == null) stmt.setNull(5, java.sql.Types.BIGINT);
            else stmt.setLong(5, expiresAt);
        });
    }

    CompletableFuture<Integer> unsetUserPermission(UUID uuid, String permission, String world) {
        return database.update(
                "DELETE FROM core_user_permission WHERE uuid = ? AND permission = ? AND world = ?",
                stmt -> {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, permission);
                    stmt.setString(3, world == null ? "" : world);
                });
    }

    private List<String> split(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return List.of(raw.split(","));
    }
}
