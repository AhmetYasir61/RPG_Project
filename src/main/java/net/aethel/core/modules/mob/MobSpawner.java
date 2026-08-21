package net.aethel.core.modules.mob;

import net.aethel.core.api.Difficulty;
import net.aethel.core.api.ItemService;
import net.aethel.core.api.MobDefinition;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.nms.VersionAdapter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Optional;

/**
 * Mob dogurma ve ozelliklerin uygulanmasi. Bolgenin zorlugu can ve hasara burada
 * carpilir; ayni tanim farkli bolgelerde farkli guclerde dogar.
 */
final class MobSpawner {

    private final CoreContext ctx;
    private final NamespacedKey idKey;
    private final NamespacedKey tierKey;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final VersionAdapter nms;

    MobSpawner(CoreContext ctx, VersionAdapter nms) {
        this.ctx = ctx;
        this.nms = nms;
        this.idKey = new NamespacedKey(ctx.plugin(), "mob_id");
        this.tierKey = new NamespacedKey(ctx.plugin(), "mob_tier");
    }

    Optional<LivingEntity> spawn(MobDefinition definition, Location location) {
        EntityType type = parseType(definition.baseType());
        if (type == null || !type.isSpawnable()) {
            ctx.logger().warning("Gecersiz mob tipi: " + definition.baseType());
            return Optional.empty();
        }
        Difficulty difficulty = ctx.services()
                .optional(net.aethel.core.api.RegionService.class)
                .map(regions -> regions.difficultyAt(location))
                .orElseGet(Difficulty::normal);

        var entity = location.getWorld().spawnEntity(location, type);
        if (!(entity instanceof LivingEntity living)) {
            entity.remove();
            return Optional.empty();
        }
        applyStats(living, definition, difficulty);
        applyAppearance(living, definition);
        stamp(living, definition, difficulty);

        // Vanilla AI hedefleri temizlenir ki MobEngine kendi davranisini kurabilsin.
        nms.clearGoals(living);
        return Optional.of(living);
    }

    /**
     * Zorluk carpanlari: can dogrusal, hasar karekok olcekler. Hasar dogrusal artsaydi
     * yuksek zorlukta oyuncu tek vurusta olur ve zorluk "imkansiz"a donerdi.
     */
    private void applyStats(LivingEntity living, MobDefinition definition, Difficulty difficulty) {
        setAttribute(living, Attribute.MAX_HEALTH,
                definition.health() * difficulty.healthMultiplier());
        living.setHealth(Math.min(living.getHealth() + definition.health(),
                definition.health() * difficulty.healthMultiplier()));

        setAttribute(living, Attribute.ATTACK_DAMAGE,
                definition.damage() * difficulty.damageMultiplier());
        setAttribute(living, Attribute.ARMOR, definition.armor());
        setAttribute(living, Attribute.MOVEMENT_SPEED, definition.movementSpeed());
        setAttribute(living, Attribute.FOLLOW_RANGE, definition.followRange());
    }

    private void setAttribute(LivingEntity living, Attribute attribute, double value) {
        var instance = living.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    /** Gorunum: isim, can cubugu ve ekipman. Model alani harici motora birakilir. */
    private void applyAppearance(LivingEntity living, MobDefinition definition) {
        var appearance = definition.appearance();
        living.customName(mini.deserialize(definition.displayName()));
        living.setCustomNameVisible(appearance.showHealthBar());

        EntityEquipment equipment = living.getEquipment();
        if (equipment == null) return;
        var items = ctx.services().optional(ItemService.class);

        equip(equipment, items, appearance.helmet(), EquipSlot.HELMET);
        equip(equipment, items, appearance.chestplate(), EquipSlot.CHEST);
        equip(equipment, items, appearance.leggings(), EquipSlot.LEGS);
        equip(equipment, items, appearance.boots(), EquipSlot.FEET);
        equip(equipment, items, appearance.mainHand(), EquipSlot.MAIN_HAND);
        equip(equipment, items, appearance.offHand(), EquipSlot.OFF_HAND);

        // Mob ekipmani asla dusmez: aksi halde loot tablosu anlamsizlasir.
        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
        equipment.setItemInMainHandDropChance(0f);
        equipment.setItemInOffHandDropChance(0f);
    }

    /** Ekipman slotlari; Bukkit'in setter'lari ayri metotlar oldugu icin kucuk bir enum. */
    private enum EquipSlot { HELMET, CHEST, LEGS, FEET, MAIN_HAND, OFF_HAND }

    private void equip(EntityEquipment equipment, Optional<ItemService> items,
                       String itemId, EquipSlot slot) {
        if (itemId == null || itemId.isBlank()) return;
        var stack = items.flatMap(service -> service.create(itemId)).orElseGet(() -> {
            var material = org.bukkit.Material.matchMaterial(itemId.toUpperCase(Locale.ROOT));
            return material == null ? null : new org.bukkit.inventory.ItemStack(material);
        });
        if (stack == null) return;

        switch (slot) {
            case HELMET -> equipment.setHelmet(stack);
            case CHEST -> equipment.setChestplate(stack);
            case LEGS -> equipment.setLeggings(stack);
            case FEET -> equipment.setBoots(stack);
            case MAIN_HAND -> equipment.setItemInMainHand(stack);
            case OFF_HAND -> equipment.setItemInOffHand(stack);
        }
    }

    /** Kimlik ve tier PDC'ye yazilir; sunucu yeniden baslasa da mob taninir. */
    private void stamp(LivingEntity living, MobDefinition definition, Difficulty difficulty) {
        var container = living.getPersistentDataContainer();
        container.set(idKey, PersistentDataType.STRING, definition.id());
        container.set(tierKey, PersistentDataType.INTEGER,
                (int) Math.max(1, Math.round(definition.tier() * difficulty.multiplier())));
    }

    Optional<String> idOf(org.bukkit.entity.Entity entity) {
        return Optional.ofNullable(entity.getPersistentDataContainer()
                .get(idKey, PersistentDataType.STRING));
    }

    int tierOf(org.bukkit.entity.Entity entity) {
        Integer tier = entity.getPersistentDataContainer().get(tierKey, PersistentDataType.INTEGER);
        return tier == null ? 0 : tier;
    }

    private EntityType parseType(String raw) {
        try {
            return EntityType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
