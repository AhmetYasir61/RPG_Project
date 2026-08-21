package net.aethel.core.event;

/**
 * Tum ic event'lerin ortak koku. Bukkit event sisteminden bagimsizdir: modul
 * kapatilinca listener'lari sahip id'si uzerinden toplu kaldirilabilir.
 */
public interface CoreEvent {

    /** Iptal edilebilir event'ler bunu uygular; EventBus iptali kontrol eder. */
    interface Cancellable extends CoreEvent {
        boolean cancelled();
        void cancelled(boolean cancelled);
    }
}
