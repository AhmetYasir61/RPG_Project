package net.aethel.core.modules.menu;

import net.aethel.core.api.CustomItem;
import net.aethel.core.api.ItemService;
import net.aethel.core.api.Menu;
import net.aethel.core.bootstrap.CoreContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Custom item katalogu: tanimli her item'i KENDI gorunumuyle (resource pack'ten
 * gelen dokusuyla) bir menude gosterir. Oyuncu icin bir vitrin, yetkili icin
 * ayni zamanda "uzerine al" yoludur.
 *
 * Neden ayri bir komut degil de menu: yonetim yuzeyinin tamami menu tabanli
 * kalmalidir. Yetkili bir oyuncu item'i buradan alir, chat'e komut yazmaz.
 */
final class ItemCatalog {

    /** Item verme yetkisi. Yetkisi olmayan oyuncu katalogu yalnizca GORUR. */
    static final String GIVE_PERMISSION = "aethel.admin.items";

    /** Sayfa icerigi: ust satir baslik, alt satir gezinme icin ayrilir. */
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final CoreContext ctx;
    private final MenuModule module;
    private final MiniMessage mini = MiniMessage.miniMessage();

    ItemCatalog(CoreContext ctx, MenuModule module) {
        this.ctx = ctx;
        this.module = module;
    }

    /** Katalogu acar. Icerik modulu kapaliysa false doner ve menu hic acilmaz. */
    boolean open(Player player, String filterTag) {
        var service = ctx.services().optional(ItemService.class);
        if (service.isEmpty()) return false;

        List<CustomItem> definitions = new ArrayList<>(service.get().all());
        if (filterTag != null && !filterTag.isBlank()) {
            definitions.removeIf(item -> !item.tags().contains(filterTag));
        }
        definitions.sort(Comparator.comparing(CustomItem::fullId));

        boolean canGive = player.hasPermission(GIVE_PERMISSION);
        Menu menu = module.create(ctx.lang().render(player, canGive
                ? "menu.catalog-title-admin" : "menu.catalog-title"), 5);

        List<Menu.MenuEntry> entries = new ArrayList<>(definitions.size());
        for (CustomItem definition : definitions) {
            entries.add(new Menu.MenuEntry(icon(service.get(), definition, canGive, player),
                    click -> handle(click, definition, canGive)));
        }
        menu.paginate(entries, CONTENT_SLOTS, 39, 41);
        menu.open(player);
        return true;
    }

    /**
     * Vitrin item'i tanimin KENDISINDEN uretilir: oyuncu menude gordugu seyin
     * birebir aynisini alir, ayri bir "onizleme" gorunumu bakim yuku olmaz.
     */
    private ItemStack icon(ItemService service, CustomItem definition,
                           boolean canGive, Player viewer) {
        ItemStack stack = service.create(definition.fullId())
                .orElseGet(() -> new ItemStack(Material.PAPER));

        stack.editMeta(meta -> {
            List<Component> lore = meta.lore() == null
                    ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(mini.deserialize("<dark_gray>" + definition.fullId() + "</dark_gray>")
                    .decoration(TextDecoration.ITALIC, false));
            if (canGive) {
                lore.add(ctx.lang().render(viewer, "menu.catalog-give-hint")
                        .decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
        });
        return stack;
    }

    /**
     * Sol tik bir adet, shift+sol tik bir yigin verir. Yetkisi olmayan oyuncuda
     * tiklama hicbir sey yapmaz: kontrol sunucu tarafinda, menunun gorunumunde degil.
     */
    private void handle(Menu.MenuClick click, CustomItem definition, boolean canGive) {
        if (!canGive) return;
        Player player = click.player();
        if (!player.hasPermission(GIVE_PERMISSION)) return;

        int amount = click.shiftClick() ? 64 : 1;
        ctx.services().optional(ItemService.class)
                .flatMap(service -> service.create(definition.fullId(), amount))
                .ifPresent(stack -> {
                    var leftover = player.getInventory().addItem(stack);
                    if (!leftover.isEmpty()) {
                        leftover.values().forEach(rest ->
                                player.getWorld().dropItemNaturally(player.getLocation(), rest));
                    }
                    ctx.lang().send(player, "menu.catalog-given",
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
                                    .unparsed("item", definition.fullId()),
                            net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
                                    .unparsed("amount", String.valueOf(stack.getAmount())));
                });
    }

    /** "aethel:alev_kilici" gibi bir kimlikten menu ikonu; yoksa bos doner. */
    java.util.Optional<ItemStack> resolve(String fullId, int amount) {
        return ctx.services().optional(ItemService.class)
                .flatMap(service -> service.create(fullId.toLowerCase(Locale.ROOT), amount));
    }
}
