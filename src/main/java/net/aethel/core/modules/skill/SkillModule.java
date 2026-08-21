package net.aethel.core.modules.skill;

import net.aethel.core.api.ParticleEffect;
import net.aethel.core.api.SkillDefinition;
import net.aethel.core.api.SkillService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Yetenek motoru. Efektler particle olarak cizilir, hedefler geometriye gore secilir;
 * hicbir yetenek entity spawn etmez ve bu kisit tip sistemiyle guvence altindadir.
 */
@ModuleInfo(id = "skill", name = "Yetenekler", depends = {"content"})
public final class SkillModule implements Module, SkillService {

    /** Bir efektin gorunur oldugu azami mesafe; uzaktaki oyunculara paket gitmez. */
    private static final double VIEW_DISTANCE = 48.0D;

    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private SkillLoader loader;
    private SkillExecutor executor;
    private CoreContext ctx;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.loader = new SkillLoader(ctx.logger());
        ctx.services().register(SkillService.class, this, "skill");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        this.executor = new SkillExecutor(ctx, this);
        reload();
    }

    @Override
    public void onDisable(CoreContext ctx) {
        skills.clear();
        cooldowns.clear();
    }

    @Override
    public void onReload(CoreContext ctx) {
        reload();
    }

    @Override
    public int reload() {
        skills.clear();
        skills.putAll(loader.loadAll(new java.io.File(ctx.config().dataFolder(), "contents")));
        ctx.logger().info("Yuklenen yetenek: " + skills.size());
        return skills.size();
    }

    @Override
    public Optional<SkillDefinition> definition(String id) {
        return Optional.ofNullable(skills.get(id));
    }

    @Override
    public Collection<SkillDefinition> all() {
        return java.util.List.copyOf(skills.values());
    }

    @Override
    public boolean cast(LivingEntity caster, String skillId) {
        return cast(caster, skillId, null);
    }

    /**
     * Soguma kontrolu once yapilir: efekt oynatildiktan sonra reddetmek, oyuncuya
     * yetenegin calistigi izlenimini verir ve hile ihbarlarina yol acar.
     */
    @Override
    public boolean cast(LivingEntity caster, String skillId, LivingEntity target) {
        SkillDefinition skill = skills.get(skillId);
        if (skill == null) return false;
        if (isOnCooldown(caster, skillId)) return false;

        cooldowns.put(key(caster, skillId),
                System.currentTimeMillis() + (long) (skill.cooldownSeconds() * 1000));
        executor.execute(caster, skill, target);
        return true;
    }

    @Override
    public void play(ParticleEffect effect, Location origin) {
        play(effect, origin, null);
    }

    /** Efekti adim adim oynatir; her adim tick zamanlayicisina yayilir. */
    @Override
    public void play(ParticleEffect effect, Location origin, Location target) {
        int steps = effect.steps();
        for (int step = 0; step < steps; step++) {
            int current = step;
            ctx.scheduler().later("skill", (long) step * effect.periodTicks(),
                    () -> drawStep(effect, origin, target, current));
        }
    }

    /**
     * Particle'lar dogrudan yakin oyunculara gonderilir. World#spawnParticle tum
     * gorus mesafesindeki oyunculara yayin yapar; 100 kisilik bir savasta bu gereksiz
     * paket trafigi demektir, bu yuzden mesafe filtresini kendimiz uyguluyoruz.
     */
    private void drawStep(ParticleEffect effect, Location origin, Location target, int step) {
        var points = ParticleGeometry.build(effect, origin, target, step);
        var viewers = origin.getWorld().getPlayers().stream()
                .filter(player -> player.getLocation().distanceSquared(origin)
                        <= VIEW_DISTANCE * VIEW_DISTANCE)
                .toList();
        if (viewers.isEmpty()) return;

        for (Location point : points) {
            for (Player viewer : viewers) {
                spawn(viewer, effect, point);
            }
        }
    }

    private void spawn(Player viewer, ParticleEffect effect, Location point) {
        if (effect.particle() == Particle.DUST) {
            viewer.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(effect.color(), (float) effect.size()));
            return;
        }
        viewer.spawnParticle(effect.particle(), point, 1, 0, 0, 0, effect.speed());
    }

    @Override
    public boolean isOnCooldown(LivingEntity caster, String skillId) {
        return cooldownRemaining(caster, skillId) > 0;
    }

    @Override
    public long cooldownRemaining(LivingEntity caster, String skillId) {
        Long until = cooldowns.get(key(caster, skillId));
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    private String key(LivingEntity caster, String skillId) {
        return caster.getUniqueId() + ":" + skillId;
    }

    /** Cikan oyuncunun sogumalarini temizler; bellek sizintisini onler. */
    public void clearCooldowns(UUID entityId) {
        cooldowns.keySet().removeIf(key -> key.startsWith(entityId.toString()));
    }
}
