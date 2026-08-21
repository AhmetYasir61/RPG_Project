package net.aethel.core.module;

import net.aethel.core.bootstrap.CoreContext;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modullerin kaydi, topolojik yuklenmesi ve calisma zamaninda acilip kapatilmasi.
 * ServiceRegistry ve EventBus temizligini modul adina kendisi yapar.
 */
public final class ModuleManager {

    private final CoreContext ctx;
    private final Logger log;
    private final Map<String, ModuleContainer> modules = new LinkedHashMap<>();
    private List<ModuleContainer> loadOrder = List.of();
    private boolean failSoft = true;

    public ModuleManager(CoreContext ctx) {
        this.ctx = ctx;
        this.log = ctx.logger();
    }

    /** modules.yml okunur ve verilen modul ornekleri kaydedilir. onLoad'da cagrilir. */
    public void register(ConfigurationSection modulesSection, boolean failSoft, Module... instances) {
        this.failSoft = failSoft;
        for (Module module : instances) {
            ModuleInfo info = module.getClass().getAnnotation(ModuleInfo.class);
            if (info == null) {
                log.warning("@ModuleInfo eksik, atlaniyor: " + module.getClass().getName());
                continue;
            }
            boolean enabled = modulesSection == null
                    || modulesSection.getBoolean(info.id() + ".enabled", true);
            modules.put(info.id(), new ModuleContainer(module, info, enabled));
        }
        loadOrder = DependencyGraph.sort(modules);
        log.info("Modul yukleme sirasi: " + loadOrder.stream().map(ModuleContainer::id).toList());
    }

    /** Tum acik modullerin onLoad'unu sirayla calistirir. */
    public void loadAll() {
        for (ModuleContainer c : loadOrder) {
            if (!c.enabledInConfig()) { c.state(ModuleState.DISABLED); continue; }
            if (!dependenciesSatisfied(c)) { c.state(ModuleState.SKIPPED); continue; }
            try {
                c.state(ModuleState.LOADING);
                c.instance().onLoad(ctx);
                c.state(ModuleState.LOADED);
            } catch (Throwable t) {
                handleFailure(c, "onLoad", t);
            }
        }
    }

    /** Tum yuklu modullerin onEnable'ini sirayla calistirir. */
    public void enableAll() {
        for (ModuleContainer c : loadOrder) {
            if (c.state() != ModuleState.LOADED) continue;
            if (!dependenciesSatisfied(c)) { c.state(ModuleState.SKIPPED); continue; }
            enableSingle(c);
        }
        long ok = modules.values().stream().filter(c -> c.state().isRunning()).count();
        log.info("Aktif modul: " + ok + "/" + modules.size());

        // Sifir aktif modul, "sunucu acildi ama hicbir sey calismiyor" demektir;
        // bu durumun bilgi satirinda kaybolmasi teshisi saatlerce geciktirir.
        if (ok == 0 && !modules.isEmpty()) {
            log.severe("Hicbir modul acilamadi. Yukaridaki hatalari kontrol et.");
            return;
        }
        List<String> failed = modules.values().stream()
                .filter(c -> c.state() == ModuleState.FAILED)
                .map(ModuleContainer::id).toList();
        if (!failed.isEmpty()) log.warning("Basarisiz modul: " + failed);

        List<String> skipped = modules.values().stream()
                .filter(c -> c.state() == ModuleState.SKIPPED)
                .map(ModuleContainer::id).toList();
        if (!skipped.isEmpty()) log.info("Atlanan modul: " + skipped);
    }

    /** Ters topolojik sirada kapatir; bagimlilar once gider. */
    public void disableAll() {
        List<ModuleContainer> reversed = new ArrayList<>(loadOrder);
        java.util.Collections.reverse(reversed);
        for (ModuleContainer c : reversed) {
            if (c.state().isRunning()) disableSingle(c, false);
        }
    }

    /** /core modules enable <id> — calisma zamaninda tek modul acar. */
    public boolean enable(String id) {
        ModuleContainer c = modules.get(id);
        if (c == null || c.state().isRunning()) return false;
        if (!dependenciesSatisfied(c)) {
            log.warning("Bagimliliklari karsilanmadi, acilamadi: " + id);
            return false;
        }
        try {
            c.state(ModuleState.LOADING);
            c.instance().onLoad(ctx);
            c.state(ModuleState.LOADED);
        } catch (Throwable t) {
            handleFailure(c, "onLoad", t);
            return false;
        }
        return enableSingle(c);
    }

    /** /core modules disable <id> — once bu module bagimli olanlari kapatir. */
    public boolean disable(String id) {
        ModuleContainer c = modules.get(id);
        if (c == null || !c.state().isRunning()) return false;
        if (!c.info().hotDisable()) {
            log.warning("Bu modul calisma zamaninda kapatilamaz: " + id);
            return false;
        }
        for (ModuleContainer dependent : DependencyGraph.dependentsOf(id, modules)) {
            if (dependent.state().isRunning()) disableSingle(dependent, true);
        }
        return disableSingle(c, true);
    }

    /** Tum aktif modullere onReload gonderir. */
    public void reloadAll() {
        for (ModuleContainer c : loadOrder) {
            if (!c.state().isRunning()) continue;
            try {
                c.instance().onReload(ctx);
            } catch (Throwable t) {
                log.log(Level.WARNING, "Modul reload hatasi: " + c.id(), t);
            }
        }
    }

    public Optional<ModuleContainer> module(String id) {
        return Optional.ofNullable(modules.get(id));
    }

    public List<ModuleContainer> all() {
        return List.copyOf(loadOrder);
    }

    private boolean enableSingle(ModuleContainer c) {
        try {
            c.state(ModuleState.ENABLING);
            c.instance().onEnable(ctx);
            c.state(ModuleState.ENABLED);
            log.info("Modul acildi: " + c.info().name());
            return true;
        } catch (Throwable t) {
            handleFailure(c, "onEnable", t);
            ctx.services().unregisterAll(c.id());
            return false;
        }
    }

    private boolean disableSingle(ModuleContainer c, boolean userTriggered) {
        try {
            c.state(ModuleState.DISABLING);
            c.instance().onDisable(ctx);
        } catch (Throwable t) {
            log.log(Level.WARNING, "Modul kapatilirken hata: " + c.id(), t);
        } finally {
            ctx.services().unregisterAll(c.id());
            ctx.events().unregisterOwner(c.id());
            ctx.scheduler().cancelOwner(c.id());
            c.state(ModuleState.DISABLED);
            if (userTriggered) log.info("Modul kapatildi: " + c.id());
        }
        return true;
    }

    private boolean dependenciesSatisfied(ModuleContainer c) {
        for (String dep : c.info().depends()) {
            ModuleContainer d = modules.get(dep);
            if (d == null) {
                log.warning("Bilinmeyen bagimlilik '" + dep + "' (" + c.id() + ")");
                return false;
            }
            if (d.state() != ModuleState.LOADED && !d.state().isRunning()) return false;
        }
        return true;
    }

    private void handleFailure(ModuleContainer c, String phase, Throwable t) {
        // Modul "bu sunucuda calisamam" diyorsa bu bir hata degil, bir kosuldur:
        // stack trace basmak logu kirletir ve gercek hatalari gizler.
        if (t instanceof ModuleUnavailableException) {
            c.state(ModuleState.SKIPPED);
            log.warning("Modul atlandi (" + c.id() + "): " + t.getMessage());
            return;
        }
        c.fail(t);
        log.log(Level.SEVERE, "Modul " + phase + " basarisiz: " + c.id(), t);
        if (!failSoft) {
            throw new IllegalStateException("fail-soft kapali, boot durduruldu: " + c.id(), t);
        }
    }
}
