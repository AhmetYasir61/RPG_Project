package net.aethel.core.storage;

import java.sql.Statement;
import java.util.List;
import java.util.logging.Logger;

/**
 * Modul basina sema surumlerini takip eder ve eksik migration'lari sirayla uygular.
 * Uygulanan surumler core_schema tablosunda tutulur, tekrar calistirilmaz.
 */
public final class SchemaManager {

    private static final String CREATE_TRACKER = """
            CREATE TABLE IF NOT EXISTS core_schema (
              module {uuid} NOT NULL,
              version INTEGER NOT NULL,
              applied_at BIGINT NOT NULL,
              PRIMARY KEY (module, version)
            )""";

    private final Database database;
    private final Logger log;

    public SchemaManager(Database database, Logger log) {
        this.database = database;
        this.log = log;
        database.transaction(conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(database.dialect().apply(CREATE_TRACKER));
            }
        }).join();
    }

    /**
     * migrations listesindeki index+1 surum numarasidir. Yeni sema eklerken listenin
     * SONUNA eklenir; mevcut girisler asla degistirilmez, aksi halde surum takibi bozulur.
     */
    public void migrate(String moduleId, List<String> migrations) {
        Integer current = database.queryOne(
                "SELECT MAX(version) AS v FROM core_schema WHERE module = ?",
                stmt -> stmt.setString(1, moduleId),
                rs -> rs.getInt("v")).join();
        int from = current == null ? 0 : current;

        for (int version = from + 1; version <= migrations.size(); version++) {
            String sql = database.dialect().apply(migrations.get(version - 1));
            int applied = version;
            database.transaction(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    for (String part : sql.split(";\\s*\\n")) {
                        if (!part.isBlank()) stmt.execute(part);
                    }
                }
                try (var insert = conn.prepareStatement(
                        "INSERT INTO core_schema (module, version, applied_at) VALUES (?, ?, ?)")) {
                    insert.setString(1, moduleId);
                    insert.setInt(2, applied);
                    insert.setLong(3, System.currentTimeMillis());
                    insert.executeUpdate();
                }
            }).join();
            log.info("Sema uygulandi: " + moduleId + " v" + version);
        }
    }
}
