package net.aethel.core.util;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Modul sahipligi takip eden zamanlayici. Bir modul kapatildiginda o modulun tum
 * task'lari tek cagride iptal edilir; I/O isleri sanal thread havuzuna gider.
 */
public final class CoreScheduler {

    private final Plugin plugin;
    private final ExecutorService virtualThreads =
            Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, List<BukkitTask>> owned = new ConcurrentHashMap<>();

    public CoreScheduler(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Ana thread'de tek seferlik is. */
    public void sync(String owner, Runnable task) {
        track(owner, plugin.getServer().getScheduler().runTask(plugin, task));
    }

    /** Ana thread'de periyodik is (tick cinsinden). */
    public void repeating(String owner, long delayTicks, long periodTicks, Runnable task) {
        track(owner, plugin.getServer().getScheduler()
                .runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    /** Ana thread'de gecikmeli is. */
    public void later(String owner, long delayTicks, Runnable task) {
        track(owner, plugin.getServer().getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    /**
     * Sanal thread'de calisan I/O isi. Veritabani, dosya ve HTTP cagrilari BURADAN
     * gecer; Bukkit'in async pool'u sinirli iken sanal thread'ler blokta ucuzdur.
     */
    public void io(Runnable task) {
        virtualThreads.execute(task);
    }

    public ExecutorService ioExecutor() {
        return virtualThreads;
    }

    /** Sahip bazli iptal; ModuleManager hot-disable akisinda cagirir. */
    public void cancelOwner(String owner) {
        List<BukkitTask> tasks = owned.remove(owner);
        if (tasks != null) tasks.forEach(BukkitTask::cancel);
    }

    public void shutdown() {
        owned.keySet().forEach(this::cancelOwner);
        virtualThreads.shutdown();
    }

    private void track(String owner, BukkitTask task) {
        owned.computeIfAbsent(owner, k -> new CopyOnWriteArrayList<>()).add(task);
    }
}
