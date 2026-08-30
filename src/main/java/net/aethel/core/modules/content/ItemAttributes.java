package net.aethel.core.modules.content;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;

/**
 * YAML nitelik adlarini gercek Bukkit AttributeModifier'lara cevirir.
 *
 * Bunlar bir zamanlar YALNIZCA lore'a yaziliyordu: tanimda "damage: 8.5" yazsa da
 * item vanilla kilicin hasarini veriyordu. Tooltip'te vanilla degerler goruluyor,
 * oyuncu 8.5 vuruyorum saniyordu; hicbir yere uyari dusmuyordu.
 *
 * "damage" ve "attack-speed" icin TABAN degerler cikarilir: vanilla bir kilic
 * zaten kendi hasarini tasir, uzerine eklemek 8.5 degil 14 hasar verirdi. Bu
 * yuzden istenen degere ULASACAK sekilde fark uygulanir.
 */
final class ItemAttributes {

    /** Vanilla oyuncunun elle vurus tabani. Silah modifier'i bunun uzerine biner. */
    private static final double BASE_ATTACK_DAMAGE = 1.0D;
    private static final double BASE_ATTACK_SPEED = 4.0D;

    private final Plugin plugin;

    ItemAttributes(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Tanimdaki nitelikleri meta'ya yazar. Once TUM modifier'lar temizlenir:
     * item yeniden kurulunca eski degerlerin uzerine eklenmesi, her yenilemede
     * silahin guclenmesine yol acardi.
     */
    void apply(ItemMeta meta, Map<String, Double> attributes, String suffix) {
        if (attributes.isEmpty()) return;

        attributes.forEach((name, value) -> {
            Attribute attribute = attributeOf(name);
            if (attribute == null || value == null || value == 0) return;

            NamespacedKey key = new NamespacedKey(plugin,
                    "attr_" + name.toLowerCase(Locale.ROOT).replace('-', '_')
                            + (suffix == null || suffix.isBlank() ? "" : "_" + suffix));

            meta.addAttributeModifier(attribute, new AttributeModifier(key,
                    adjust(attribute, value), AttributeModifier.Operation.ADD_NUMBER,
                    slotOf(attribute)));
        });
    }

    /** Yeniden kurmadan once eski modifier'lari temizler. */
    void clear(ItemMeta meta) {
        meta.setAttributeModifiers(null);
    }

    /**
     * Istenen SONUC degerine ulasmak icin uygulanacak fark.
     * damage: 8.5 -> oyuncu 8.5 vursun demektir, "+8.5 daha" degil.
     */
    private static double adjust(Attribute attribute, double value) {
        if (attribute == Attribute.ATTACK_DAMAGE) return value - BASE_ATTACK_DAMAGE;
        if (attribute == Attribute.ATTACK_SPEED) {
            // attack-speed YAML'de vanilla FARKI olarak yaziliyor (-2.4 gibi);
            // negatifse fark, pozitifse mutlak deger kabul edilir.
            return value < 0 ? value : value - BASE_ATTACK_SPEED;
        }
        return value;
    }

    /** Silah nitelikleri yalnizca ELDE, zirh nitelikleri her slotta gecerlidir. */
    private static EquipmentSlotGroup slotOf(Attribute attribute) {
        if (attribute == Attribute.ATTACK_DAMAGE || attribute == Attribute.ATTACK_SPEED
                || attribute == Attribute.ATTACK_KNOCKBACK) {
            return EquipmentSlotGroup.MAINHAND;
        }
        if (attribute == Attribute.ARMOR || attribute == Attribute.ARMOR_TOUGHNESS) {
            return EquipmentSlotGroup.ARMOR;
        }
        return EquipmentSlotGroup.ANY;
    }

    /** YAML adi -> Bukkit niteligi. Bilinmeyen ad sessizce atlanir. */
    private static Attribute attributeOf(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "damage", "attack-damage" -> Attribute.ATTACK_DAMAGE;
            case "attack-speed" -> Attribute.ATTACK_SPEED;
            case "knockback", "attack-knockback" -> Attribute.ATTACK_KNOCKBACK;
            case "armor" -> Attribute.ARMOR;
            case "toughness", "armor-toughness" -> Attribute.ARMOR_TOUGHNESS;
            case "health", "max-health" -> Attribute.MAX_HEALTH;
            case "speed", "movement-speed" -> Attribute.MOVEMENT_SPEED;
            case "knockback-resistance" -> Attribute.KNOCKBACK_RESISTANCE;
            case "luck" -> Attribute.LUCK;
            default -> null;
        };
    }
}
