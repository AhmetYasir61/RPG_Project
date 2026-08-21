package net.aethel.core.api;

import net.aethel.core.event.EventBus;
import net.aethel.core.service.ServiceRegistry;

import java.util.Optional;

/**
 * AethelApi'nin cekirdek ici implementasyonu. ServiceRegistry ve EventBus'a ince
 * bir kabuk gecirir; addon'lar ic siniflara dogrudan bagimli olmaz.
 */
final class ApiImpl implements AethelApi {

    private final ServiceRegistry registry;
    private final EventBus events;
    private final String version;

    ApiImpl(ServiceRegistry registry, EventBus events, String version) {
        this.registry = registry;
        this.events = events;
        this.version = version;
    }

    @Override
    public <T> T service(Class<T> type) {
        return registry.get(type);
    }

    @Override
    public <T> Optional<T> optionalService(Class<T> type) {
        return registry.optional(type);
    }

    @Override
    public <T> void provide(Class<T> type, T implementation, String owner) {
        registry.register(type, implementation, owner);
    }

    @Override
    public void subscribe(String owner, Object listener) {
        events.register(owner, listener);
    }

    @Override
    public String coreVersion() {
        return version;
    }
}
