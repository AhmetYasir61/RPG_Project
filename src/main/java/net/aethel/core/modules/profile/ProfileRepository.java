package net.aethel.core.modules.profile;

import net.aethel.core.api.PlayerProfile;
import net.aethel.core.storage.Database;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Profil verisinin SQL erisimi. Tum cagrilar asenkron; ana thread hicbir zaman
 * veritabanini beklemez. Nitelikler ayri tabloda tutulur, toplu yazilir.
 */
final class ProfileRepository {

    private final Database database;

    ProfileRepository(Database database) {
        this.database = database;
    }

    CompletableFuture<Optional<PlayerProfile>> find(UUID uuid) {
        return database.queryOne(
                "SELECT * FROM core_profile WHERE uuid = ?",
                stmt -> stmt.setString(1, uuid.toString()),
                rs -> new PlayerProfile(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        rs.getLong("first_join"),
                        rs.getLong("last_join"),
                        rs.getLong("playtime_seconds")))
                .thenCompose(profile -> profile == null
                        ? CompletableFuture.completedFuture(Optional.<PlayerProfile>empty())
                        : loadAttributes(profile).thenApply(Optional::of));
    }

    CompletableFuture<Optional<PlayerProfile>> findByName(String name) {
        return database.queryOne(
                "SELECT uuid FROM core_profile WHERE LOWER(name) = LOWER(?)",
                stmt -> stmt.setString(1, name),
                rs -> UUID.fromString(rs.getString("uuid")))
                .thenCompose(uuid -> uuid == null
                        ? CompletableFuture.completedFuture(Optional.<PlayerProfile>empty())
                        : find(uuid));
    }

    private CompletableFuture<PlayerProfile> loadAttributes(PlayerProfile profile) {
        return database.query(
                "SELECT attr_key, attr_value FROM core_profile_attribute WHERE uuid = ?",
                stmt -> stmt.setString(1, profile.uuid().toString()),
                rs -> Map.entry(rs.getString("attr_key"),
                        Optional.ofNullable(rs.getString("attr_value")).orElse("")))
                .thenApply(rows -> {
                    rows.forEach(entry -> profile.loadAttribute(entry.getKey(), entry.getValue()));
                    profile.markClean();
                    return profile;
                });
    }

    /**
     * Profili ve tum niteliklerini tek transaction'da yazar. Nitelikler upsert edilir;
     * silinen nitelikler icin once ilgili satirlar temizlenir.
     */
    CompletableFuture<Void> save(PlayerProfile profile) {
        Map<String, String> attributes = profile.attributes();
        return database.transaction(conn -> {
            try (var stmt = conn.prepareStatement(profileUpsert())) {
                stmt.setString(1, profile.uuid().toString());
                stmt.setString(2, profile.name());
                stmt.setLong(3, profile.firstJoin());
                stmt.setLong(4, profile.lastJoin());
                stmt.setLong(5, profile.playtimeSeconds());
                if (database.dialect() == net.aethel.core.storage.SqlDialect.MYSQL) {
                    stmt.setString(6, profile.name());
                    stmt.setLong(7, profile.lastJoin());
                    stmt.setLong(8, profile.playtimeSeconds());
                }
                stmt.executeUpdate();
            }
            try (var delete = conn.prepareStatement(
                    "DELETE FROM core_profile_attribute WHERE uuid = ?")) {
                delete.setString(1, profile.uuid().toString());
                delete.executeUpdate();
            }
            if (attributes.isEmpty()) return;
            try (var insert = conn.prepareStatement(
                    "INSERT INTO core_profile_attribute (uuid, attr_key, attr_value) VALUES (?, ?, ?)")) {
                for (Map.Entry<String, String> entry : attributes.entrySet()) {
                    insert.setString(1, profile.uuid().toString());
                    insert.setString(2, entry.getKey());
                    insert.setString(3, entry.getValue());
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }).thenRun(profile::markClean);
    }

    /** Iki motorun upsert sozdizimi farkli; SqlDialect bunu tek yerde tasir. */
    private String profileUpsert() {
        if (database.dialect() == net.aethel.core.storage.SqlDialect.MYSQL) {
            return "INSERT INTO core_profile (uuid, name, first_join, last_join, playtime_seconds)"
                    + " VALUES (?, ?, ?, ?, ?)"
                    + " ON DUPLICATE KEY UPDATE name = ?, last_join = ?, playtime_seconds = ?";
        }
        return "INSERT OR REPLACE INTO core_profile"
                + " (uuid, name, first_join, last_join, playtime_seconds) VALUES (?, ?, ?, ?, ?)";
    }

    CompletableFuture<List<PlayerProfile>> topByPlaytime(int limit) {
        return database.query(
                "SELECT * FROM core_profile ORDER BY playtime_seconds DESC LIMIT " + Math.max(1, limit),
                Database.StatementBinder.NONE,
                rs -> new PlayerProfile(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("name"),
                        rs.getLong("first_join"),
                        rs.getLong("last_join"),
                        rs.getLong("playtime_seconds")));
    }
}
