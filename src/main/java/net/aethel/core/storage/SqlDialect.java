package net.aethel.core.storage;

/**
 * MySQL ve SQLite arasindaki sema farklarini tek yerde toplar; modul migration'lari
 * ham SQL yerine bu sabitleri kullanarak iki motorda da calisir.
 */
public enum SqlDialect {

    MYSQL("BIGINT AUTO_INCREMENT PRIMARY KEY", "VARCHAR(36)", "LONGTEXT",
            "INSERT INTO", "ON DUPLICATE KEY UPDATE"),
    SQLITE("INTEGER PRIMARY KEY AUTOINCREMENT", "TEXT", "TEXT",
            "INSERT OR REPLACE INTO", "");

    private final String autoIncrementId;
    private final String uuidType;
    private final String textType;
    private final String upsertPrefix;
    private final String upsertSuffix;

    SqlDialect(String autoIncrementId, String uuidType, String textType,
               String upsertPrefix, String upsertSuffix) {
        this.autoIncrementId = autoIncrementId;
        this.uuidType = uuidType;
        this.textType = textType;
        this.upsertPrefix = upsertPrefix;
        this.upsertSuffix = upsertSuffix;
    }

    public String autoIncrementId() { return autoIncrementId; }
    public String uuidType() { return uuidType; }
    public String textType() { return textType; }
    public String upsertPrefix() { return upsertPrefix; }
    public String upsertSuffix() { return upsertSuffix; }

    /** Sema SQL'i icindeki {id} {uuid} {text} yer tutucularini motora gore doldurur. */
    public String apply(String template) {
        return template
                .replace("{id}", autoIncrementId)
                .replace("{uuid}", uuidType)
                .replace("{text}", textType);
    }
}
