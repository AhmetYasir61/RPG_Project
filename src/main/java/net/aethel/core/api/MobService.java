package net.aethel.core.api;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.Collection;
import java.util.Optional;

/**
 * Mob motoru. Custom moblar gercek entity olarak dogar; yetenekleri particle
 * efektleriyle calisir ve zorluk carpanlari bolgeden okunur.
 */
public interface MobService {

    Optional<MobDefinition> definition(String id);

    Collection<MobDefinition> all();

    /** Tanimdan mob dogurur; bolgenin zorlugu can ve hasara uygulanir. */
    Optional<LivingEntity> spawn(String id, Location location);

    /** Bir entity'nin hangi tanimdan geldigini cozer. */
    Optional<MobDefinition> resolve(Entity entity);

    boolean isCustom(Entity entity);

    /** Bir mob'un anlik tier degeri (zorluk uygulanmis). */
    int tierOf(Entity entity);

    int reload();
}
