package net.aethel.core.api;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Collection;
import java.util.Optional;

/**
 * Yetenek motoru. Skill'ler particle efektleri ile cizilir ve hasar/etki uygular;
 * hicbir skill entity spawn etmez.
 */
public interface SkillService {

    Optional<SkillDefinition> definition(String id);

    Collection<SkillDefinition> all();

    /** Yetenegi calistirir; soguma ve mana kontrolu icerde yapilir. */
    boolean cast(LivingEntity caster, String skillId);

    /** Yetenegi belirli bir hedefe yoneltir. */
    boolean cast(LivingEntity caster, String skillId, LivingEntity target);

    /** Yalnizca gorsel: efekti verilen konumda oynatir. */
    void play(ParticleEffect effect, Location origin);

    /** Yalnizca gorsel: efekti hedefe dogru oynatir (BEAM/TRAVEL icin). */
    void play(ParticleEffect effect, Location origin, Location target);

    boolean isOnCooldown(LivingEntity caster, String skillId);

    long cooldownRemaining(LivingEntity caster, String skillId);

    int reload();
}
