package net.aethel.core.modules.economy;

import java.util.List;

/** Ekonomi tablolari: bakiye ve islem gecmisi. Bakiye profil ile ayni uuid'ye baglidir. */
final class EconomySchema {

    private EconomySchema() {}

    static final List<String> MIGRATIONS = List.of(
            """
            CREATE TABLE IF NOT EXISTS core_balance (
              uuid {uuid} NOT NULL PRIMARY KEY,
              balance DOUBLE NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS core_transaction (
              id {id},
              uuid {uuid} NOT NULL,
              amount DOUBLE NOT NULL,
              balance_after DOUBLE NOT NULL,
              reason {text},
              created_at BIGINT NOT NULL
            )"""
    );
}
