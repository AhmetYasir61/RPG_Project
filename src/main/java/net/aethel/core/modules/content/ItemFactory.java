package net.aethel.core.modules.content;

import net.aethel.core.api.CustomItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * CustomItem tanimindan ItemStack uretir ve geri cozer. Kimlik PDC'ye yazilir:
 * oyuncu item'i yeniden adlandirsa da, tanim degisse de kimlik korunur.
 */
final class ItemFactory {

    private final NamespacedKey idKey;
    private final MiniMessage mini = MiniMessage.miniMessage();

    ItemFactory(Plugin plugin) {
        this.idKey = new NamespacedKey(plugin, "item_id");
    }

    ItemStack create(CustomItem definition, int amount) {
        Material material = Material.matchMaterial(
                definition.baseMaterial().toUpperCase(Locale.ROOT));
        if (material == null) material = Material.PAPER;

        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        stack.editMeta(meta -> {
            meta.displayName(mini.deserialize(definition.displayName())
                    .decoration(TextDecoration.ITALIC, false));
            if (!definition.lore().isEmpty()) {
                List<Component> lore = new ArrayList<>(definition.lore().size());
                definition.lore().forEach(line -> lore.add(
                        mini.deserialize(line).decoration(TextDecoration.ITALIC, false)));
                meta.lore(lore);
            }
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, definition.fullId());

            // item_model 1.21.2+ ile gelir ve CMD'den daha saglamdir: model dogrudan
            // kimlikle baglanir, sayi carpismasi diye bir sorun kalmaz. CMD'yi yine de
            // yaziyoruz ki eski istemci profilleri ve harici araclar geride kalmasin.
            meta.setItemModel(NamespacedKey.fromString(definition.modelKey()));
            if (definition.customModelData() > 0) {
                meta.setCustomModelData(definition.customModelData());
            }
            meta.setUnbreakable(definition.unbreakable());
            definition.enchantments().forEach((key, level) -> {
                Enchantment enchantment = Enchantment.getByKey(
                        NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
                if (enchantment != null) meta.addEnchant(enchantment, level, true);
            });
            if (definition.glow() && definition.enchantments().isEmpty()) {
                meta.setEnchantmentGlintOverride(true);
            }
            if (definition.maxDurability() != null && meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                damageable.setMaxDamage(definition.maxDurability());
            }
        });
        return stack;
    }

    /** Item'in PDC kimligini okur; custom degilse bos doner. */
    Optional<String> idOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        return Optional.ofNullable(stack.getItemMeta().getPersistentDataContainer()
                .get(idKey, PersistentDataType.STRING));
    }
}
