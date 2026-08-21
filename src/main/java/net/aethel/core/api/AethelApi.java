package net.aethel.core.api;

import net.aethel.core.service.ServiceRegistry;

/**
 * Addon'larin cekirdege girdigi tek kapi. Bu paketteki her sey kararlidir: imza
 * degisirse ana surum artar. Ic paketlere (bootstrap, module, storage) addon dokunmaz.
 */
public interface AethelApi {

    /** Zorunlu servis erisimi; servis yoksa hata firlatir. */
    <T> T service(Class<T> type);

    /** Modul kapali olabilecek servisler icin guvenli erisim. */
    <T> java.util.Optional<T> optionalService(Class<T> type);

    /** Addon kendi servisini kaydeder; sahip adi addon plugin adidir. */
    <T> void provide(Class<T> type, T implementation, String owner);

    /** Ic event otobusune erisim; addon @Subscribe dinleyicisi kaydedebilir. */
    void subscribe(String owner, Object listener);

    /** Cekirdek surumu, uyumluluk kontrolu icin. */
    String coreVersion();

    /** Yalnizca cekirdek tarafindan cagrilan fabrika. */
    static AethelApi of(ServiceRegistry registry,
                        net.aethel.core.event.EventBus events,
                        String version) {
        return new ApiImpl(registry, events, version);
    }
}
