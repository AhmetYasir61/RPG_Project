package net.aethel.core.modules.auth;

import java.util.List;

/** Kimlik dogrulama tablolari. Karma degeri disinda hicbir sir saklanmaz. */
final class AuthSchema {

    private AuthSchema() {}

    static final List<String> MIGRATIONS = List.of(
            """
            CREATE TABLE IF NOT EXISTS core_auth (
              uuid {uuid} NOT NULL PRIMARY KEY,
              secret_hash {text} NOT NULL,
              registered_at BIGINT NOT NULL,
              last_login BIGINT,
              last_ip {uuid}
            );
            CREATE TABLE IF NOT EXISTS core_auth_audit (
              id {id},
              uuid {uuid} NOT NULL,
              action {uuid} NOT NULL,
              actor {uuid},
              created_at BIGINT NOT NULL
            )"""
    );
}
