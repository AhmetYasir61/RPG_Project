package net.aethel.core.modules.profile;

import net.aethel.core.api.PlayerProfile;
import net.aethel.core.api.ProfileService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Oyuncu profillerini yukler, bellekte tutar ve periyodik olarak diske yazar.
 * Neredeyse tum moduller bu servise baglidir; hot-disable edilmemesi onerilir.
 */
@ModuleInfo(id = "profile", name = "Profil", hotDisable = false)
public final class ProfileModule implements Module, ProfileService, Listener {

    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();
    private ProfileRepository repository;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.repository = new ProfileRepository(ctx.database());
        ctx.services().register(ProfileService.class, this, "profile");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        ctx.schema().migrate("profile", ProfileSchema.MIGRATIONS);
        ctx.listener(this);

        int autosave = Math.max(30, ctx.config().get("config.yml").yaml()
                .getInt("profile.autosave-seconds", 300));
        ctx.scheduler().repeating("profile", 20L * autosave, 20L * autosave, this::flushDirty);

        // Reload sonrasi zaten cevrimici olan oyuncular icin profil yukle.
        ctx.plugin().getServer().getOnlinePlayers().forEach(this::loadFor);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        cache.values().forEach(profile -> repository.save(profile).join());
        cache.clear();
        sessionStart.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        loadFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerProfile profile = cache.remove(uuid);
        Long start = sessionStart.remove(uuid);
        if (profile == null) return;
        if (start != null) profile.addPlaytime((System.currentTimeMillis() - start) / 1000L);
        repository.save(profile);
    }

    /**
     * Profil asenkron yuklenir; oyuncu bu sirada oynayabilir. Yukleme bitene kadar
     * cached() bos doner, bu yuzden modul kodu daima Optional kontrol eder.
     */
    private void loadFor(Player player) {
        UUID uuid = player.getUniqueId();
        sessionStart.put(uuid, System.currentTimeMillis());
        repository.find(uuid).thenAccept(found -> {
            PlayerProfile profile = found.orElseGet(() ->
                    PlayerProfile.fresh(uuid, player.getName()));
            profile.name(player.getName());
            profile.lastJoin(System.currentTimeMillis());
            cache.put(uuid, profile);
            if (found.isEmpty()) repository.save(profile);
        });
    }

    /** Yalnizca kirlenmis profilleri yazar; degismemis veri icin disk isi yapilmaz. */
    private void flushDirty() {
        cache.values().stream().filter(PlayerProfile::dirty).forEach(repository::save);
    }

    @Override
    public Optional<PlayerProfile> cached(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> load(UUID uuid) {
        PlayerProfile cached = cache.get(uuid);
        return cached != null
                ? CompletableFuture.completedFuture(Optional.of(cached))
                : repository.find(uuid);
    }

    @Override
    public CompletableFuture<Optional<PlayerProfile>> loadByName(String name) {
        return cache.values().stream()
                .filter(profile -> profile.name().equalsIgnoreCase(name))
                .findFirst()
                .map(profile -> CompletableFuture.completedFuture(Optional.of(profile)))
                .orElseGet(() -> repository.findByName(name));
    }

    @Override
    public Collection<PlayerProfile> online() {
        return List.copyOf(cache.values());
    }

    @Override
    public CompletableFuture<Void> save(PlayerProfile profile) {
        return repository.save(profile);
    }
}
