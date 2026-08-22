package net.aethel.core.modules.menu;

import net.aethel.core.api.Menu;
import net.aethel.core.bootstrap.CoreContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * contents/<ns>/menus/*.yml dosyalarindan menu tanimlarini okur ve action DSL'ini
 * calistirir. Boylece basit menuler icin Java kodu yazmak gerekmez.
 */
final class YamlMenuLoader {

    private final CoreContext ctx;
    private final MenuModule module;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final Map<String, YamlConfiguration> definitions = new ConcurrentHashMap<>();

    YamlMenuLoader(CoreContext ctx, MenuModule module) {
        this.ctx = ctx;
        this.module = module;
    }

    /** Tum namespace'lerdeki menu tanimlarini yeniden okur. */
    void reload() {
        definitions.clear();
        File contents = new File(ctx.config().dataFolder(), "contents");
        File[] namespaces = contents.listFiles(File::isDirectory);
        if (namespaces == null) return;

        for (File namespace : namespaces) {
            File menus = new File(namespace, "menus");
            File[] files = menus.listFiles(file -> file.getName().endsWith(".yml"));
            if (files == null) continue;
            for (File file : files) {
                String id = namespace.getName() + ":" + file.getName().replace(".yml", "");
                definitions.put(id, YamlConfiguration.loadConfiguration(file));
            }
        }
        ctx.logger().info("Yuklenen menu tanimi: " + definitions.size());
    }

    /** Tab-complete icin yuklu menu kimlikleri. */
    java.util.Collection<String> menuIds() {
        return definitions.keySet();
    }

    boolean open(Player player, String menuId) {
        YamlConfiguration yaml = definitions.get(menuId);
        if (yaml == null) return false;

        Component title = mini.deserialize(yaml.getString("title", menuId));
        Menu menu = module.create(title, yaml.getInt("rows", 3));

        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection entry = items.getConfigurationSection(key);
                if (entry == null) continue;
                applyItem(menu, entry, player);
            }
        }
        menu.open(player);
        return true;
    }

    private void applyItem(Menu menu, ConfigurationSection entry, Player player) {
        int amount = Math.max(1, entry.getInt("amount", 1));

        // "item: aethel:alev_kilici" verilmisse ikon custom item tanimindan uretilir;
        // menude gorunen sey oyuncunun alacagi seyle birebir ayni olur. Tanim
        // bulunamazsa material'e dusulur, menu bozulmaz.
        String customId = entry.getString("item");
        ItemStack item = customId == null ? null
                : module.catalog().resolve(customId, amount).orElse(null);
        if (item == null) {
            if (customId != null) {
                ctx.logger().warning("Menu ikonu icin bilinmeyen item: " + customId);
            }
            Material material = Material.matchMaterial(
                    entry.getString("material", "STONE").toUpperCase(Locale.ROOT));
            if (material == null) material = Material.STONE;
            item = new ItemStack(material, amount);
        }
        String name = entry.getString("name");
        List<String> lore = entry.getStringList("lore");
        ItemStack styled = item;
        styled.editMeta(meta -> {
            if (name != null) meta.displayName(mini.deserialize(name));
            if (!lore.isEmpty()) {
                List<Component> rendered = new ArrayList<>(lore.size());
                lore.forEach(line -> rendered.add(mini.deserialize(line)));
                meta.lore(rendered);
            }
        });

        List<String> actions = entry.getStringList("actions");
        ItemStack icon = item;
        menu.set(entry.getInt("slot"), icon, click -> actions.forEach(
                action -> runAction(click.player(), action)));
    }

    /**
     * Action DSL: [command], [console], [sound], [menu], [items], [close], [message].
     * Menu yazarinin Java'ya inmesine gerek kalmadan yaygin islemleri kapsar.
     */
    private void runAction(Player player, String action) {
        int end = action.indexOf(']');
        if (!action.startsWith("[") || end < 0) return;
        String type = action.substring(1, end).toLowerCase(Locale.ROOT);
        String value = action.substring(end + 1).trim();

        switch (type) {
            case "command" -> player.performCommand(value);
            case "console" -> ctx.plugin().getServer().dispatchCommand(
                    ctx.plugin().getServer().getConsoleSender(), value);
            case "message" -> player.sendMessage(mini.deserialize(value));
            case "menu" -> open(player, value);
            case "close" -> player.closeInventory();
            case "items" -> module.openCatalog(player, value.isBlank() ? null : value);
            case "sound" -> playSound(player, value);
            default -> ctx.logger().warning("Bilinmeyen menu eylemi: " + type);
        }
    }

    private void playSound(Player player, String value) {
        String[] parts = value.split(" ");
        net.aethel.core.util.Sounds.parse(parts[0]).ifPresentOrElse(sound ->
                player.playSound(player.getLocation(), sound,
                        parts.length > 1 ? Float.parseFloat(parts[1]) : 1f,
                        parts.length > 2 ? Float.parseFloat(parts[2]) : 1f),
                () -> ctx.logger().warning("Bilinmeyen ses: " + value));
    }
}
