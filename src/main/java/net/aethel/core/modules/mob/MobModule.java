package net.aethel.core.modules.mob;

import net.aethel.core.api.MobDefinition;
import net.aethel.core.api.MobService;
import net.aethel.core.api.SkillDefinition;
import net.aethel.core.api.SkillService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.aethel.core.nms.VersionAdapters;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mob motoru. Moblar gercek entity olarak dogar (gorunum serbest), yetenekleri ise
 * particle efektleriyle calisir; skill tarafinda hicbir entity olusturulmaz.
 */
@ModuleInfo(id = "mob", name = "Mob Motoru", depends = {"content"}, softDepends = {"skill", "region"})
public final class MobModule implements Module, MobService {

    /** Zamanlayici tabanli yeteneklerin tarandigi periyot. */
    private static final long SKILL_TICK_PERIOD = 20L;

    private final Map<String, MobDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, Long> lastSkillUse = new ConcurrentHashMap<>();
    private MobLoader loader;
    private MobSpawner spawner;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.loader = new MobLoader(ctx.logger());
        ctx.services().register(MobService.class, this, "mob");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        var adapter = VersionAdapters.detect().orElseThrow(() ->
                new IllegalStateException("Bu Minecraft surumu icin NMS adapteri yok"));
        this.spawner = new MobSpawner(ctx, adapter);
        reload();

        ctx.listener(new MobListener(this, ctx));
        ctx.scheduler().repeating("mob", SKILL_TICK_PERIOD, SKILL_TICK_PERIOD, this::tickSkills);
    }

    @Override
    public void onDisable(CoreContext ctx) {
        definitions.clear();
        lastSkillUse.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        reload();
    }

    @Override
    public int reload() {
        definitions.clear();
        definitions.putAll(loader.loadAll(new File(ctx.config().dataFolder(), "contents")));
        ctx.logger().info("Yuklenen mob tanimi: " + definitions.size());
        return definitions.size();
    }

    @Override
    public Optional<MobDefinition> definition(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public Collection<MobDefinition> all() {
        return java.util.List.copyOf(definitions.values());
    }

    @Override
    public Optional<LivingEntity> spawn(String id, Location location) {
        return definition(id).flatMap(definition -> {
            var spawned = spawner.spawn(definition, location);
            spawned.ifPresent(entity -> trigger(entity, definition, SkillDefinition.Trigger.ON_SPAWN));
            return spawned;
        });
    }

    @Override
    public Optional<MobDefinition> resolve(Entity entity) {
        return spawner.idOf(entity).map(definitions::get);
    }

    @Override
    public boolean isCustom(Entity entity) {
        return spawner.idOf(entity).isPresent();
    }

    @Override
    public int tierOf(Entity entity) {
        return spawner.tierOf(entity);
    }

    /**
     * Zamanlayici ve dusuk-can tetikleyicileri saniyede bir taranir. Her tick taramak
     * yuzlerce mobda gereksiz is uretir; yetenek sogumasi zaten saniye mertebesindedir.
     */
    private void tickSkills() {
        for (var world : ctx.plugin().getServer().getWorlds()) {
            for (var entity : world.getLivingEntities()) {
                resolve(entity).ifPresent(definition -> {
                    trigger(entity, definition, SkillDefinition.Trigger.ON_TIMER);
                    if (healthRatio(entity) <= lowHealthThreshold(definition)) {
                        trigger(entity, definition, SkillDefinition.Trigger.ON_LOW_HEALTH);
                    }
                });
            }
        }
    }

    private double healthRatio(LivingEntity entity) {
        var attribute = entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = attribute == null ? 20.0 : attribute.getValue();
        return entity.getHealth() / Math.max(1.0, max);
    }

    private double lowHealthThreshold(MobDefinition definition) {
        return definition.skills().stream()
                .filter(skill -> skill.trigger() == SkillDefinition.Trigger.ON_LOW_HEALTH)
                .mapToDouble(MobDefinition.SkillTrigger::healthThreshold)
                .max().orElse(-1);
    }

    /** Bir tetikleyiciye bagli tum yetenekleri sansa gore calistirir. */
    void trigger(LivingEntity entity, MobDefinition definition, SkillDefinition.Trigger trigger) {
        var skills = ctx.services().optional(SkillService.class);
        if (skills.isEmpty()) return;

        for (MobDefinition.SkillTrigger entry : definition.skills()) {
            if (entry.trigger() != trigger) continue;
            if (Math.random() > entry.chance()) continue;
            if (!intervalPassed(entity, entry)) continue;
            skills.get().cast(entity, entry.skillId());
        }
    }

    /** Zamanlayici yetenekleri icin kendi araligi; skill sogumasindan bagimsizdir. */
    private boolean intervalPassed(LivingEntity entity, MobDefinition.SkillTrigger entry) {
        if (entry.intervalTicks() <= 0) return true;
        String key = entity.getUniqueId() + ":" + entry.skillId();
        long now = System.currentTimeMillis();
        Long last = lastSkillUse.get(key);
        if (last != null && now - last < entry.intervalTicks() * 50L) return false;
        lastSkillUse.put(key, now);
        return true;
    }

    CoreContext context() {
        return ctx;
    }
}
