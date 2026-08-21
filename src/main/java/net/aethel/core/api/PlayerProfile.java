package net.aethel.core.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bir oyuncunun cekirdek verisi ve modullerin ona iliştirdigi nitelikler.
 * Bellekte tutulur, kirlendiginde periyodik olarak ve cikista diske yazilir.
 */
public final class PlayerProfile {

    private final UUID uuid;
    private String name;
    private final long firstJoin;
    private long lastJoin;
    private long playtimeSeconds;
    private final Map<String, String> attributes = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public PlayerProfile(UUID uuid, String name, long firstJoin, long lastJoin, long playtimeSeconds) {
        this.uuid = uuid;
        this.name = name;
        this.firstJoin = firstJoin;
        this.lastJoin = lastJoin;
        this.playtimeSeconds = playtimeSeconds;
    }

    public static PlayerProfile fresh(UUID uuid, String name) {
        long now = System.currentTimeMillis();
        PlayerProfile profile = new PlayerProfile(uuid, name, now, now, 0L);
        profile.dirty = true;
        return profile;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public long firstJoin() { return firstJoin; }
    public long lastJoin() { return lastJoin; }
    public long playtimeSeconds() { return playtimeSeconds; }
    public boolean dirty() { return dirty; }
    public Map<String, String> attributes() { return Map.copyOf(attributes); }

    public void name(String name) { this.name = name; markDirty(); }
    public void lastJoin(long lastJoin) { this.lastJoin = lastJoin; markDirty(); }
    public void addPlaytime(long seconds) { this.playtimeSeconds += seconds; markDirty(); }

    /**
     * Modul nitelikleri "modul:anahtar" bicimindedir (orn. "rpg:level").
     * Boylece moduller birbirinin verisini ezmeden ayni profili paylasir.
     */
    public Optional<String> attribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    public int attributeInt(String key, int fallback) {
        try {
            return attribute(key).map(Integer::parseInt).orElse(fallback);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public double attributeDouble(String key, double fallback) {
        try {
            return attribute(key).map(Double::parseDouble).orElse(fallback);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void attribute(String key, String value) {
        if (value == null) attributes.remove(key); else attributes.put(key, value);
        markDirty();
    }

    public void attribute(String key, Number value) {
        attribute(key, String.valueOf(value));
    }

    /** Yalnizca depolama katmani cagirir: yukleme sirasinda kirli isareti dogmasin diye. */
    public void loadAttribute(String key, String value) {
        attributes.put(key, value);
    }

    /** Niteligi siler. attribute(key, null) cagrisi asiri yukleme belirsizligi uretir. */
    public void removeAttribute(String key) {
        attributes.remove(key);
        markDirty();
    }

    public void markDirty() { this.dirty = true; }

    public void markClean() { this.dirty = false; }
}
