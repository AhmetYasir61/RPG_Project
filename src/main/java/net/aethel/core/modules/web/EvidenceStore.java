package net.aethel.core.modules.web;

import net.aethel.core.storage.Database;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * El konulan esyalarin deposu. Esya SILINMEZ: kanit olarak saklanir, gerekirse
 * sahibine geri verilir. Boylece hatali bir mudahale telafi edilebilir.
 */
final class EvidenceStore {

    /** Saklanan bir esya kaydi. */
    record Evidence(long id, UUID owner, UUID actor, ItemStack item,
                    String reason, boolean returned, long createdAt) {}

    private final Database database;

    EvidenceStore(Database database) {
        this.database = database;
    }

    CompletableFuture<Integer> seize(UUID owner, UUID actor, ItemStack item, String reason) {
        String encoded = encode(item);
        return database.update(
                "INSERT INTO core_evidence (owner, actor, item_data, reason, returned, created_at)"
                        + " VALUES (?, ?, ?, ?, 0, ?)",
                stmt -> {
                    stmt.setString(1, owner.toString());
                    stmt.setString(2, actor.toString());
                    stmt.setString(3, encoded);
                    stmt.setString(4, reason);
                    stmt.setLong(5, System.currentTimeMillis());
                });
    }

    CompletableFuture<List<Evidence>> of(UUID owner) {
        return database.query(
                "SELECT * FROM core_evidence WHERE owner = ? ORDER BY created_at DESC",
                stmt -> stmt.setString(1, owner.toString()),
                rs -> new Evidence(rs.getLong("id"),
                        UUID.fromString(rs.getString("owner")),
                        UUID.fromString(rs.getString("actor")),
                        decode(rs.getString("item_data")).orElse(null),
                        rs.getString("reason"),
                        rs.getInt("returned") == 1,
                        rs.getLong("created_at")));
    }

    /** Panelin kanit listesi icin: en yeni kayitlar basta. */
    CompletableFuture<List<Evidence>> recent(int limit) {
        return database.query(
                "SELECT * FROM core_evidence ORDER BY created_at DESC LIMIT "
                        + Math.max(1, Math.min(500, limit)),
                Database.StatementBinder.NONE,
                rs -> new Evidence(rs.getLong("id"),
                        UUID.fromString(rs.getString("owner")),
                        UUID.fromString(rs.getString("actor")),
                        decode(rs.getString("item_data")).orElse(null),
                        rs.getString("reason"),
                        rs.getInt("returned") == 1,
                        rs.getLong("created_at")));
    }

    CompletableFuture<Integer> markReturned(long id) {
        return database.update("UPDATE core_evidence SET returned = 1 WHERE id = ?",
                stmt -> stmt.setLong(1, id));
    }

    /**
     * Bukkit'in kendi serilestirmesi kullanilir: item'in tum NBT'si, custom PDC
     * kimligi ve bilesenleri korunur. Elle alan alan kopyalamak veri kaybettirirdi.
     */
    private String encode(ItemStack item) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(item);
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new net.aethel.core.storage.DataAccessException("Esya serilestirilemedi", e);
        }
    }

    private Optional<ItemStack> decode(String encoded) {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
            return Optional.of((ItemStack) in.readObject());
        } catch (IOException | ClassNotFoundException e) {
            return Optional.empty();
        }
    }
}
