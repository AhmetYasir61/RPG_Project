package net.aethel.core.modules.web;

import java.util.List;

/**
 * Web panelinin tablolari: denetim kaydi ve el konulan esya deposu.
 * El konulan esya SILINMEZ, kanit olarak saklanir ve geri verilebilir.
 */
final class WebSchema {

    private WebSchema() {}

    static final List<String> MIGRATIONS = List.of(
            """
            CREATE TABLE IF NOT EXISTS core_admin_audit (
              id {id},
              actor {uuid} NOT NULL,
              actor_name {uuid},
              target {uuid},
              action {uuid} NOT NULL,
              details {text},
              created_at BIGINT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS core_evidence (
              id {id},
              owner {uuid} NOT NULL,
              actor {uuid} NOT NULL,
              item_data {text} NOT NULL,
              reason {text},
              returned INTEGER NOT NULL DEFAULT 0,
              created_at BIGINT NOT NULL
            )"""
    );
}
