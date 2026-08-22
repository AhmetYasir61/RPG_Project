package net.aethel.core.modules.menu;

import net.aethel.core.api.AuthService;
import net.aethel.core.api.Menu;
import net.aethel.core.api.MenuService;
import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.i18n.LangService;
import net.aethel.core.module.Module;
import net.aethel.core.module.ModuleInfo;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * Menu motoru modulu. MenuService'i saglar; giris/kayit ekranini ve YAML tanimli
 * menuleri acar. Metin girisi daima anvil uzerinden alinir.
 */
@ModuleInfo(id = "menu", name = "Menu Motoru")
public final class MenuModule implements Module, MenuService {

    private CoreContext ctx;
    private YamlMenuLoader loader;
    private ItemCatalog catalog;

    @Override
    public void onLoad(CoreContext ctx) {
        this.ctx = ctx;
        this.loader = new YamlMenuLoader(ctx, this);
        this.catalog = new ItemCatalog(ctx, this);
        ctx.services().register(MenuService.class, this, "menu");
    }

    @Override
    public void onEnable(CoreContext ctx) {
        ctx.listener(new MenuListener());
        loader.reload();
        ctx.commands().register("menu", new MenuCommand(ctx, this));
    }

    @Override
    public void onReload(CoreContext ctx) {
        loader.reload();
    }

    /** Item vitrinini acar; icerik modulu yoksa false doner. */
    public boolean openCatalog(Player player, String filterTag) {
        return catalog.open(player, filterTag);
    }

    /** YAML menulerinin custom item ikonu cozmesi icin. */
    ItemCatalog catalog() {
        return catalog;
    }

    @Override
    public Menu create(Component title, int rows) {
        return new SimpleMenu(title, rows);
    }

    @Override
    public void anvilInput(Player player, Component title, String initial, Consumer<String> callback) {
        new AnvilInput(title, initial == null ? "" : initial, callback).open(player);
    }

    /**
     * Sayi girisi ayni anvil akisini kullanir; gecersiz deger girilirse hata mesaji
     * verilip ekran yeniden acilir, boylece kullanici veriyi bastan yazmak zorunda kalmaz.
     */
    @Override
    public void anvilNumber(Player player, Component title, double initial, Consumer<Double> callback) {
        anvilInput(player, title, String.valueOf(initial), text -> {
            try {
                callback.accept(Double.parseDouble(text.trim().replace(',', '.')));
            } catch (NumberFormatException e) {
                ctx.lang().send(player, "menu.invalid-number", LangService.of("value", text));
                anvilNumber(player, title, initial, callback);
            }
        });
    }

    /**
     * GUI modunda giris/kayit. PIN, anvil ile alinir; kayitta ikinci kez sorulup
     * dogrulanir, cunku yanlis yazilmis bir PIN oyuncuyu hesabindan tamamen kilitler.
     */
    @Override
    public void openAuth(Player player, boolean registration) {
        ctx.services().optional(AuthService.class).ifPresent(auth -> {
            Component title = ctx.lang().render(player,
                    registration ? "auth.gui-title-register" : "auth.gui-title-login");
            anvilInput(player, title, "", first -> {
                if (!registration) {
                    auth.login(player.getUniqueId(), first).thenAccept(ok ->
                            ctx.scheduler().sync("menu", () -> afterLogin(player, ok, auth)));
                    return;
                }
                Component confirmTitle = ctx.lang().render(player, "auth.gui-title-confirm");
                anvilInput(player, confirmTitle, "", second -> {
                    if (!first.equals(second)) {
                        ctx.lang().send(player, "auth.mismatch");
                        ctx.scheduler().later("menu", 10L, () -> openAuth(player, true));
                        return;
                    }
                    auth.register(player.getUniqueId(), first).thenAccept(ok ->
                            ctx.scheduler().sync("menu", () -> afterRegister(player, ok)));
                });
            });
        });
    }

    private void afterLogin(Player player, boolean ok, AuthService auth) {
        if (ok) {
            ctx.lang().send(player, "auth.login-success");
            return;
        }
        ctx.lang().send(player, "auth.login-failed");
        ctx.scheduler().later("menu", 10L, () -> openAuth(player, false));
    }

    private void afterRegister(Player player, boolean ok) {
        ctx.lang().send(player, ok ? "auth.register-success" : "auth.register-failed");
        if (!ok) ctx.scheduler().later("menu", 10L, () -> openAuth(player, true));
    }

    @Override
    public boolean openNamed(Player player, String menuId) {
        return loader.open(player, menuId);
    }

    /** Menu ogeleri icin kisa yol; ad ve aciklama MiniMessage ile render edilir. */
    public ItemStack icon(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            if (lore.length > 0) meta.lore(java.util.List.of(lore));
        });
        return item;
    }
}
