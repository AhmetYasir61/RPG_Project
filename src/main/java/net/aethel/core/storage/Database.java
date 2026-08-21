package net.aethel.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HikariCP tabanli veri erisimi. Tum sorgular sanal thread havuzunda calisir;
 * ana thread'de blocking cagri yapan hicbir metot disari acilmaz.
 */
public final class Database {

    private final Logger log;
    private final ExecutorService io;
    private final HikariDataSource dataSource;
    private final SqlDialect dialect;

    public Database(DatabaseSettings settings, File dataFolder, Logger log, ExecutorService io) {
        this.log = log;
        this.io = io;
        HikariConfig config = new HikariConfig();
        config.setPoolName("AethelCore-Pool");

        if (settings.isMySql()) {
            this.dialect = SqlDialect.MYSQL;
            config.setJdbcUrl("jdbc:mysql://" + settings.host + ":" + settings.port + "/"
                    + settings.database + "?useSSL=false&allowPublicKeyRetrieval=true"
                    + "&characterEncoding=utf8&rewriteBatchedStatements=true");
            config.setUsername(settings.username);
            config.setPassword(settings.password);
            config.setMaximumPoolSize(settings.poolSize);
        } else {
            this.dialect = SqlDialect.SQLITE;
            config.setJdbcUrl("jdbc:sqlite:" + new File(dataFolder, settings.sqliteFile).getAbsolutePath());
            // SQLite tek yazar destekler; havuzu 1'de tutmak kilit cakismalarini bitirir.
            config.setMaximumPoolSize(1);
        }
        config.setConnectionTimeout(10_000L);
        config.setLeakDetectionThreshold(30_000L);
        this.dataSource = new HikariDataSource(config);
        log.info("Veritabani hazir: " + dialect);
    }

    public SqlDialect dialect() {
        return dialect;
    }

    /** Sorgu calistirir ve satirlari mapper ile donusturur. */
    public <T> CompletableFuture<List<T>> query(String sql, StatementBinder binder, RowMapper<T> mapper) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                binder.bind(stmt);
                try (ResultSet rs = stmt.executeQuery()) {
                    List<T> results = new ArrayList<>();
                    while (rs.next()) results.add(mapper.map(rs));
                    return results;
                }
            } catch (SQLException e) {
                throw new DataAccessException("Sorgu basarisiz: " + sql, e);
            }
        }, io);
    }

    /** Tek satirlik sorgular icin kisayol; sonuc yoksa null iceren future doner. */
    public <T> CompletableFuture<T> queryOne(String sql, StatementBinder binder, RowMapper<T> mapper) {
        return query(sql, binder, mapper).thenApply(list -> list.isEmpty() ? null : list.get(0));
    }

    /** INSERT/UPDATE/DELETE; etkilenen satir sayisini doner. */
    public CompletableFuture<Integer> update(String sql, StatementBinder binder) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                binder.bind(stmt);
                return stmt.executeUpdate();
            } catch (SQLException e) {
                throw new DataAccessException("Guncelleme basarisiz: " + sql, e);
            }
        }, io);
    }

    /** Birden fazla ifadeyi tek transaction'da calistirir; hata halinde rollback yapar. */
    public CompletableFuture<Void> transaction(TransactionBody body) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                boolean previous = conn.getAutoCommit();
                conn.setAutoCommit(false);
                try {
                    body.execute(conn);
                    conn.commit();
                } catch (Exception e) {
                    conn.rollback();
                    throw new DataAccessException("Transaction geri alindi", e);
                } finally {
                    conn.setAutoCommit(previous);
                }
            } catch (SQLException e) {
                throw new DataAccessException("Baglanti alinamadi", e);
            }
        }, io);
    }

    /** Sunucu kapanirken cagrilir; bekleyen yazmalarin bitmesi icin havuz drain edilir. */
    public void close() {
        try {
            dataSource.close();
        } catch (Exception e) {
            log.log(Level.WARNING, "Veritabani kapatilirken hata", e);
        }
    }

    @FunctionalInterface public interface StatementBinder {
        void bind(PreparedStatement stmt) throws SQLException;
        StatementBinder NONE = stmt -> {};
    }

    @FunctionalInterface public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface public interface TransactionBody {
        void execute(Connection connection) throws Exception;
    }
}
