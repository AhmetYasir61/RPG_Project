package net.aethel.core.modules.profile;

import java.util.List;

/**
 * Profil tablolarinin sema surumleri. Yeni sema DAIMA listenin sonuna eklenir;
 * mevcut girisler degistirilmez, aksi halde surum takibi bozulur.
 */
final class ProfileSchema {

    private ProfileSchema() {}

    static final List<String> MIGRATIONS = List.of(
            """
            CREATE TABLE IF NOT EXISTS core_profile (
              uuid {uuid} NOT NULL PRIMARY KEY,
              name {uuid} NOT NULL,
              first_join BIGINT NOT NULL,
              last_join BIGINT NOT NULL,
              playtime_seconds BIGINT NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS core_profile_attribute (
              uuid {uuid} NOT NULL,
              attr_key {uuid} NOT NULL,
              attr_value {text},
              PRIMARY KEY (uuid, attr_key)
            )"""
    );
}
