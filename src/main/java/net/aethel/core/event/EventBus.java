package net.aethel.core.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cekirdek ic event sistemi. Dinleyiciler @Subscribe ile bulunur, MethodHandle ile
 * cagrilir (refleksiyon cagri maliyeti olmadan) ve async olanlar sanal thread'e duser.
 */
public final class EventBus {

    private final Logger log;
    private final ExecutorService asyncExecutor;
    private final Map<Class<?>, List<Listener>> listeners = new ConcurrentHashMap<>();

    public EventBus(Logger log, ExecutorService asyncExecutor) {
        this.log = log;
        this.asyncExecutor = asyncExecutor;
    }

    private record Listener(String owner, Object target, MethodHandle handle,
                            int priority, boolean async, boolean ignoreCancelled) {}

    /** Bir nesnedeki tum @Subscribe metotlarini kaydeder. owner genelde modul id'sidir. */
    public void register(String owner, Object target) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (Method method : target.getClass().getDeclaredMethods()) {
            Subscribe ann = method.getAnnotation(Subscribe.class);
            if (ann == null) continue;
            if (method.getParameterCount() != 1
                    || !CoreEvent.class.isAssignableFrom(method.getParameterTypes()[0])) {
                log.warning("@Subscribe metodu tek bir CoreEvent parametresi almali: " + method);
                continue;
            }
            try {
                method.setAccessible(true);
                MethodHandle handle = lookup.unreflect(method).bindTo(target);
                Class<?> eventType = method.getParameterTypes()[0];
                listeners.computeIfAbsent(eventType, k -> new ArrayList<>())
                        .add(new Listener(owner, target, handle,
                                ann.priority(), ann.async(), ann.ignoreCancelled()));
                listeners.get(eventType).sort(Comparator.comparingInt(Listener::priority));
            } catch (IllegalAccessException e) {
                log.log(Level.WARNING, "Dinleyici baglanamadi: " + method, e);
            }
        }
    }

    /** Modul kapatilirken cagrilir; o modulun tum dinleyicilerini dusurur. */
    public void unregisterOwner(String owner) {
        listeners.values().forEach(list -> list.removeIf(l -> l.owner().equals(owner)));
    }

    public void unregister(Object target) {
        listeners.values().forEach(list -> list.removeIf(l -> l.target() == target));
    }

    /**
     * Event'i yayinlar. Cagiran thread hangisi ise sync dinleyiciler orada calisir;
     * async isaretli dinleyiciler sanal thread havuzuna gonderilir.
     */
    public <T extends CoreEvent> T post(T event) {
        for (Map.Entry<Class<?>, List<Listener>> entry : listeners.entrySet()) {
            if (!entry.getKey().isInstance(event)) continue;
            for (Listener listener : List.copyOf(entry.getValue())) {
                if (isCancelled(event) && !listener.ignoreCancelled()) continue;
                if (listener.async()) {
                    asyncExecutor.execute(() -> invoke(listener, event));
                } else {
                    invoke(listener, event);
                }
            }
        }
        return event;
    }

    private void invoke(Listener listener, CoreEvent event) {
        try {
            listener.handle().invoke(event);
        } catch (Throwable t) {
            log.log(Level.SEVERE, "Dinleyici hatasi (" + listener.owner() + "): "
                    + event.getClass().getSimpleName(), t);
        }
    }

    private boolean isCancelled(CoreEvent event) {
        return event instanceof CoreEvent.Cancellable c && c.cancelled();
    }
}
