package net.aethel.core.modules.permissions;

import java.util.List;

/** Yetki tablolari: gruplar, grup izinleri, oyuncu gruplari ve oyuncu izinleri. */
final class PermissionSchema {

    private PermissionSchema() {}

    static final List<String> MIGRATIONS = List.of(
            """
            CREATE TABLE IF NOT EXISTS core_group (
              name {uuid} NOT NULL PRIMARY KEY,
              display_name {text},
              prefix {text},
              suffix {text},
              weight INTEGER NOT NULL DEFAULT 0,
              parents {text}
            );
            CREATE TABLE IF NOT EXISTS core_group_permission (
              group_name {uuid} NOT NULL,
              permission {uuid} NOT NULL,
              PRIMARY KEY (group_name, permission)
            );
            CREATE TABLE IF NOT EXISTS core_user_group (
              uuid {uuid} NOT NULL,
              group_name {uuid} NOT NULL,
              expires_at BIGINT,
              PRIMARY KEY (uuid, group_name)
            );
            CREATE TABLE IF NOT EXISTS core_user_permission (
              uuid {uuid} NOT NULL,
              permission {uuid} NOT NULL,
              world {uuid} NOT NULL DEFAULT '',
              value INTEGER NOT NULL DEFAULT 1,
              expires_at BIGINT,
              PRIMARY KEY (uuid, permission, world)
            )"""
    );
}
