package net.aethel.core.storage;

import net.aethel.core.config.ConfigValue;

/**
 * Veritabani baglanti ayarlari. config.yml -> storage.* altindan doldurulur;
 * type MYSQL degilse tum MySQL alanlari yoksayilir ve SQLite dosyasi kullanilir.
 */
public final class DatabaseSettings {

    /** MYSQL veya SQLITE. MySQL erisilemezse cekirdek SQLite'a duser. */
    @ConfigValue("storage.type")
    public String type = "SQLITE";

    @ConfigValue("storage.mysql.host")
    public String host = "127.0.0.1";

    @ConfigValue("storage.mysql.port")
    public int port = 3306;

    @ConfigValue("storage.mysql.database")
    public String database = "aethel";

    @ConfigValue("storage.mysql.username")
    public String username = "aethel";

    @ConfigValue("storage.mysql.password")
    public String password = "";

    @ConfigValue("storage.mysql.pool-size")
    public int poolSize = 10;

    @ConfigValue("storage.sqlite.file")
    public String sqliteFile = "data.db";

    public boolean isMySql() {
        return "MYSQL".equalsIgnoreCase(type);
    }
}
