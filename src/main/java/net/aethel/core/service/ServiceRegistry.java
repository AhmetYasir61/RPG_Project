package net.aethel.core.service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moduller arasi tek temas noktasi: bir modul digerinin sinifini degil, yalnizca
 * arayuzunu gorur. Sahip (owner) bilgisi tutuldugu icin bir modul kapatildiginda
 * sagladigi tum servisler tek cagride geri alinir.
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Registration<?>> services = new ConcurrentHashMap<>();

    /** Tek bir servis kaydi: arayuz, implementasyon ve saglayan modulun id'si. */
    public record Registration<T>(Class<T> type, T instance, String owner) {}

    public <T> void register(Class<T> type, T instance, String owner) {
        Registration<?> existing = services.get(type);
        if (existing != null) {
            throw new IllegalStateException(
                    "Servis zaten kayitli: " + type.getName() + " (sahip: " + existing.owner() + ")");
        }
        services.put(type, new Registration<>(type, instance, owner));
    }

    /** Servisi zorunlu olarak alir; yoksa hata firlatir. Yalnizca zorunlu bagimliliklar icin. */
    public <T> T get(Class<T> type) {
        return optional(type).orElseThrow(() -> new IllegalStateException(
                "Servis kayitli degil: " + type.getName() + " (modulu modules.yml icinde acik mi?)"));
    }

    /** Opsiyonel bagimliliklar icin. Modul kapaliyken cagiran taraf cokmez. */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> optional(Class<T> type) {
        Registration<?> reg = services.get(type);
        return reg == null ? Optional.empty() : Optional.of((T) reg.instance());
    }

    public boolean isRegistered(Class<?> type) {
        return services.containsKey(type);
    }

    /** Bir modulun tum servislerini kaldirir. Hot-disable akisinda cagrilir. */
    public int unregisterAll(String owner) {
        int removed = 0;
        for (Map.Entry<Class<?>, Registration<?>> e : Set.copyOf(services.entrySet())) {
            if (e.getValue().owner().equals(owner)) {
                services.remove(e.getKey());
                removed++;
            }
        }
        return removed;
    }

    public Map<Class<?>, Registration<?>> snapshot() {
        return Map.copyOf(services);
    }
}
