package net.aethel.core.feature;

import net.aethel.core.api.FeatureService;
import net.aethel.core.config.ConfigFile;
import net.aethel.core.event.EventBus;
import net.aethel.core.event.FeatureToggleEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Ozellik anahtarlarinin merkezi kaydi. Kapali ozellikler icin hicbir listener
 * kayitli kalmaz; bu yuzden kapali bir ozellik gercekten "yok" olur, sadece susmaz.
 */
public final class FeatureRegistry implements FeatureService {

    private final Plugin plugin;
    private final ConfigFile config;
    private final EventBus events;
    private final Logger log;

    private final Map<String, Boolean> values = new ConcurrentHashMap<>();
    private final Map<String, String> descriptions = new ConcurrentHashMap<>();
    private final Map<String, List<Listener>> listeners = new ConcurrentHashMap<>();

    public FeatureRegistry(Plugin plugin, ConfigFile config, EventBus events, Logger log) {
        this.plugin = plugin;
        this.config = config;
        this.events = events;
        this.log = log;
        load();
    }

    private void load() {
        var section = config.yaml().getConfigurationSection("features");
        if (section == null) return;
        section.getKeys(true).forEach(key -> {
            if (section.isBoolean(key)) values.put(key, section.getBoolean(key));
        });
        log.info("Ozellik anahtari yuklendi: " + values.size());
    }

    /**
     * Modul kendi ozelligini bildirir. Dosyada zaten bir deger varsa ona dokunulmaz:
     * sunucu sahibinin kapattigi bir ozellik guncelleme sonrasi kendiliginden acilmamali.
     */
    @Override
    public void declare(String key, boolean defaultValue, String description) {
        descriptions.put(key, description);
        if (values.containsKey(key)) return;
        values.put(key, defaultValue);
        config.yaml().set("features." + key, defaultValue);
        config.save();
    }

    @Override
    public boolean enabled(String key) {
        return values.getOrDefault(key, true);
    }

    /**
     * Degeri degistirir ve listener'lari buna gore baglar/dusurur. Kapatmada
     * HandlerList'ten silmek sart: yalnizca bayrak kontrolu koymak, olayin yine de
     * islenmesi ve her cagride bos is yapilmasi demektir.
     */
    @Override
    public void set(String key, boolean value) {
        boolean previous = enabled(key);
        values.put(key, value);
        config.yaml().set("features." + key, value);
        config.save();
        if (previous == value) return;

        if (value) attachAll(key); else detachAll(key);
        events.post(new FeatureToggleEvent(key, value));
        log.info("Ozellik " + (value ? "acildi" : "kapatildi") + ": " + key);
    }

    @Override
    public Set<String> keys() {
        return new LinkedHashSet<>(values.keySet());
    }

    @Override
    public Map<String, Boolean> snapshot() {
        return new LinkedHashMap<>(values);
    }

    @Override
    public String description(String key) {
        return descriptions.getOrDefault(key, "");
    }

    /**
     * Ozellige bagli bir listener kaydeder. Ozellik kapaliysa listener Bukkit'e HIC
     * verilmez; acildiginda otomatik baglanir. Modul kodu bayrak kontrolu yazmaz.
     */
    public void listener(String key, Listener listener) {
        listeners.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(listener);
        if (enabled(key)) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    private void attachAll(String key) {
        listeners.getOrDefault(key, List.of()).forEach(listener ->
                plugin.getServer().getPluginManager().registerEvents(listener, plugin));
    }

    private void detachAll(String key) {
        listeners.getOrDefault(key, List.of()).forEach(HandlerList::unregisterAll);
    }

    /** Modul kapatilirken o modulun ozellik listener'larini dusurur. */
    public void releasePrefix(String prefix) {
        listeners.entrySet().removeIf(entry -> {
            if (!entry.getKey().startsWith(prefix)) return false;
            entry.getValue().forEach(HandlerList::unregisterAll);
            return true;
        });
    }

    public void reload() {
        config.reload();
        values.clear();
        load();
        // Yeni degerlere gore tum listener baglantilarini bastan kurar.
        listeners.keySet().forEach(key -> {
            detachAll(key);
            if (enabled(key)) attachAll(key);
        });
    }
}
