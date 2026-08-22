package net.aethel.core.modules.menu;

import net.aethel.core.bootstrap.CoreContext;
import net.aethel.core.command.Arg;
import net.aethel.core.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Oyuncu menu komutu. Yonetim komutu DEGILDIR: yalnizca YAML'de tanimli bir menuyu
 * ya da item katalogunu acar. Yonetim islemleri /adminmenu uzerinden yurur.
 */
@Command(value = "menu", aliases = {"m"}, playerOnly = true,
        descriptionKey = "menu.command-description")
public final class MenuCommand {

    private final CoreContext ctx;
    private final MenuModule menus;

    public MenuCommand(CoreContext ctx, MenuModule menus) {
        this.ctx = ctx;
        this.menus = menus;
    }

    /** Argumansiz kullanim ana menuyu acar. */
    @Command("")
    public void main(CommandSender sender) {
        open((Player) sender, "ana_menu");
    }

    /** Ad ile acma: /menu <ad>. Namespace verilmezse tum namespace'lerde aranir. */
    @Command("ac")
    public void named(CommandSender sender, @Arg(value = "ad", suggests = "menu") String menuId) {
        open((Player) sender, menuId);
    }

    /**
     * Item vitrini. Herkes gorebilir; yalnizca aethel.admin.items yetkisi olan
     * tiklayinca item'i alir. Yetki kontrolu katalogun kendi icindedir.
     */
    @Command("itemler")
    public void catalog(CommandSender sender) {
        Player player = (Player) sender;
        if (!menus.openCatalog(player, null)) {
            ctx.lang().send(player, "menu.catalog-unavailable");
        }
    }

    private void open(Player player, String menuId) {
        if (!menus.openNamed(player, menuId)) {
            ctx.lang().send(player, "menu.not-found",
                    net.aethel.core.i18n.LangService.of("id", menuId));
        }
    }
}
