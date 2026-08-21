package net.aethel.core.modules.panel;

import net.aethel.core.api.Menu;
import net.aethel.core.api.MenuService;
import net.aethel.core.bootstrap.CoreContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Oyun ici yonetim paneli. Bolumler menude listelenir; metin girisi gerektiginde
 * chat degil anvil kullanilir, boylece yetkili panelden hic cikmaz.
 */
final class PanelGui {

    /** Icerik slotlari; ust ve alt siralar cerceve icin ayrilir. */
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34};

    private final CoreContext ctx;
    private final AdminPanelModule panel;
    private final MiniMessage mini = MiniMessage.miniMessage();

    PanelGui(CoreContext ctx, AdminPanelModule panel) {
        this.ctx = ctx;
        this.panel = panel;
    }

    void openRoot(Player player) {
        MenuService menus = panel.menus();
        Menu menu = menus.create(ctx.lang().render(player, "panel.title"), 5);

        ItemStack frame = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        List<Menu.MenuEntry> entries = new ArrayList<>();

        for (AdminPanelModule.Section section : panel.sectionList()) {
            if (!player.hasPermission(section.permission())) continue;
            entries.add(new Menu.MenuEntry(
                    icon(material(section.icon()), "<yellow>" + section.displayName() + "</yellow>",
                            List.of("<gray>Duzenlemek icin tikla.</gray>")),
                    click -> openSection(click.player(), section.id())));
        }
        menu.paginate(entries, CONTENT_SLOTS, 39, 41);
        menu.fill(frame);
        menu.open(player);
    }

    /**
     * Bolum ekrani. Gercek duzenleme akislari ilgili modulun servisine baglanir;
     * panel yalnizca on yuzdur, is mantigi burada tutulmaz.
     */
    void openSection(Player player, String sectionId) {
        MenuService menus = panel.menus();
        Menu menu = menus.create(mini.deserialize("<dark_gray>Panel · " + sectionId), 6);

        switch (sectionId) {
            case "items" -> fillItems(menu, player);
            case "skills" -> fillSkills(menu, player);
            case "regions" -> fillRegions(menu, player);
            case "difficulties" -> fillDifficulties(menu, player);
            case "pack" -> fillPack(menu, player);
            case "features" -> fillFeatures(menu, player);
            default -> menu.set(22, icon(Material.BARRIER,
                    "<red>Bu bolum henuz bos</red>",
                    List.of("<gray>Ilgili modul kapali olabilir.</gray>")));
        }
        menu.set(49, icon(Material.ARROW, "<gray>Geri</gray>", List.of()),
                click -> openRoot(click.player()));
        menu.open(player);
    }

    private void fillItems(Menu menu, Player player) {
        ctx.services().optional(net.aethel.core.api.ItemService.class).ifPresent(items -> {
            List<Menu.MenuEntry> entries = new ArrayList<>();
            items.all().forEach(item -> entries.add(new Menu.MenuEntry(
                    items.create(item.fullId()).orElse(new ItemStack(Material.PAPER)),
                    click -> giveItem(click.player(), item.fullId()))));
            menu.paginate(entries, CONTENT_SLOTS, 45, 53);
        });
    }

    private void fillSkills(Menu menu, Player player) {
        ctx.services().optional(net.aethel.core.api.SkillService.class).ifPresent(skills -> {
            List<Menu.MenuEntry> entries = new ArrayList<>();
            skills.all().forEach(skill -> entries.add(new Menu.MenuEntry(
                    icon(Material.BLAZE_POWDER, skill.displayName(), skill.description()),
                    click -> skills.cast(click.player(), skill.id()))));
            menu.paginate(entries, CONTENT_SLOTS, 45, 53);
        });
    }

    private void fillRegions(Menu menu, Player player) {
        ctx.services().optional(net.aethel.core.api.RegionService.class).ifPresent(regions -> {
            List<Menu.MenuEntry> entries = new ArrayList<>();
            regions.regions().forEach(region -> entries.add(new Menu.MenuEntry(
                    icon(Material.MAP, "<yellow>" + region.id() + "</yellow>",
                            List.of("<gray>Dunya:</gray> <white>" + region.world() + "</white>",
                                    "<gray>Sekil:</gray> <white>"
                                            + region.shape().getClass().getSimpleName() + "</white>",
                                    "<gray>Zorluk:</gray> <white>" + region.difficultyId() + "</white>")),
                    click -> ctx.lang().send(click.player(), "panel.selected",
                            net.aethel.core.i18n.LangService.of("value", region.id())))));
            menu.paginate(entries, CONTENT_SLOTS, 45, 53);
        });
    }

    /** Zorluk duzenleme: ad ve yuzde anvil ile girilir. */
    private void fillDifficulties(Menu menu, Player player) {
        ctx.services().optional(net.aethel.core.api.RegionService.class).ifPresent(regions -> {
            List<Menu.MenuEntry> entries = new ArrayList<>();
            regions.difficulties().forEach(difficulty -> entries.add(new Menu.MenuEntry(
                    icon(Material.REDSTONE, difficulty.displayName(),
                            List.of("<gray>Yuzde:</gray> <white>" + difficulty.percent() + "%</white>",
                                    "<gray>Hardcore:</gray> <white>" + difficulty.hardcore() + "</white>",
                                    "", "<yellow>Tikla: yuzdeyi degistir</yellow>")),
                    click -> panel.menus().anvilNumber(click.player(),
                            ctx.lang().render(click.player(), "panel.enter-percent"),
                            difficulty.percent(),
                            value -> {
                                regions.saveDifficulty(new net.aethel.core.api.Difficulty(
                                        difficulty.id(), difficulty.displayName(),
                                        (int) Math.round(value), difficulty.icon(),
                                        difficulty.hardcore()));
                                ctx.lang().send(click.player(), "panel.saved");
                            }))));
            menu.paginate(entries, CONTENT_SLOTS, 45, 53);
        });
    }

    /**
     * Ozellik listesi. Tiklama aninda ozellik acilir/kapanir ve etki HEMEN gecerlidir:
     * listener'lar baglanir/dusurulur, komut agaci tazelenir.
     */
    private void fillFeatures(Menu menu, Player player) {
        ctx.services().optional(net.aethel.core.api.FeatureService.class).ifPresent(features -> {
            List<Menu.MenuEntry> entries = new ArrayList<>();
            features.snapshot().forEach((key, value) -> entries.add(new Menu.MenuEntry(
                    icon(value ? Material.LIME_DYE : Material.GRAY_DYE,
                            (value ? "<green>" : "<dark_gray>") + key
                                    + (value ? "</green>" : "</dark_gray>"),
                            List.of("<gray>" + features.description(key) + "</gray>", "",
                                    value ? "<green>◆ Acik</green>" : "<red>◇ Kapali</red>",
                                    "<yellow>Tikla: degistir</yellow>")),
                    click -> {
                        features.set(key, !features.enabled(key));
                        ctx.lang().send(click.player(), "panel.saved");
                        openSection(click.player(), "features");
                    })));
            menu.paginate(entries, CONTENT_SLOTS, 45, 53);
        });
    }

    private void fillPack(Menu menu, Player player) {
        menu.set(22, icon(Material.BOOK, "<green>Paketi Yeniden Uret</green>",
                        List.of("<gray>contents/ ve blueprints/ taranir,</gray>",
                                "<gray>generated.zip yeniden uretilir.</gray>")),
                click -> {
                    ctx.lang().send(click.player(), "panel.pack-regenerating");
                    click.player().closeInventory();
                    ctx.plugin().getServer().dispatchCommand(
                            ctx.plugin().getServer().getConsoleSender(), "core reload");
                });
    }

    private void giveItem(Player player, String itemId) {
        ctx.services().optional(net.aethel.core.api.ItemService.class)
                .flatMap(items -> items.create(itemId))
                .ifPresent(stack -> {
                    player.getInventory().addItem(stack);
                    ctx.lang().send(player, "panel.item-given",
                            net.aethel.core.i18n.LangService.of("item", itemId));
                });
    }

    private Material material(String name) {
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material == null ? Material.PAPER : material;
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(mini.deserialize(name)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            if (!lore.isEmpty()) {
                List<Component> rendered = new ArrayList<>(lore.size());
                lore.forEach(line -> rendered.add(mini.deserialize(line)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
                meta.lore(rendered);
            }
        });
        return item;
    }
}
