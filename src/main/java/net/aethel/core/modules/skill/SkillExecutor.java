package net.aethel.core.modules.skill;

import net.aethel.core.api.ParticleEffect;
import net.aethel.core.api.SkillDefinition;
import net.aethel.core.bootstrap.CoreContext;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Yetenegin etki katmani: hedef secimi, hasar ve ses. Gorsel katman SkillModule
 * tarafindan particle olarak cizilir; burada hicbir entity olusturulmaz.
 */
final class SkillExecutor {

    private final CoreContext ctx;
    private final SkillModule skills;

    SkillExecutor(CoreContext ctx, SkillModule skills) {
        this.ctx = ctx;
        this.skills = skills;
    }

    void execute(LivingEntity caster, SkillDefinition skill, LivingEntity explicitTarget) {
        Location origin = caster.getEyeLocation();
        Location aim = explicitTarget != null
                ? explicitTarget.getEyeLocation()
                : origin.clone().add(origin.getDirection().multiply(skill.range()));

        skill.effects().forEach(effect -> playEffect(effect, origin, aim));
        skill.sounds().forEach(sound -> playSound(caster, sound));

        List<LivingEntity> targets = selectTargets(caster, skill, explicitTarget);
        double damage = skill.value("damage", 0);
        if (damage > 0) targets.forEach(target -> target.damage(damage, caster));

        double heal = skill.value("heal", 0);
        if (heal > 0) targets.forEach(target -> heal(target, heal));
    }

    private void playEffect(ParticleEffect effect, Location origin, Location aim) {
        boolean needsTarget = effect.shape() == ParticleEffect.Shape.BEAM
                || effect.shape() == ParticleEffect.Shape.CONE
                || effect.motion() == ParticleEffect.Motion.TRAVEL;
        skills.play(effect, origin, needsTarget ? aim : null);
    }

    /**
     * Hedef secimi geometrik: AREA kure, CONE aci testi, LINE ise isin uzerindeki
     * mesafe testidir. Boylece particle gorseli ile gercek etki alani ayni sekli tasir.
     */
    private List<LivingEntity> selectTargets(LivingEntity caster, SkillDefinition skill,
                                             LivingEntity explicitTarget) {
        return switch (skill.targeting()) {
            case SELF -> List.of(caster);
            case SINGLE_TARGET -> explicitTarget == null ? List.of() : List.of(explicitTarget);
            case AREA -> nearby(caster, skill.range()).stream()
                    .filter(entity -> entity != caster).toList();
            case CONE -> nearby(caster, skill.range()).stream()
                    .filter(entity -> entity != caster)
                    .filter(entity -> inCone(caster, entity, skill.value("angle", 45)))
                    .toList();
            case LINE -> nearby(caster, skill.range()).stream()
                    .filter(entity -> entity != caster)
                    .filter(entity -> onLine(caster, entity, skill.value("width", 1.5)))
                    .toList();
            case ALL_ALLIES -> nearby(caster, skill.range()).stream()
                    .filter(entity -> entity instanceof Player).toList();
        };
    }

    private List<LivingEntity> nearby(LivingEntity caster, double range) {
        return List.copyOf(caster.getWorld().getNearbyLivingEntities(caster.getLocation(), range));
    }

    /** Koni testi: bakis yonu ile hedefe olan yon arasindaki aci esikten kucuk mu. */
    private boolean inCone(LivingEntity caster, LivingEntity target, double angleDegrees) {
        Vector toTarget = target.getLocation().toVector()
                .subtract(caster.getLocation().toVector()).normalize();
        double angle = Math.toDegrees(caster.getLocation().getDirection().angle(toTarget));
        return angle <= angleDegrees;
    }

    /** Isin testi: hedefin bakis dogrusuna dik uzakligi genislikten kucuk mu. */
    private boolean onLine(LivingEntity caster, LivingEntity target, double width) {
        Vector origin = caster.getEyeLocation().toVector();
        Vector direction = caster.getEyeLocation().getDirection();
        Vector toTarget = target.getLocation().toVector().subtract(origin);
        double projection = toTarget.dot(direction);
        if (projection < 0) return false;
        Vector closest = origin.clone().add(direction.clone().multiply(projection));
        return target.getLocation().toVector().distance(closest) <= width;
    }

    private void heal(LivingEntity target, double amount) {
        var attribute = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = attribute == null ? 20.0 : attribute.getValue();
        target.setHealth(Math.min(max, target.getHealth() + amount));
    }

    /** Bicim: "<ses> [ses-seviyesi] [perde]" — orn. "entity.blaze.shoot 1 0.8" */
    private void playSound(LivingEntity caster, String raw) {
        String[] parts = raw.split(" ");
        net.aethel.core.util.Sounds.parse(parts[0]).ifPresentOrElse(sound ->
                caster.getWorld().playSound(caster.getLocation(), sound,
                        parts.length > 1 ? Float.parseFloat(parts[1]) : 1f,
                        parts.length > 2 ? Float.parseFloat(parts[2]) : 1f),
                () -> ctx.logger().warning("Bilinmeyen ses: " + raw));
    }
}
